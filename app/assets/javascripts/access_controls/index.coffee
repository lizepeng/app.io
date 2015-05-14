this.views ?= {}
this.views.access_controls ?= {}

views.access_controls.index = angular.module 'access_controls.list', [
  'ui.bootstrap'
  'ui.parts'
  'api.access_control'
  'api.group'
  'api.user'
  'api.helper'
]

views.access_controls.index.factory 'ACList', [
  'AccessControl'
  'Group'
  'User'
  'LinkHeader'
  'Alert'
  (AC, Group, User, LinkHeader, Alert) ->
    service           = {}
    service.links     = LinkHeader.links
    service.acs       = []
    service.resources = {}
    service.actions   = {}

    service.reload = (params) ->
      service.acs = AC.query
        page     : params.page
        per_page : params.pageSize,
        (acs, headers) ->
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers
          Group.query
            ids: AC.gids(acs).join(','),
            (grps, headers) -> service.groups = Group.toMap grps
          User.query
            ids: AC.uids(acs).join(','),
            (usrs, headers) -> service.users  = User.toMap usrs

    service.create = (data, principal) ->
      new AC(data).$save(
        (value) ->
          if data.is_group
            service.groups[principal.id] = principal
          else
            service.users[principal.id]  = principal
          if _.findIndex(service.acs,
              principal : value.principal
              resource  : value.resource
              action    : value.action) == -1
            service.acs.unshift value
        (res) ->
          Alert.push
            type : 'danger'
            msg  : res.data.message)

    service.delete = (data) ->
      data.$delete data,
        () ->
          idx = service.acs.indexOf(data)
          service.acs.splice idx, 1
        (res) ->
          Alert.push
            type : 'danger'
            msg  : res.data.message

    service.save = (data) ->
      data.$save data,
        (value) ->
        (res)   ->
          Alert.push
            type : 'danger'
            msg  : res.data.message
    service
]

.controller 'ACCtrl', [
  '$scope'
  'ACList'
  'ModalDialog'
  ($scope, ACList, ModalDialog) ->
    $scope.ACList           = ACList
    $scope.jsRoutes         = jsRoutes
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.confirm = (ac) ->
      ModalDialog.open().result.then(
        -> ACList.delete ac
        ->)
    return
]

.controller 'NewEntryCtrl', [
  '$scope'
  '$http'
  'ACList'
  ($scope, $http, ACList) ->
    $scope.ACList = ACList
    $scope.ac     =
      principal : ''
      resource  : _.keys(ACList.resources)[0]
      action    : _.keys(ACList.actions)[0]
      granted   : true

    types = [ 'groups', 'users']

    $scope.create = (ac) ->
      if _.contains(types, ac.principal._type)
        ACList.create(
          principal : ac.principal.id
          resource  : ac.resource
          action    : ac.action
          is_group  : ac.principal._type == 'groups'
          granted   : ac.granted
          ac.principal._source)

    $scope.getItems = (val) ->
      $http.get(
        '/api/search'
        params:
          types : types.join ','
          q     : "*#{val}*")
      .then (response) ->
        _.chain response.data
          .filter (item) -> _.contains types, item._type
          .each   (item) ->
            item.id = item._source.id
            if item._type == 'users'
              item.label = item._source.email
            if item._type == 'groups'
              item.label = item._source.name
        response.data
    return
]
angular.module('app').requires.push 'access_controls.list'