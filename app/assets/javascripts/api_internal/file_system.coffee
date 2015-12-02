# -------------------------------------------------------- #
# Model CFS
# -------------------------------------------------------- #
angular.module 'api_internal.cfs', []

.factory 'CFS', [
  '$http'
  ($http) ->
    resource = {}

    resource.list = (path) ->
      $http(
        method : 'GET'
        url    : "/api_internal/cfs/list/#{path.encode()}"
      )

    resource.delete = (path) ->
      $http(
        method : 'DELETE'
        url    : "/api_internal/cfs/#{path.encode()}"
      )

    resource.updatePermission = (path, pos, gid) ->
      params = pos : pos
      params['gid'] = gid if gid?
      $http(
        method : 'POST'
        url    : "/api_internal/cfs/perm/#{path.encode()}"
        params : params
      )

    resource.deletePermission = (path, gid) ->
      $http(
        method : 'DELETE'
        url    : "/api_internal/cfs/perm/#{path.encode()}"
        params : gid : gid
      )

    return resource
]

#
# Helper to operate Path in javascript
#
.factory 'Path', ->
  Path = (path) ->
    segments : path.segments.slice()
    filename : path.filename
    set      : (filename) -> @copy (that) -> that.filename = filename
    prepend  : (segment)  -> @copy (that) -> that.segments.unshift segment
    append   : (segment)  -> @copy (that) -> that.segments.push segment
    encode   : ->
      segments = ("#{encodeURIComponent(s)}/" for s in @segments).join ''
      filename = if @filename? then encodeURIComponent(@filename) else ''
      "#{segments}#{filename}"
    copy     : (fn) ->
      that = new Path(this)
      fn(that)
      that

  return Path

#
# Helper to convert filename to font awesome icon name
#
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

#
# Need Math.round10 defined in utility.coffee
#
.filter 'filesize', ->
  (input = 0) ->
    k = 1000
    if input < k
      return "#{Math.round10(input, -3)} bytes"
    if (input /= k) < k
      return "#{Math.round10(input, -0)} KB"
    if (input /= k) < k
      return "#{Math.round10(input, -1)} MB"
    if (input /= k) < k
      return "#{Math.round10(input, -2)} GB"
    input /= k
    return   "#{Math.round10(input, -3)} TB"