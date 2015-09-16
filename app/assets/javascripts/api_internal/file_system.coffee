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

  .factory 'FileExtension', ->
    service = {}

    service.faName = (filename) ->
      file_ext = filename.split('.').pop().toLowerCase()
      switch file_ext
        when 'png'    then 'fa-file-image-o'
        when 'jpg'    then 'fa-file-image-o'
        when 'gif'    then 'fa-file-image-o'
        when 'mp3'    then 'fa-file-audio-o'
        when 'wma'    then 'fa-file-audio-o'
        when 'm4a'    then 'fa-file-audio-o'
        when 'text'   then 'fa-file-text-o'
        when 'pdf'    then 'fa-file-pdf-o'
        when 'ps'     then 'fa-file-pdf-o'
        when 'doc'    then 'fa-file-word-o'
        when 'docx'   then 'fa-file-word-o'
        when 'ppt'    then 'fa-file-powerpoint-o'
        when 'xls'    then 'fa-file-excel-o'
        when 'xlsx'   then 'fa-file-excel-o'
        when 'mp4'    then 'fa-file-video-o'
        when 'mkv'    then 'fa-file-video-o'
        when 'mov'    then 'fa-file-video-o'
        when 'zip'    then 'fa-file-archive-o'
        when 'rar'    then 'fa-file-archive-o'
        when '7z'     then 'fa-file-archive-o'
        when 'tar'    then 'fa-file-archive-o'
        when 'gz'     then 'fa-file-archive-o'
        when 'bz'     then 'fa-file-archive-o'
        when 'html'   then 'fa-file-code-o'
        when 'css'    then 'fa-file-code-o'
        when 'js'     then 'fa-file-code-o'
        when 'xml'    then 'fa-file-code-o'
        when 'scala'  then 'fa-file-code-o'
        when 'coffee' then 'fa-file-code-o'
        else 'fa-file-o'

    return service