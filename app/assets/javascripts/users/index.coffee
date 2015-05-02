this.views ?= {}
this.views.users ?= {}

views.users.index = angular.module 'users.list', [
  'ui.bootstrap'
  'api.user'
  'api.helper'
  'ui.parts'
]

views.users.index.factory 'UserList', [
  'User'
  'LinkHeader'
  'Alert'
  (User, LinkHeader, Alert) ->
    service               = {}
    service.links         = LinkHeader.links
    service.users         = []
    service.options       = {}

    service.reload = (q) ->
      opt = service.options
      service.users = User.query
        page     : opt.page
        per_page : opt.pageSize
        q        : """*#{q}*""",
        (value, headers) ->
          LinkHeader.updateLinks opt.nextPage, opt.prevPage, headers
          return
      return

    service.create = (data) ->
      new User(data).$save(
        (value) ->
          service.users.unshift value
          return
        (res) ->
          Alert.push
            type : 'danger'
            msg  : res.data.message
          return)
      return

    service
]

.controller 'UsersCtrl', [
  '$scope'
  'UserList'
  ($scope, UserList) ->
    $scope.UserList = UserList
    $scope.jsRoutes = jsRoutes
    $scope.keyword  = ''

    $scope.$watch 'keyword', (nv, ov) ->
      UserList.reload nv
      return
    return
]

angular.module('app').requires.push 'users.list'