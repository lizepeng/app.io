this.views ?= {}
this.views.files ?= {}

views.files.index = angular.module 'files.list', [
  'xeditable'
  'api.cfs'
  'api.helper'
  'ui.parts'
]

views.files.index.factory 'FileList', [
  'CFS'
  'Path'
  'LinkHeader'
  'Alert'
  (CFS, Path, LinkHeader, Alert) ->
    service        = {}
    service.links  = LinkHeader.links
    service.files  = []
    service.params = {}

    service.reload = (params) ->
      service.params = params

      # since we're going to show files under user'home folder,
      # and the name of user's home folder is the same as user's id
      service.path = Path.create(params.path)
      service.realPath = Path.create(service.path.prepend(params.userId))

      CFS.find(service.realPath)
        .success (data, status, headers, config) ->
          service.files = data
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers
      # service.files = CFS.query
      #   page     : params.page
      #   per_page : params.pageSize
      #   path     : "#{params.userId}/#{params.path}/"
      #   (value, headers) ->
      #     LinkHeader.updateLinks params.nextPage, params.prevPage, headers

    service.delete = (data) ->
      # data.$delete(
      #   ->
      #     idx = service.files.indexOf(data)
      #     service.files.splice idx, 1
      #   (res) ->
      #     Alert.push
      #       type : 'danger'
      #       msg  : res.data.message)

    service
]

.controller 'FilesCtrl', [
  '$scope'
  '$http'
  '$q'
  'ClientError'
  'FileList'
  'ModalDialog'
  ($scope, $http, $q, ClientError, FileList, ModalDialog) ->
    $scope.FileList         = FileList
    $scope.jsRoutes         = jsRoutes
    $scope.path             = FileList.path
    $scope.realPath         = FileList.realPath
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.checkName = (data) ->
      d = $q.defer()
      $http.post jsRoutes.controllers.Files.checkName(data).url, name: data
      .success (res) -> d.resolve()
      .error (data, status) -> d.resolve ClientError.firstMsg(data, status)
      d.promise

    $scope.confirm = (grp) ->
      ModalDialog.open().result.then(
        -> FileList.delete grp
        ->)
    return
]

.run (editableOptions) -> editableOptions.theme = 'bs3'

angular.module('app').requires.push 'files.list'