# RESTful api client
angular.module 'api', [ 'api.group' ]

# Model Group
angular.module 'api.group', [ 'ngResource' ]

  .factory 'Group', [ '$resource', ($resource) ->
    $resource 'api/groups/:id', id: '@id'
  ]

# help to display json error message
angular.module 'api.helper', []

  .factory 'ClientError', ->
    service = {}
    service.firstMsg = (data, status) ->
      if (status == 422)
        #TODO boundary check
        data.errors[0].errors[0].message
      else "Unknown Error"
    service

  .factory 'LinkHeader', ->
    service = {}
    service.parse = (headers) ->
      header = headers('Link')
      if header.length == 0
        throw new Error('input must not be of zero length')
      # Split parts by comma
      parts = header.split(',')
      links = {}
      links.has = (rel) ->
        rel of this
      # Parse each part into a named link
      angular.forEach parts, (p) ->
        section = p.split(';')
        if section.length != 2
          throw new Error('section could not be split on \';\'')
        url = section[0].replace(/<(.*)>/, '$1').trim()
        name = section[1].replace(/rel="(.*)"/, '$1').trim()
        links[name] = url
      links
    service