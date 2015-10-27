this.views ?= {}
this.views.my ?= {}

views.my.profile = angular.module 'my.profile', [
  'flow'
]

.controller 'ProfileCtrl', [
  '$scope'
  ($scope) ->
    $scope.jsRoutes = jsRoutes
    $scope.imgURL = "#{jsRoutes.controllers.MyCtrl.profileImage().url}?s=-1"

    $scope.onUploaded = (resp) ->
      updateImgURL() if resp isnt ''

    updateImgURL = ->
      now = new Date()
      $scope.imgURL = "#{jsRoutes.controllers.MyCtrl.profileImage().url}?cb=#{now.getTime()}&s=-1"

    return
]

angular.module('app').requires.push 'my.profile'