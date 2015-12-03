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

      CFS.list(@path).then (resp) =>
        @inodes =
          _.chain resp.data
            .filter (inode) -> inode.name isnt '.'
            .map    (inode) -> decorate(inode)
            .sortBy (inode) -> !inode.is_directory
            .value()
        LinkHeader.updateLinks @options.nextPage, @options.prevPage, resp.headers

    decorate = (inode) ->
      inode.path = new Path(inode.path)
      inode.permissions = ([false, false, false] for i in [0..20])
      inode.permissions[Math.floor(idx / 3)][idx % 3] = true for idx in inode.permission
      inode.collapsed = true
      inode

    service.add = (file) ->
      @inodes.push decorate(file)

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
      CFS.delete(file.path).then(
         -> INodeListSvc.delete file
        (data) -> Alert.danger data.message
      )

    $scope.clear = ->
      CFS.delete(INodeListSvc.path).then(
        -> INodeListSvc.clear()
        (data) -> Alert.danger data.message
      )

    $scope.created = (file) ->
      INodeListSvc.add file

    $scope.toggle = (inode, role, access) ->
      CFS.updatePermission(inode.path, role * 3 + access)

    $scope.roleName = (idx) ->
      switch idx
        when 0 then INodeListSvc.dictionary['owner']
        when 20 then INodeListSvc.dictionary['other']
        else INodeListSvc.dictionary['intGroupNames'][idx - 1]

    INodeListSvc.load()
    return
]

angular.module('app').requires.push 'files.index', 'flow'