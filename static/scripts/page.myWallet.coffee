define ['jquery', 'underscore', 'icanhaz'], ($, _, ich) ->

  insertChallengeLine = () ->
    $('form#addTrustPool .challenge .pieces').append(ich.challengeTmpl())

  clear = () ->
    $('input#newKey').val('')
    $('input#newValue').val('')
    $('form#addTrustPool .challenge .pieces').empty()
    insertChallengeLine()

  initialize: () ->
    clear()

  onAddBagItem: (handler) ->
    $('form#addBagItem').on 'submit', (e) ->
      handler($('input#newKey').val(), $('input#newValue').val())
      e.preventDefault()

  onAddChallenge: (handler) ->
    $('#addChallenge').on 'click', (e) ->
      handler()
      e.preventDefault()

  onAddTrustPool: (handler) ->
    $('form#addTrustPool').on 'submit', (e) ->
      handler($('input#poolName').val(), _.map($('.challengeEntry'), (ce) -> $(ce).val()))
      e.preventDefault()

  onSign: (handler) ->
    $('a.sign').on 'click', (e) ->
      e.preventDefault()
      handler(e.target.dataset.pool)

  render: (viewMsg) ->
    if _.has(viewMsg, 'wallet')
      $('.wallet').html(ich.walletTmpl(viewMsg.wallet))
      $('.bag .entries').html(ich.bagTmpl(viewMsg.wallet))
    if _.has(viewMsg, 'addChallengeLine')
      insertChallengeLine()
    if _.has(viewMsg, 'addTrustPool')
      $('.pools .entries').append(ich.trustPoolsTmpl(viewMsg.addTrustPool))
    if _.has(viewMsg, 'reset')
      clear()