# -------------------------------------------------------- #
# Model AccessControl
# -------------------------------------------------------- #
angular.module 'api_internal.access_control', [ 'ngResource' ]

  .factory 'AccessControl', [ '$resource', ($resource) ->
    resource = $resource '/api_internal/access_controls/:principal/:resource/:action'

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