$('input[data-autocheck-url]').on 'input', ->
  formGroup = $(@).parents '.form-group'

  formGroup
    .addClass "has-feedback"
    .append(
      """
        <span class="form-control-feedback">
          <i class="fa fa-lg"></i>
        </span>
      """) if formGroup.find('.form-control-feedback').length is 0

  formGroup.removeHelp = ->
    $(@).find('.help-block').remove()

  formGroup.addHelp = (msg) ->
    @.removeHelp()
    $(@).append """<span class="help-block"> #{msg} </span>"""

  icon = $(@).siblings('.form-control-feedback').find('i')

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