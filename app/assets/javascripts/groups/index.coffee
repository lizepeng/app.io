this.views ?= {}
this.views.groups ?= {}

views.groups.index = angular.module 'groups.list', [
  'xeditable'
  'api.group'
  'api.helper'
  'ui.parts'
]

views.groups.index.run (editableOptions) ->
  editableOptions.theme = 'bs3'
  return

views.groups.index.factory 'GroupList', [
  'Group'
  'LinkHeader'
  'Alert'
  (Group, LinkHeader, Alert) ->
    service        = {}
    service.links  = LinkHeader.links
    service.groups = []

    service.reload = (params) ->
      service.groups = Group.query({
        page: params.page
        per_page: params.pageSize
      }, (value, headers) ->
        LinkHeader.updateLinks params.nextPage, params.prevPage, headers
        return
      )
      return

    service.create = (data) ->
      new Group(data).$save (value) ->
        service.groups.push value
        return
      return

    service.delete = (data) ->
      data.$delete (->
        idx = service.groups.indexOf(data)
        service.groups.splice idx, 1
        return
      ), (res) ->
        Alert.push
          type: 'danger'
          msg: res.data.message
        return
      return

    service
]
views.groups.index.controller 'GroupsCtrl', [
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
      $http.post('@routes.Groups.checkName', name: data).success((res) ->
        d.resolve()
        return
      ).error (data, status) ->
        d.resolve ClientError.firstMsg(data, status)
        return
      d.promise

    $scope.confirm = (grp) ->
      instance = ModalDialog.open()
      instance.result.then (->
        GroupList.delete grp
        return
      ), ->
      return

    return
]

angular.module('app').requires.push 'groups.list'