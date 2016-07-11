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
  'Group'
  'GroupListSvc'
  'ClientError'
  'Alert'
  ($scope, $http, $q, Group, GroupListSvc, ClientError, Alert) ->
    $scope.GroupListSvc     = GroupListSvc
    $scope.Layouts          = GroupListSvc.Layouts
    $scope.jsRoutes         = jsRoutes

    $scope.checkName = (name) ->
      d = $q.defer()
      $http.post jsRoutes.controllers.api_internal.GroupsCtrl.checkName().url, group_name: name
        .then(
          -> d.resolve()
          (resp) -> d.resolve ClientError.firstMsg(resp)
        )
      d.promise

    $scope.create = (data) ->
      new Group(data).$save (value) ->
        value.layout ?= ''
        GroupListSvc.values.push value

    $scope.confirm = (grp) ->
      swal(GroupListSvc.confirmDelete).then(
        -> $scope.delete grp
        (dismiss) ->
      )

    $scope.delete = (data) ->
      data.$delete(
        ->
          idx = GroupListSvc.values.indexOf(data)
          GroupListSvc.values.splice idx, 1
        (resp) ->
          Alert.danger ClientError.firstMsg(resp)
      )

    $scope.setLayout = (gid, layout) -> Group.setLayout gid, layout

    $scope.loadNextPage = ->
      if GroupListSvc.links.next
        GroupListSvc.options.page += 1
        GroupListSvc.load()

    $scope.loadPrevPage = ->
      if GroupListSvc.links.prev
        GroupListSvc.options.page -= 1
        GroupListSvc.load()

    GroupListSvc.load()
    return
]

.run ['editableOptions', (editableOptions) -> editableOptions.theme = 'bs3']

angular.module('app').requires.push 'groups.index'