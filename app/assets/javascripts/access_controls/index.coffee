this.views ?= {}
this.views.access_controls ?= {}

views.access_controls.index = angular.module 'access_controls.index', [
  'ui.bootstrap'
  'ui.parts'
  'api_internal.access_control'
  'api_internal.group'
  'api_internal.user'
  'api.helper'
]

views.access_controls.index.factory 'ACListSvc', [
  'AccessControl'
  'Group'
  'User'
  'LinkHeader'
  (AccessControl, Group, User, LinkHeader) ->
    service            = {}
    service.links      = LinkHeader.links
    service.aces       = []
    service.resources  = {}
    service.access_def = {}
    service.options    = {}

    service.load = ->
      @aces = AccessControl.query
        page     : @options.page
        per_page : @options.pageSize
        sort     : @options.sort.join(','),
        (aces, headers) =>
          @buildPerm ace for ace in aces
          LinkHeader.updateLinks @options.nextPage, @options.prevPage, headers
          Group.query
            ids: AccessControl.gids(aces).join(','),
            (grps) => @groups = Group.toMap grps
          User.query
            ids: AccessControl.uids(aces).join(','),
            (usrs) => @users  = User.toMap usrs

    service.buildPerm = (ace) ->
      ace.permissions = (ace.permission.charAt(i) is '1' for i in [63..0])

    service
]

.controller 'ACListCtrl', [
  '$scope'
  'ACListSvc'
  'ModalDialog'
  'Alert'
  ($scope, ACListSvc, ModalDialog, Alert) ->
    $scope.ACListSvc           = ACListSvc
    $scope.jsRoutes         = jsRoutes
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.confirm = (ace) ->
      ModalDialog.open().result.then(
        -> $scope.delete ace
        ->
      )

    $scope.toggle = (ace, pos) ->
      ace.$save(
        pos : pos
        (value) -> ACListSvc.buildPerm value
        (res) ->
          Alert.danger res.data.message
      )
      return

    $scope.delete = (ace) ->
      ace.$delete(
        ->
          ACListSvc.aces.splice ACListSvc.aces.indexOf(ace), 1
        (res) ->
          Alert.danger res.data.message
      )

    return
]

.controller 'NewEntryCtrl', [
  '$scope'
  '$http'
  'ACListSvc'
  'AccessControl'
  ($scope, $http, ACListSvc, AC) ->
    $scope.ACListSvc     = ACListSvc
    $scope.checkModel = {}
    $scope.newEntry   =
      principal  : ''
      resource   : _.keys(ACListSvc.resources)[0]

    types = ['groups', 'users']

    $scope.create = (newEntry) ->
      is_group     = newEntry.principal._type is 'groups'
      principal_id = newEntry.principal.id
      principal    = newEntry.principal._source
      if _.contains(types, newEntry.principal._type)
        AC.create(
          principal_id : principal_id
          resource     : newEntry.resource
          is_group     : is_group
          (value) ->
            ACListSvc.groups[principal_id] = principal if  is_group
            ACListSvc.users[principal_id]  = principal if !is_group
            if _.findIndex(ACListSvc.aces,
                principal_id : value.principal_id
                resource     : value.resource) is -1
              ACListSvc.aces.unshift value
          (res) ->
            Alert.danger res.data.message
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

    ACListSvc.load()
    return
]
angular.module('app').requires.push 'access_controls.index'