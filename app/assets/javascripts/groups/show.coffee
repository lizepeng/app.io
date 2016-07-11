this.views ?= {}
this.views.groups ?= {}

views.groups.show = angular.module('group.show', [
  'api_internal.group'
  'api_internal.user'
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
  ($scope, $attrs, Group, UsersListSvc) ->
    $scope.UsersListSvc = UsersListSvc
    $scope.jsRoutes     = jsRoutes

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
  'Group'
  'User'
  'UsersListSvc'
  'ClientError'
  'Alert'
  ($scope, Group, User, UsersListSvc, ClientError, Alert) ->
    $scope.UsersListSvc = UsersListSvc

    $scope.getUsers = (val) -> User.query(q : "*#{val}*").$promise

    $scope.create = (data) ->
      Group.addUser UsersListSvc.group, data,
        (value) ->
          users = UsersListSvc.users
          users.unshift value if _.findIndex(users, id:value.id) is -1
        (resp) ->
          Alert.danger ClientError.firstMsg(resp)

    return
]

angular.module('app').requires.push 'group.show'