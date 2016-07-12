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

      CFS.list(@path, @options.pager).then (resp) =>
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
  'ClientError'
  'Alert'
  ($scope, CFS, Path, FileExtension, INodeListSvc, ClientError, Alert) ->
    $scope.INodeListSvc     = INodeListSvc
    $scope.jsRoutes         = jsRoutes
    $scope.uploading        = false
    $scope.faName           = FileExtension.faName

    $scope.confirmDelete = (file) ->
      swal(INodeListSvc.confirmDelete).then(
        -> $scope.delete file
        (dismiss) ->
      )

    $scope.confirmClear = ->
      swal(INodeListSvc.confirmClear).then(
        -> $scope.clear()
        (dismiss) ->
      )

    $scope.flowUploadStarted = ($flow) ->
      $scope.uploading = true
      $scope.upload_progress = 0

    $scope.flowProgress = ($flow) ->
      $scope.upload_progress = Math.min(0.99, $flow.progress())

    $scope.flowfileSuccess = ($file, resp) ->
      $scope.created(JSON.parse(resp)) if resp isnt ''

    $scope.flowComplete = ($flow) ->
      $scope.upload_progress = 1
      $scope.uploading = false
      $flow.cancel()

    $scope.delete = (file) ->
      CFS.delete(file.path).then(
         -> INodeListSvc.delete file
        (resp) -> Alert.danger ClientError.firstMsg(resp)
      )

    $scope.clear = ->
      CFS.delete(INodeListSvc.path).then(
        -> INodeListSvc.clear()
        (resp) -> Alert.danger ClientError.firstMsg(resp)
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