angular.module 'ui.notification', [ 'ws.helper' ]

  .controller 'NotificationCtrl', [
    '$scope'
    'UserWebSocket'
    ($scope, UserWebSocket) ->
      $scope.notifications = []

      UserWebSocket.register
        protocol  : 'notification'
        onmessage : (notify) ->
          notify.type ?= 'info'
          $scope.notifications.push notify
          $scope.$apply()

      UserWebSocket.connect()

      $scope.dismiss = (idx) ->
        $scope.notifications.splice idx, 1

      return
  ]