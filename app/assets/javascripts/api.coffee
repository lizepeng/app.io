# RESTful api client
angular.module 'api', [ 'api.group', 'api.user' ]

# Model Group
angular.module 'api.group', [ 'ngResource' ]

  .factory 'Group', [ '$resource', ($resource) ->
    $resource '/api/groups/:id/:relations/:uid', {
      id        : '@id'
      relations : ''
      uid       : ''
    }, {
      users :
        method  : 'GET'
        params  :
          relations : 'users'
        isArray : true

      addUser :
        method  : 'POST'
        params  :
          relations : 'users'
          uid       : '@uid'

      delUser :
        method  : 'DELETE'
        params  :
          relations : 'users'
          uid       : '@uid'
    }
  ]

# Model User
angular.module 'api.user', [ 'ngResource' ]

  .factory 'User', [ '$resource', ($resource) ->
    $resource '/api/users/:id/:relations', {
      id        : '@id'
      relations : ''
    }, {
      groups :
        method  : 'GET'
        params  :
          relations : 'groups'
        isArray : true

      externalGroups :
        method  : 'GET'
        params  :
          relations : 'groups'
          options   : 'external'
        isArray : true
    }
  ]

# Helpers
angular.module 'api.helper', []

  # Helper to display json error message
  .factory 'ClientError', ->
    service = {}
    service.firstMsg = (data, status) ->
      if (status == 422)
        #TODO boundary check
        data.errors[0].errors[0].message
      else "Unknown Error"
    service

  # Helper to parse pagination link header
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