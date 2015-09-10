# -------------------------------------------------------- #
# Model CFS
# -------------------------------------------------------- #
angular.module 'api_internal.cfs', []

  .factory 'CFS', [ '$http', ($http) ->
    resource = {}

    resource.find = (path) ->
      $http.get "/api_internal/cfs/list/#{path.encode()}"

    resource.delete = (path) ->
      $http.delete "/api_internal/cfs/#{path.encode()}"

    return resource
  ]

  #
  # Helper to operate Path in javascript
  #
  .factory 'Path', ->
    service = {}
    service.create = (param) ->
      parts       : param.parts
      filename    : param.filename
      set         : (filename) ->
        @copy (that) ->
          that.filename = filename
      prepend     : (part) ->
        @copy (that) ->
          that.parts.unshift part
      append      : (part) ->
        @copy (that) ->
          that.parts.push part
      copy        : (fun) ->
        that = angular.copy @
        if fun?
          fun(that)
        that
      # Content should be same as Path.javascriptUbind
      encode      : ->
        parts =
          _.chain @parts
            .map (part)-> "#{encodeURIComponent(part)}/"
            .join ''
            .value()
        filename =
          if !@filename? then ''
          else encodeURIComponent(@filename)
        "#{parts}#{filename}"

    return service