this.views ?= {}
this.views.my ?= {}

views.my.profile = angular.module 'my.profile', [
  'flow'
]

.controller 'ProfileCtrl', [
  '$scope'
  ($scope) ->
    $scope.jsRoutes  = jsRoutes
    $scope.imgURL    = "#{jsRoutes.controllers.MyCtrl.profileImage().url}?s=64"
    $scope.uploading = false

    $scope.onUploaded = (resp) ->
      $scope.uploading = false
      updateImgURL() if resp isnt ''

    updateImgURL = ->
      now = new Date()
      $scope.imgURL = "#{jsRoutes.controllers.MyCtrl.profileImage().url}?cb=#{now.getTime()}&s=64"

    $scope.startUpload = ->
      $scope.uploading = true

    return
]

angular.module('app').requires.push 'my.profile'