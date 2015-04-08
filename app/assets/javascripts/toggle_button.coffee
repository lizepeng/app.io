$('div[data-toggle-url]').on 'click', ->
  $.ajax
    type : 'POST'
    url  : $(@).data 'toggle-url'
    data : value : !($(@).data 'toggle-value')

  .done (r) =>
    $(@).data('toggle-value', r.value)
    $(@).find('i')
      .toggleClass('fa-toggle-on btn-toggle', r.value)
      .toggleClass('fa-toggle-off', !r.value)