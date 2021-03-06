(ns asid.json-doc-exchange
  (:use midje.sweet)

  (:require [clojure.data.json :as json]
            [ring.util.response :as rr]
            [ring.util.request :as rreq]
            [ring.mock.request :as mr]
            [clojure.tools.logging :as log]))

(defn- ct [req-or-resp]
  (let [hdrs (:headers req-or-resp)]
    (cond
     (contains? hdrs "Content-Type") (get hdrs "Content-Type")
     (contains? hdrs "content-type") (get hdrs "content-type")
     :else "")))

(fact
  (ct (mr/request :get "/")) => ""
  (ct (-> (mr/request :get "/") (mr/header "Content-Type" "type"))) => "type"
  (ct (-> (rr/response "doc") (rr/content-type "type"))) => "type")

(defn- content-type-json? [req-or-resp]
  (re-find #"\+json\b" (ct req-or-resp)))

(fact
  (let [req (mr/request :get "/")]
    (content-type-json? req) => falsey
    (content-type-json? (-> req (mr/header "Content-Type" "app/vnd+json"))) => truthy
    (content-type-json? (-> req (mr/header "Content-Type" "app/vnd+json;charset=utf-8"))) => truthy)
  (let [resp (-> (rr/response "doc")
                 (rr/content-type "app+json"))]
    (content-type-json? resp) => truthy))

(defn json-documents [handler]
  (fn [req]
    (let [response (if (content-type-json? req)
                     (try
                       (let [body (rreq/body-string req)]
                         (log/info "JSON body received: " body)
                         (let [json-doc (json/read-str body
                                                       :key-fn keyword)]
                           (log/debug "Parsed json-doc: " json-doc)
                           (handler (conj req [:json-doc json-doc]))))
                       (catch Exception ex
                         (log/error ex "JSON could not be parsed.")
                         (-> (rr/response "JSON could not be parsed.")
                             (rr/status 400))))
                     (handler req))]
      (if (content-type-json? response)
        (conj response [:body (json/write-str (:body response))])
        response))))

(fact "about valid or no json in the request"
  (let [wrapper (json-documents #(:json-doc %))]
    (wrapper (mr/request :post "/" "body")) => nil?
    (wrapper (-> (mr/request :post "/" "{\"key\":10}")
                 (mr/header "Content-Type" "app/test+json"))) => {:key 10}))

(fact "about invalid json in the request"
  (let [wrapper (json-documents (fn [req] req))]
    (wrapper (-> (mr/request :post "/" "{\"malformed\": 10")
                 (mr/header "Content-Type" "app/test+json"))) => (contains {:status 400})))

(fact "about json in the response"
  (let [response-provider (fn [resp]
                            (let [app (json-documents (fn [_] resp))]
                              (app (mr/request :get "/anything"))))]
    (response-provider (-> (rr/response "hello"))) => (contains {:status 200
                                                                 :body "hello"})
    (response-provider (-> (rr/response {:key 10})
                           (rr/content-type "app+json")))) => (contains {:status 200
                                                                         :body "{\"key\":10}"}))
