this.views ?= {}
this.views.files ?= {}

views.files.index = angular.module 'files.list', [
  'xeditable'
  'api.cfs'
  'api.helper'
  'ui.parts'
]

views.files.index.factory 'INodeList', [
  'CFS'
  'Path'
  'LinkHeader'
  'Alert'
  (CFS, Path, LinkHeader, Alert) ->
    service        = {}
    service.links  = LinkHeader.links
    service.inodes = []
    service.params = {}

    service.reload = (params) ->
      service.params = params

      # since we're going to show files under user'home folder,
      # and the name of user's home folder is the same as user's id
      service.path = Path.create(params.path)
      service.realPath = Path.create(service.path.prepend(params.userId))

      CFS.find(service.realPath)
        .success (data, status, headers, config) ->
          service.inodes =
            _.chain data
              .map (inode) ->
                inode.path = Path.create(inode.path)
                inode
              .value()
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers

    service.delete = (file) ->
      CFS.delete(file.path)
        .success (data, status, headers, config) ->
          idx = service.inodes.indexOf(file)
          service.inodes.splice idx, 1
        .error (data, status, headers, config) ->
          Alert.push
            type : 'danger'
            msg  : data.message

    service
]

.controller 'FilesCtrl', [
  '$scope'
  '$http'
  '$q'
  'ClientError'
  'INodeList'
  'ModalDialog'
  ($scope, $http, $q, ClientError, INodeList, ModalDialog) ->
    $scope.INodeList        = INodeList
    $scope.jsRoutes         = jsRoutes
    $scope.path             = INodeList.path
    $scope.realPath         = INodeList.realPath
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.checkName = (data) ->
      d = $q.defer()
      $http.post jsRoutes.controllers.Files.checkName(data).url, name: data
      .success (res) -> d.resolve()
      .error (data, status) -> d.resolve ClientError.firstMsg(data, status)
      d.promise

    $scope.confirmDelete = (file) ->
      ModalDialog.open().result.then(
        -> INodeList.delete file
        ->)
    return
]

.run (editableOptions) -> editableOptions.theme = 'bs3'

angular.module('app').requires.push 'files.list', 'flow'