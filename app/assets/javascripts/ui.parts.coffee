#
# Common UI Parts.
#
angular.module 'ui.parts', [
  'ui.parts.toggle-switch'
]

#
# Helper to make alert easier to use.
# msg format: {type:'danger', msg:'msg'}
#
.factory 'Alert', ->
  alerts  : []

  dismiss : (idx) -> @alerts.splice idx, 1

  push    : (alert) ->
    alert.type ?= 'info'
    @alerts.push alert

  show    : (type, msg) ->
    @push
      type : type
      msg  : msg

  success : (msg) -> @show 'success', msg
  info    : (msg) -> @show 'info'   , msg
  warning : (msg) -> @show 'warning', msg
  danger  : (msg) -> @show 'danger' , msg

.controller 'AlertCtrl', ['$scope', 'Alert', ($scope, Alert) ->
  $scope.Alert = Alert
  return
]

#
# Helper to make confirm message box easier to use.
#
.factory 'ModalDialog', [
  '$modal'
  ($modal) ->
    service  = {}
    service.templateUrl = ''
    service.open = -> $modal.open
      templateUrl : service.templateUrl
      controller  : 'ModalDialogCtrl'
    service
]

.controller 'ModalDialogCtrl', [
  '$scope'
  '$modalInstance'
  ($scope, $modalInstance) ->
    $scope.ok =
      -> $modalInstance.close()
    $scope.cancel =
      -> $modalInstance.dismiss 'cancel'
    return
]

#
# Toggle switch button - iOS style.
#
angular.module 'ui.parts.toggle-switch', [ 'ui.template/buttons/toggle-switch.html' ]

.controller 'ToggleSwitchController', [ '$scope', ($scope) ->
  $scope.toggle = ->
    $scope.status = !$scope.status
    return
  return
]

.directive 'toggleSwitch', ->
  restrict    : 'E'
  scope       :
    status : '='
  controller  : 'ToggleSwitchController'
  templateUrl : 'ui.template/buttons/toggle-switch.html'

angular.module 'ui.template/buttons/toggle-switch.html', []

.run [
  '$templateCache'
  ($templateCache) ->
    $templateCache.put(
      'ui.template/buttons/toggle-switch.html',
      """
      <div class="toggle-switch">
        <i class="fa fa-2x fa-toggle-on"
           ng-class="status?'active':'inactive fa-rotate-180'"
          ng-click="toggle()">
        </i>
      </div>
      """)
    return
]