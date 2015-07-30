this.views ?= {}
this.views.groups ?= {}

views.groups.index = angular.module 'groups.list', [
  'xeditable'
  'api_internal.group'
  'api_internal.helper'
  'ui.parts'
]

views.groups.index.factory 'GroupList', [
  'Group'
  'LinkHeader'
  'Alert'
  (Group, LinkHeader, Alert) ->
    service        = {}
    service.links  = LinkHeader.links
    service.groups = []

    service.reload = (params) ->
      service.groups = Group.query
        page     : params.page
        per_page : params.pageSize,
        (value, headers) ->
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers

    service.create = (data) ->
      new Group(data).$save (value) ->
        service.groups.push value

    service.delete = (data) ->
      data.$delete(
        ->
          idx = service.groups.indexOf(data)
          service.groups.splice idx, 1
        (res) ->
          Alert.push
            type: 'danger'
            msg: res.data.message)

    service
]

.controller 'GroupsCtrl', [
  '$scope'
  '$http'
  '$q'
  'ClientError'
  'GroupList'
  'ModalDialog'
  ($scope, $http, $q, ClientError, GroupList, ModalDialog) ->
    $scope.GroupList        = GroupList
    $scope.jsRoutes         = jsRoutes
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.checkName = (data) ->
      d = $q.defer()
      $http.post jsRoutes.controllers.Groups.checkName(data).url, name: data
      .success (res) -> d.resolve()
      .error (data, status) -> d.resolve ClientError.firstMsg(data, status)
      d.promise

    $scope.confirm = (grp) ->
      ModalDialog.open().result.then(
        -> GroupList.delete grp
        ->)
    return
]

.run (editableOptions) -> editableOptions.theme = 'bs3'

angular.module('app').requires.push 'groups.list'