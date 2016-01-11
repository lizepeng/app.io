#
# UI Notification.
#
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

    ###
    Dismiss a notification box has been shown on the screen.

    @param idx [Integer] the index of the notification
    ###
    $scope.dismiss = (idx) ->
      $scope.notifications.splice idx, 1

    return
]