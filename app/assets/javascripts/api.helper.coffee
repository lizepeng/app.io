# -------------------------------------------------------- #
# Helpers
# -------------------------------------------------------- #
angular.module 'api.helper', []

  #
  # Helper to display json error message
  #
  .factory 'ClientError', ->
    service = {}

    service.firstMsg = (data, status) ->
      unknown = "Unknown Error Occurred."
      switch status
        when 422 then data.errors?[0].errors?[0].message || unknown
        else unknown

    return service

  #
  # Helper to parse pagination link header
  #
  .factory 'LinkHeader', ->
    service = {}
    service.links = {}

    service.updateLinks = (next, prev, headers) ->
      apiLinks = service.parse headers
      @links.next = next if apiLinks.next?
      @links.prev = prev if apiLinks.prev?

    service.parse = (headers) ->
      header   = headers 'Link'
      next     = /<([^<>]+)>; rel="(next)"/g.exec header
      prev     = /<([^<>]+)>; rel="(prev)"/g.exec header
      apiLinks = {}

      apiLinks[next[2]] = next[1] if next?
      apiLinks[prev[2]] = prev[1] if prev?
      apiLinks

    return service