this.views ?= {}
this.views.users ?= {}

views.users.index = angular.module 'users.index', [
  'ui.bootstrap'
  'api_internal.user'
  'api.helper'
  'ui.parts'
]

views.users.index.factory 'UserListSvc', [
  'User'
  'LinkHeader'
  (User, LinkHeader) ->
    service         = {}
    service.links   = LinkHeader.links
    service.values  = []
    service.options = {}

    service.reload = (q) ->
      @values = User.query
        page     : @options.page
        per_page : @options.pageSize
        sort     : @options.sort.join(',')
        q        : "email:#{q}*",
        (value, headers) =>
          LinkHeader.updateLinks @options.nextPage, @options.prevPage, headers

    return service
]

.controller 'UsersCtrl', [
  '$scope'
  'User'
  'UserListSvc'
  'ClientError'
  'Alert'
  ($scope, User, UserListSvc, ClientError, Alert) ->
    $scope.UserListSvc = UserListSvc
    $scope.jsRoutes    = jsRoutes
    $scope.keyword     = ''

    $scope.create = (data) ->
      new User(data).$save(
        (value) -> UserListSvc.values.unshift value
        (resp) -> Alert.danger ClientError.firstMsg(resp)
      )

    $scope.$watch 'keyword', (nv) -> UserListSvc.reload nv

    return
]

angular.module('app').requires.push 'users.index'