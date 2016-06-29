#
# Common UI Helpers.
#
angular.module 'ui.common', []

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
  '$element'
  '$timeout'
  ($scope, $element, $timeout) ->

    $scope.minimalize = ->
      $('body').toggleClass 'mini-navbar'
      if !$('body').hasClass('mini-navbar') or $('body').hasClass('body-small')
        # Hide menu in order to smoothly turn on when maximize menu
        $('#side-menu').hide()
        # For smoothly turn on menu
        $timeout (->
          $('#side-menu').fadeIn 400
        ), 200
      else
        # Remove all inline style from jquery fadeIn function to reset menu state
        $('#side-menu').removeAttr 'style'
      return

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