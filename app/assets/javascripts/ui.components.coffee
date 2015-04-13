angular.module 'ui.components', [ 'ui.components.toggle-switch' ]

angular.module 'ui.components.toggle-switch', [ 'ui.template/buttons/toggle-switch.html' ]

  .controller 'ToggleSwitchController', [ '$scope', '$http', '$attrs', ($scope, $http, $attrs) ->
    isRemote = 'postTo' of $attrs
    $scope.status = $scope.$eval($attrs.status) == true
    $scope.toggle = ->
      if isRemote
        $http.post $attrs.postTo, value: !$scope.status
          .success (data) ->
            $scope.status = data.value
            return
          .error (data, status) ->
            console.log status
            console.log data
            return
      else
        $scope.status = !$scope.status
      return
    return
  ]

  .directive 'toggleSwitch', ->
      restrict    : 'E'
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
        """
      )
      return
  ]