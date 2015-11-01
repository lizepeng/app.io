angular.module 'ui.common', []

# if ng-src in image resolves to a 404, then fallback to err-src
.directive 'errSrc',  [
  ->
    link: (scope, element, attrs) ->
      element.bind 'error', -> attrs.$set('src', attrs.errSrc) if attrs.src isnt attrs.errSrc
]