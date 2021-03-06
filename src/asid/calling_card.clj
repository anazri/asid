(ns asid.calling-card
  (:use midje.sweet
        [asid.error.thread :only [fail->]])

  (:require [clojure.data.json :as json]
            [asid.http :as http]
            [asid.identity :as aid]
            [asid.wallet :as w]
            [asid.graph :as ag]
            [asid.render :as render]
            [asid.strings :as as]
            [asid.trust-pool :as tp]
            [asid.error.definition :as ed]
            [asid.current-request :as req]))

(defrecord CallingCard [identity target-uri other-party])

(defn uri [card wallet]
  (str (w/uri wallet) "/card/" (:identity card)))

(defn new-calling-card [target-uri other-party-identity]
  (CallingCard. (aid/new-identity other-party-identity)
                target-uri
                other-party-identity))

(defn- find-letterplate [card]
  (let [identity-url (:target-uri card)]
    (fail-> (http/get identity-url
                      {:accept "application/vnd.org.asidentity.introduction+json"})
            (:body)
            (-> :links :letterplate)
            (as/resolve-url identity-url))))

(defn- connection-request [card wallet pool]
  {:from (:identity wallet)
   :trust {:name (:name pool)
           :identity (:identity pool)
           :challenge (:challenge pool)}
   :links {:self (req/url-relative-to-request (uri card wallet))
           :initiator (req/url-relative-to-request (w/uri wallet))}})

(defn- seek-introduction [letterplate-url card wallet pool]
  (fail-> (http/post letterplate-url
                     {:body (json/write-str (connection-request card wallet pool))
                      :content-type "vnd/application.org.asidentity.connection-request+json"})
          :headers
          (get "location")))

(defn- remember-counterpart [location card]
  (conj card [:counterpart location]))

(defn submit [card wallet pool]
  (fail-> (find-letterplate card)
          (seek-introduction card wallet pool)
          (remember-counterpart card)))

(defn attach [card pool]
  (ag/adds-identity card pool))

(defn self-link [so-far card]
   (let [wallet (ag/c->w card)]
    (conj so-far [:self (uri card wallet)])))

(defn trustpool-link [so-far card]
  (conj so-far [:trustpool (tp/uri (ag/c->w card)
                                   (ag/c->tp card))]))

(defn otherparty-link [so-far card]
  (conj so-far [:otherParty (:target-uri card)]))

(extend-type CallingCard
  render/Linked

  (links [card]
    (-> {}
        (self-link card)
        (trustpool-link card)
        (otherparty-link card))))

(extend-type CallingCard
  render/Resource

  (to-json [card]
    {:identity (:identity card)
     :otherParty (:other-party card)})

  (content-type [_]
    "application/vnd.org.asidentity.calling-card+json"))
