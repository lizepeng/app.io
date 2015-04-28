# RESTful api client
angular.module 'api', [ 'api.group', 'api.user', 'api.access_control' ]

# Model Group
angular.module 'api.group', [ 'ngResource' ]

  .factory 'Group', [ '$resource', ($resource) ->
    resource = $resource '/api/groups/:id/:relations/:uid', {
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

    resource.toMap = (grps) ->
      _.chain grps
        .map (grp) -> [ grp.id, grp ]
        .object()
        .value()

    return resource
  ]

# Model User
angular.module 'api.user', [ 'ngResource' ]

  .factory 'User', [ '$resource', ($resource) ->
    resource = $resource '/api/users/:id/:relations', {
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

    resource.toMap = (usrs) ->
      _.chain usrs
        .map (usr) -> [ usr.id, usr ]
        .object()
        .value()

    return resource
  ]

# Model AccessControl
angular.module 'api.access_control', [ 'ngResource' ]

  .factory 'AccessControl', [ '$resource', ($resource) ->
    resource = $resource '/api/access_controls/:principal/:resource/:action'

    resource.gids = (acs) ->
      _.chain acs
        .filter (ac) -> ac.is_group
        .map    (ac) -> ac.principal
        .uniq()
        .value()

    resource.uids = (acs) ->
      _.chain acs
        .filter (ac) -> !ac.is_group
        .map    (ac) -> ac.principal
        .uniq()
        .value()

    return resource
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
    service

  # Helper to make alert easier to use
  # msg format: {type:'danger', msg:'msg'}
  .factory 'Alert', ->
    alerts  : []
    dismiss : (idx) ->
      this.alerts.splice(idx, 1)
    push    : (msg) ->
      this.alerts.push(msg)