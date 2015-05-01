angular.module 'ui.parts', [ 'ui.parts.toggle-switch' ]

  # Helper to make alert easier to use
  # msg format: {type:'danger', msg:'msg'}
  .factory 'Alert', ->
    alerts  : []
    dismiss : (idx) ->
      this.alerts.splice(idx, 1)
    push    : (msg) ->
      this.alerts.push(msg)

  .controller 'AlertCtrl', ['$scope', 'Alert', ($scope, Alert) ->
    $scope.Alert = Alert
    return
  ]

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