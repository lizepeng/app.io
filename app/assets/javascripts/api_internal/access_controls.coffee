# -------------------------------------------------------- #
# Model AccessControl
# -------------------------------------------------------- #
angular.module 'api_internal.access_control', [ 'ngResource' ]

  .factory 'AccessControl', [ '$resource', ($resource) ->
    resource = $resource '/api_internal/access_controls/:principal/:resource', {
        principal : '@principal'
        resource  : '@resource'
      }, {
        create :
          method  : 'POST'
          params  :
            principal : ''
            resource  : ''
      }

    resource.gids = (aces) ->
      _.chain aces
        .filter (ace) -> ace.is_group
        .map    (ace) -> ace.principal
        .uniq()
        .value()

    resource.uids = (aces) ->
      _.chain aces
        .filter (ace) -> !ace.is_group
        .map    (ace) -> ace.principal
        .uniq()
        .value()

    return resource
  ]