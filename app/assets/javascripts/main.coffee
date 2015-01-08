$().on "keyup", "input[data-autocheck-url]", () ->
  elem = $(@)
  data =
    url   : elem.data "autocheck -url"
    value : elem.val
  clearTimeout elem.data "timer"
  fg = $("#email") closest '.form-group'
  elem.data "timer", setTimeout () ->
    $.ajax
      type: "POST"
      url: data.url
      data:
        value: data.value
      statusCode:
        403: () ->
          fg removeClass 'has-success' .addClass 'has-error'
          fg find '.fa' .removeClass 'fa-check' .addClass 'fa-warning'
        200: () ->
          fg removeClass 'has-error' .addClass 'has-success'
          fg find '.fa' .removeClass 'fa-warning' .addClass 'fa-check'
    400