# -------------------------------------------------------- #
# Model AccessControl
# -------------------------------------------------------- #
angular.module 'api_internal.access_control', [ 'ngResource' ]

  .factory 'AccessControl', [ '$resource', ($resource) ->
    resource = $resource '/api_internal/access_controls/:principal_id/:resource', {
        principal_id : '@principal_id'
        resource     : '@resource'
      }, {
        create :
          method  : 'POST'
          params  :
            principal_id : ''
            resource     : ''
      }

    resource.gids = (aces) ->
      _.chain aces
        .filter (ace) -> ace.is_group
        .map    (ace) -> ace.principal_id
        .uniq()
        .value()

    resource.uids = (aces) ->
      _.chain aces
        .filter (ace) -> !ace.is_group
        .map    (ace) -> ace.principal_id
        .uniq()
        .value()

    return resource
  ]