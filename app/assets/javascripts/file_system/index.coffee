this.views ?= {}
this.views.files ?= {}

views.files.index = angular.module 'files.index', [
  'api_internal.cfs'
  'api.helper'
  'ui.parts'
]

views.files.index

.factory 'INodeListSvc', [
  'CFS'
  'Path'
  'LinkHeader'
  (CFS, Path, LinkHeader) ->
    service         = {}
    service.links   = LinkHeader.links
    service.inodes  = []
    service.options = {}

    service.load = ->
      @path = new Path(@options.path)

      CFS.find(@path)
        .success (data, status, headers) =>
          @inodes =
            _.chain data
              .filter (inode) -> inode.name isnt '.'
              .map    (inode) -> convertPath(inode)
              .sortBy (inode) -> !inode.is_directory
              .value()
          LinkHeader.updateLinks @options.nextPage, @options.prevPage, headers

    convertPath = (inode) ->
      inode.path = new Path(inode.path)
      inode

    service.add = (file) ->
      @inodes.push convertPath(file)

    service.delete = (file) ->
      @inodes.splice @inodes.indexOf(file), 1

    service.clear = ->
      @inodes = []

    service
]

.controller 'FilesCtrl', [
  '$scope'
  'CFS'
  'Path'
  'FileExtension'
  'INodeListSvc'
  'ModalDialog'
  'Alert'
  ($scope, CFS, Path, FileExtension, INodeListSvc, ModalDialog, Alert) ->
    $scope.INodeListSvc     = INodeListSvc
    $scope.jsRoutes         = jsRoutes
    $scope.uploading        = false
    $scope.faName           = FileExtension.faName
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.confirmDelete = (file) ->
      ModalDialog.open().result.then(
        -> $scope.delete file
        ->
      )

    $scope.confirmClearDir = ->
      ModalDialog.open().result.then(
        -> $scope.clear()
        ->
      )

    $scope.success = ($file, resp) ->
      $scope.uploading = false
      $scope.created(JSON.parse(resp)) if resp isnt ''

    $scope.startUpload = ->
      $scope.uploading = true

    $scope.delete = (file) ->
      CFS.delete(file.path)
        .success ->
          INodeListSvc.delete file
        .error (data) ->
          Alert.danger data.message

    $scope.clear = ->
      CFS.delete(INodeListSvc.path)
        .success ->
          INodeListSvc.clear()
        .error (data) ->
          Alert.danger data.message

    $scope.created = (file) ->
      INodeListSvc.add file

    INodeListSvc.load()
    return
]

angular.module('app').requires.push 'files.index', 'flow'