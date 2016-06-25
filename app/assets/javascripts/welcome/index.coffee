this.views ?= {}
this.views.welcome ?= {}

views.welcome.index = angular.module 'welcome.index', [ 'ngSanitize', 'ngAnimate' ]

.controller 'CarouselCtrl', [
  '$scope'
  ($scope) ->
    $scope.interval = 5000
    $scope.slides   = [ {
        id      : 0
        classes : 'item1'
        content : """
          <div class="carousel-caption">
            <h1>Welcome To App.io</h1>
            <p class="lead">
              <i class="fa fa-github fa-5x pull-left"></i>
              Note: If you're viewing this page via a <code>http://URL</code>, the 'next' and 'previous' Glyphicon buttons on the left and right might not load/display properly due to web browser security policy.
            </p>
            <p>
              <a class="btn btn-lg btn-success" href="#{jsRoutes.controllers.UsersCtrl.nnew().url}">
                Sign up for App.io
              </a>
            </p>
        </div> """
      }, {
        id      : 1
        classes : 'item2'
        content : """
          <div class="carousel-caption">
            <h1>Another example headline</h1>
            <p class="lead">
              <i class="fa fa-mobile fa-5x pull-left"></i>
              Cras justo odio, dapibus ac facilisis in, egestas eget quam. Donec id elit non mi porta gravida at eget metus. Nullam id dolor id nibh ultricies vehicula ut id elit.
            </p>
            <p>
              <a class="btn btn-lg btn-primary" href="#{jsRoutes.controllers.Application.wiki().url}">
                Learn More
              </a>
            </p>
        </div> """
      }, {
        id      : 2
        classes : 'item3'
        content : """
          <div class="carousel-caption">
            <h1>One more for good measure!</h1>
            <p class="lead">
              <i class="fa fa-apple fa-5x pull-left"></i>
              Cras justo odio, dapibus ac facilisis in, egestas eget quam. Donec id elit non mi porta gravida at eget metus. Nullam id dolor id nibh ultricies vehicula ut id elit.
            </p>
            <p>
              <a class="btn btn-lg btn-default" href="#">
                Browse gallery
              </a>
            </p>
        </div> """
      } ]

    return
]

angular.module('app').requires.push 'welcome.index'