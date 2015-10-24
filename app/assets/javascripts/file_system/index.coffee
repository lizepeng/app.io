this.views ?= {}
this.views.files ?= {}

views.files.index = angular.module 'files.list', [
  'api_internal.cfs'
  'api.helper'
  'ui.parts'
]

views.files.index

.factory 'INodeList', [
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

      service.path = new Path(params.path)

      CFS.find(service.path)
        .success (data, status, headers) ->
          service.inodes =
            _.chain data
              .filter (inode) -> inode.name != '.'
              .map (inode) ->
                inode.path = new Path(inode.path)
                inode
              .sortBy (inode) -> !inode.is_directory
              .value()
          LinkHeader.updateLinks params.nextPage, params.prevPage, headers

    service.delete = (file) ->
      CFS.delete(file.path)
        .success ->
          idx = service.inodes.indexOf(file)
          service.inodes.splice idx, 1
        .error (data) ->
          Alert.push
            type : 'danger'
            msg  : data.message

    service.clear = ->
      CFS.delete(service.path)
        .success ->
          service.inodes = []
        .error (data) ->
          Alert.push
            type : 'danger'
            msg  : data.message

    service.created = (file) ->
      file.path = new Path(file.path)
      service.inodes.push file

    service
]

.controller 'FilesCtrl', [
  '$scope'
  'FileExtension'
  'INodeList'
  'ModalDialog'
  ($scope, FileExtension, INodeList, ModalDialog) ->
    $scope.INodeList        = INodeList
    $scope.jsRoutes         = jsRoutes
    $scope.path             = INodeList.path
    $scope.uploading        = false
    $scope.faName           = FileExtension.faName
    ModalDialog.templateUrl = 'confirm_delete.html'

    $scope.confirmDelete = (file) ->
      ModalDialog.open().result.then(
        -> INodeList.delete file
        ->
      )

    $scope.confirmClearDir = ->
      ModalDialog.open().result.then(
        -> INodeList.clear()
        ->
      )

    $scope.success = ($file, resp) ->
      $scope.uploading = false
      if resp isnt ''
        INodeList.created(JSON.parse(resp))

    $scope.startUpload = ->
      $scope.uploading = true

    return
]

# -------------------------------------------------------- #
# Need Math.round10 defined in utility.coffee
# -------------------------------------------------------- #
.filter 'filesize', ->
  (input = 0) ->
    k = 1000
    if input < k
      return "#{Math.round10(input, -3)} bytes"
    if (input /= k) < k
      return "#{Math.round10(input, -0)} KB"
    if (input /= k) < k
      return "#{Math.round10(input, -1)} MB"
    if (input /= k) < k
      return "#{Math.round10(input, -2)} GB"
    input /= k
    return   "#{Math.round10(input, -3)} TB"

angular.module('app').requires.push 'files.list', 'flow'