# -------------------------------------------------------- #
# Model User
# -------------------------------------------------------- #
angular.module 'api_internal.user', [ 'ngResource' ]

  .factory 'User', [ '$resource', ($resource) ->
    resource = $resource '/api_internal/users/:id/:relations', {
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