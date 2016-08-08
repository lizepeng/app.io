#
# Common UI Helpers.
#
angular.module 'ui.common', [ 'ngCookies' ]

#
# If ng-src in image resolves to a 404, then fallback to err-src.
#
.directive 'errSrc',  [
  ->
    link: (scope, element, attrs) ->
      element.bind 'error', -> attrs.$set('src', attrs.errSrc) if attrs.src isnt attrs.errSrc
]

.controller 'minimalizaSidebarCtrl', [
  '$scope'
  '$cookies'
  '$timeout'
  ($scope, $cookies, $timeout) ->

    $scope.minimalize = ->
      previous = $cookies.get('mini-navbar') is 'Y'
      minimalized = if previous then 'N' else 'Y'
      # Set cookie
      $cookies.put 'mini-navbar', minimalized, path : '/'
      # Toggle mini-navbar
      $('body').toggleClass 'mini-navbar', minimalized
      if !minimalized or $('body').hasClass('body-small')
        # Hide menu in order to smoothly turn on when maximize menu
        $('.side-menu').hide()
        # For smoothly turn on menu
        $timeout (->
          $('.side-menu').fadeIn 400
        ), 200
      else
        # Remove all inline style from jquery fadeIn function to reset menu state
        $('.side-menu').removeAttr 'style'
      return

    # Set mini-navbar based on cookie
    $('body').toggleClass 'mini-navbar', $cookies.get('mini-navbar') is 'Y'

    return
]

.directive 'minimalizaSidebar',  [
  ->
    {
      restrict: 'A'
      template: """
        <a class="navbar-minimalize minimalize-styl-2 btn btn-primary " href="" ng-click="minimalize()">
          <i class="fa fa-bars"></i>
        </a>"""
      controller: 'minimalizaSidebarCtrl'
    }
]

#
# upload percentage
#
.filter 'percent', [ ->
  return (value) ->
    p1 = Math.floor((value || 0) * 100)
    p2 = Math.max(0, Math.min(100, p1))
    return """#{p2}%"""
  ]