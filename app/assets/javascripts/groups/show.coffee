list = angular.module('group.users.list', [
  'api.group'
  'api.helper'
  'ui.parts'
])

list.factory 'GroupUsersList', [
  'Group'
  'Alert'
  (Group, Alert) ->
    service       = {}
    service.users = []

    service.init = (gid) ->
      if service.users.length == 0
        service.group = id : gid
        service.users = Group.users(service.group)
      service

    service.create = (data) ->
      Group.addUser service.group, data,
        (value) ->
          if _.findIndex(service.users, id:value.id) == -1
            service.users.unshift value
        (resp) ->
          Alert.push
            type : 'danger'
            msg  : resp.data.message

    service.remove = (data) ->
      Group.delUser service.group, uid: data.id,
        ->
          idx = service.users.indexOf(data)
          service.users.splice idx, 1

    service
]

.controller 'UserGroupsCtrl', [
  '$scope'
  '$attrs'
  'GroupUsersList'
  'ModalDialog'
  ($scope, $attrs, GroupUsersList, ModalDialog) ->
    $scope.GroupUsersList   = GroupUsersList.init $attrs.id
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.confirm = (usr) ->
      ModalDialog.open().result.then(
        -> GroupUsersList.remove usr
        ->)
]

.controller 'NewEntryCtrl', [
  '$scope'
  '$http'
  'GroupUsersList'
  ($scope, $http, GroupUsersList) ->
    $scope.GroupUsersList = GroupUsersList

    $scope.getUserEmails = (val) ->
      $http.get '/api/users', params : q : """*#{val}*"""
      .then(
        (resp) ->
          resp.data.map (u) -> u.email)
]

angular.module('app').requires.push 'group.users.list'