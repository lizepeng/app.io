angular.module 'ws.helper', []

  .factory 'UserWebSocket', ->
    service            = {}
    service.url        = ''
    service.handlers   = {}
    service.socket     = {}
    service.READYSTATE =
      CONNECTING : 0
      OPEN       : 1
      CLOSING    : 2
      CLOSED     : 3

    service.connect = ->
      @socket = new WebSocket @url
      @socket.onmessage = (event) =>
        msg = JSON.parse(event.data)
        handler = @handlers[msg.protocol]
        handler.onmessage(msg) if handler?
        return
      return

    service.register = (confs...) ->
      @handlers[conf.protocol] = conf for conf in confs
      return

    service.send = (msg) ->
      @socket.send JSON.stringify(msg) if @readyState() is @READYSTATE.OPEN
      return

    service.readyState = ->
      @socket?.readyState ? @READYSTATE.CLOSED

    return service