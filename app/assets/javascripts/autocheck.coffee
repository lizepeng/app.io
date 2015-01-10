$('input[data-autocheck-url]').on 'input', ->
  formGroup    = $(@).parents '.form-group'

  formGroup.removeHelp = ->
    $(@).find('.help-block').remove()

  formGroup.addHelp = (msg) ->
    @.removeHelp()
    $(@).append """<p class="help-block"> #{msg} </p>"""

  icon = $(@).siblings('.form-control-feedback').find 'i'

  icon.refresh = ->
    if not $(@).hasClass 'fa-refresh'
      $(@).removeClass 'fa-warning fa-check'
        .addClass 'fa-refresh fa-spin'
        .css color : "#777"

  icon.warning = ->
    $(@).removeClass 'fa-refresh fa-spin'
      .removeAttr 'style'
      .addClass 'fa-warning'

  icon.check = ->
    $(@).removeClass 'fa-refresh fa-spin'
      .removeAttr 'style'
      .addClass 'fa-check'

  icon.refresh()

  clearTimeout $(@).data "timer"

  $(@).data "timer" , setTimeout =>
    $.ajax
      type : "POST"
      url  : $(@).data "autocheck-url"
      data : value : $(@).val()

    .fail (r) =>
      formGroup
        .removeClass 'has-success'
        .addClass 'has-error'
        .addHelp(r.responseJSON.value)
      icon.warning()

    .done (r) =>
      formGroup
        .removeClass 'has-error'
        .addClass 'has-success'
        .removeHelp()
      icon.check()

  , 400