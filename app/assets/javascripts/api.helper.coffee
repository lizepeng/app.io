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
      if (status == 422)
        #TODO boundary check
        data.errors[0].errors[0].message
      else "Unknown Error"

    return service

  #
  # Helper to parse pagination link header
  #
  .factory 'LinkHeader', ->
    service = {}

    service.links =
      prev: ''
      next: ''
      has : (rel) ->
        return rel of this && this[rel] != ''

    service.updateLinks = (next, prev, headers) ->
      links = service.parse headers
      if links.has 'next'
        service.links.next = next
      if links.has 'prev'
        service.links.prev = prev

    service.parse = (headers) ->
      links = {}
      links.has = (rel) ->
        rel of this

      header = headers('Link')
      if header.length == 0
        return links
      # Split parts by comma
      parts = header.split(',')
      # Parse each part into a named link
      angular.forEach parts, (p) ->
        section = p.split(';')
        if section.length != 2
          throw new Error('section could not be split on \';\'')
        url = section[0].replace(/<(.*)>/, '$1').trim()
        name = section[1].replace(/rel="(.*)"/, '$1').trim()
        links[name] = url
      links

    return service