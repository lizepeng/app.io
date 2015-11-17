this.views ?= {}
this.views.groups ?= {}

views.groups.show = angular.module('group.show', [
  'api_internal.group'
  'api.helper'
  'ui.parts'
])

views.groups.show.factory 'UsersListSvc', [
  'Group'
  (Group) ->
    service       = {}
    service.group = {}
    service.users = []

    service.load = (gid) ->
      @group = id : gid
      @users = Group.users @group

    service
]

.controller 'UserGroupsCtrl', [
  '$scope'
  '$attrs'
  'Group'
  'UsersListSvc'
  'ModalDialog'
  ($scope, $attrs, Group, UsersListSvc, ModalDialog) ->
    $scope.UsersListSvc = UsersListSvc
    $scope.jsRoutes     = jsRoutes
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.confirm = (user) ->
      ModalDialog.open().result.then(
        -> $scope.remove user
        ->
      )

    $scope.remove = (user) ->
      Group.delUser UsersListSvc.group, uid: user.id,
        ->
          users = UsersListSvc.users
          users.splice users.indexOf(user), 1


    UsersListSvc.load $attrs.id
    return
]

.controller 'NewEntryCtrl', [
  '$scope'
  '$http'
  'Group'
  'UsersListSvc'
  ($scope, $http, Group, UsersListSvc) ->
    $scope.UsersListSvc = UsersListSvc

    $scope.getUserEmails = (val) ->
      $http.get '/api_internal/users', params : q : "*#{val}*"
        .then(
          (resp) -> resp.data.map (u) -> u.email
        )

    $scope.create = (data) ->
      Group.addUser UsersListSvc.group, data,
        (value) ->
          users = UsersListSvc.users
          users.unshift value if _.findIndex(users, id:value.id) is -1
        (resp) ->
          Alert.danger resp.data.message

    return
]

angular.module('app').requires.push 'group.show'