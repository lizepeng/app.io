this.views ?= {}
this.views.access_controls ?= {}

views.access_controls.index = angular.module 'access_controls.list', [
  'ui.bootstrap'
  'ui.parts'
  'api_internal.access_control'
  'api_internal.group'
  'api_internal.user'
  'api.helper'
]

views.access_controls.index.factory 'ACList', [
  'AccessControl'
  'Group'
  'User'
  'LinkHeader'
  'Alert'
  (AC, Group, User, LinkHeader, Alert) ->
    service            = {}
    service.links      = LinkHeader.links
    service.aces       = []
    service.resources  = {}
    service.access_def = {}

    service.reload = (params) ->
      service.aces = AC.query
        page     : params.page
        per_page : params.pageSize,
        sort     : params.sort.join(','),
        (aces, headers) =>
          buildPerm ace for ace in aces
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers
          Group.query
            ids: AC.gids(aces).join(','),
            (grps) -> service.groups = Group.toMap grps
          User.query
            ids: AC.uids(aces).join(','),
            (usrs) -> service.users  = User.toMap usrs

    service.create = (data, principal) ->
      AC.create(
        data
        (value) ->
          service.groups[principal.id] = principal if  data.is_group
          service.users[principal.id]  = principal if !data.is_group
          if _.findIndex(service.aces,
              principal_id : value.principal_id
              resource     : value.resource) is -1
            service.aces.unshift value
        (res) ->
          Alert.danger res.data.message
      )

    service.delete = (data) ->
      data.$delete(
        ->
          idx = service.aces.indexOf(data)
          service.aces.splice idx, 1
        (res) ->
          Alert.danger res.data.message
      )

    service.toggle = (data, pos) ->
      data.$save(
        pos : pos
        (value) -> buildPerm value
        (res) ->
          Alert.danger res.data.message
      )

    buildPerm = (ace) ->
      ace.permissions = (ace.permission.charAt(i) is '1' for i in [63..0])

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

    $scope.confirm = (ace) ->
      ModalDialog.open().result.then(
        -> ACList.delete ace
        ->
      )

    $scope.toggle = (ace, pos) ->
      ACList.toggle ace, pos
      return

    return
]

.controller 'NewEntryCtrl', [
  '$scope'
  '$http'
  'ACList'
  ($scope, $http, ACList) ->
    $scope.ACList     = ACList
    $scope.checkModel = {}
    $scope.newEntry   =
      principal  : ''
      resource   : _.keys(ACList.resources)[0]

    types = [ 'groups', 'users']

    $scope.create = (newEntry) ->
      if _.contains(types, newEntry.principal._type)
        ACList.create(
          principal_id  : newEntry.principal.id
          resource      : newEntry.resource
          permission    : 0
          is_group      : newEntry.principal._type is 'groups'
          newEntry.principal._source
        )

    $scope.getItems = (val) ->
      $http.get(
        '/api_internal/search'
        params:
          types : types.join ','
          q     : "*#{val}*"
          sort  : " name"
      ).then (response) ->
        _.chain response.data
          .filter (item) -> _.contains types, item._type
          .each   (item) ->
            item.id    = item._source.id
            item.label = item._source.email if item._type is 'users'
            item.label = item._source.name  if item._type is 'groups'
        response.data
    return
]
angular.module('app').requires.push 'access_controls.list'