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
        q        : @options.q
        sort     : @options.sort.join(','),
        (aces, headers) =>
          @convertPerm ace for ace in aces
          LinkHeader.updateLinks @options.nextPage, @options.prevPage, headers
          Group.query
            ids: AccessControl.gids(aces).join(','),
            (grps) => @groups = Group.toMap grps
          User.query
            ids: AccessControl.uids(aces).join(','),
            (usrs) => @users  = User.toMap usrs

    service.delete = (ace) ->
      @aces.splice @aces.indexOf(ace), 1

    service.convertPerm = (ace) ->
      ace.permissions = []
      ace.permissions[i] = true for i in ace.permission

    service
]

.controller 'ACListCtrl', [
  '$scope'
  'ACListSvc'
  'ModalDialog'
  'ClientError'
  'Alert'
  ($scope, ACListSvc, ModalDialog, ClientError, Alert) ->
    $scope.ACListSvc        = ACListSvc
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
        (value) -> ACListSvc.convertPerm value
        (res) -> Alert.danger res.data.message
      )

    $scope.delete = (ace) ->
      ace.$delete(
        -> ACListSvc.delete ace
        (resp) -> Alert.danger ClientError.firstMsg(resp)
      )

    return
]

.controller 'NewEntryCtrl', [
  '$scope'
  '$http'
  'ACListSvc'
  'AccessControl'
  'ClientError'
  'Alert'
  ($scope, $http, ACListSvc, AC, ClientError, Alert) ->
    $scope.ACListSvc  = ACListSvc
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
          (resp) ->
            Alert.danger ClientError.firstMsg(resp)
        )

    $scope.getItems = (val) ->
      $http.get(
        '/api_internal/search'
        params:
          types : types.join ','
          q     : "email:*#{val}* OR group_name:*#{val}* "
          sort  : " group_name, email"
      ).then (response) ->
        _.chain response.data
          .filter (item) -> _.contains types, item._type
          .each   (item) ->
            item.id    = item._source.id
            item.label = item._source.email if item._type is 'users'
            item.label = item._source.group_name  if item._type is 'groups'
        response.data

    ACListSvc.load()
    return
]
angular.module('app').requires.push 'access_controls.index'