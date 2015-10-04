this.views ?= {}
this.views.groups ?= {}

views.groups.index = angular.module 'groups.list', [
  'xeditable'
  'api_internal.group'
  'api.helper'
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

    loadLayouts = (ids) ->
      Group
        .layouts ids
        .then (resp) ->
          _.each(service.groups, (grp) ->
            grp.layout = resp.data[grp.id] || ''
          )

    service.reload = (params) ->
      service.groups = Group.query
        page     : params.page
        per_page : params.pageSize,
        (value, headers) ->
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers
          loadLayouts value.map (v) -> v.id

    service.create = (data) ->
      new Group(data).$save (value) ->
        value.layout ?= ''
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

    service.setLayout = (gid, layout) -> Group.setLayout(gid, layout)

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
    $scope.Layouts          = GroupList.Layouts
    $scope.jsRoutes         = jsRoutes
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.checkName = (data) ->
      d = $q.defer()
      $http.post jsRoutes.controllers.api_internal.GroupsCtrl.checkName(data).url, name: data
        .success -> d.resolve()
        .error (data, status) -> d.resolve ClientError.firstMsg(data, status)
      d.promise

    $scope.confirm = (grp) ->
      ModalDialog.open().result.then(
        -> GroupList.delete grp
        ->)

    $scope.setLayout = (gid, layout) -> GroupList.setLayout(gid, layout)

    return
]

.run ['editableOptions', (editableOptions) -> editableOptions.theme = 'bs3']

angular.module('app').requires.push 'groups.list'