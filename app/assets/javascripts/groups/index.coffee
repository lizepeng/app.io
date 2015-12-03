this.views ?= {}
this.views.groups ?= {}

views.groups.index = angular.module 'groups.index', [
  'xeditable'
  'api_internal.group'
  'api.helper'
  'ui.parts'
]

views.groups.index.factory 'GroupListSvc', [
  'Group'
  'LinkHeader'
  (Group, LinkHeader) ->
    service         = {}
    service.links   = LinkHeader.links
    service.values  = []
    service.options = {}

    loadLayouts = (ids) ->
      Group.layouts ids
        .then (resp) ->
          _.each(service.values, (grp) ->
            grp.layout = resp.data[grp.id] || ''
          )

    service.load = ->
      @values = Group.query
        page     : @options.page
        per_page : @options.pageSize
        sort     : @options.sort.join(','),
        (value, headers) =>
          LinkHeader.updateLinks @options.nextPage, @options.prevPage, headers
          loadLayouts value.map (v) -> v.id

    service
]

.controller 'GroupsCtrl', [
  '$scope'
  '$http'
  '$q'
  'ClientError'
  'Group'
  'GroupListSvc'
  'ModalDialog'
  'Alert'
  ($scope, $http, $q, ClientError, Group, GroupListSvc, ModalDialog, Alert) ->
    $scope.GroupListSvc     = GroupListSvc
    $scope.Layouts          = GroupListSvc.Layouts
    $scope.jsRoutes         = jsRoutes
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.checkName = (data) ->
      d = $q.defer()
      $http.post jsRoutes.controllers.api_internal.GroupsCtrl.checkName(data).url, name: data
        .then(
          -> d.resolve()
          (data, status) -> d.resolve ClientError.firstMsg(data, status)
        )
      d.promise

    $scope.create = (data) ->
      new Group(data).$save (value) ->
        value.layout ?= ''
        GroupListSvc.values.push value

    $scope.confirm = (grp) ->
      ModalDialog.open().result.then(
        -> $scope.delete grp
        ->
      )

    $scope.delete = (data) ->
      data.$delete(
        ->
          idx = GroupListSvc.values.indexOf(data)
          GroupListSvc.values.splice idx, 1
        (res) ->
          Alert.danger res.data.message
      )

    $scope.setLayout = (gid, layout) -> Group.setLayout gid, layout

    GroupListSvc.load()
    return
]

.run ['editableOptions', (editableOptions) -> editableOptions.theme = 'bs3']

angular.module('app').requires.push 'groups.index'