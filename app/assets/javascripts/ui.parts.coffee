angular.module 'ui.parts', [ 'ui.parts.toggle-switch' ]

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