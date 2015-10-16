angular.module 'ws.helper', []

  .factory 'WebSocketOptions', ->
    options = {}
    return options

  .factory 'UserWebSocket', [
    'WebSocketOptions'
    (options) ->
      class UserWebSocket

        constructor: (url) ->
          @url = url
          @handlers = {}
          @connect() if options.autoConnect ? true

        register: (handlers...) ->
          @handlers[handler.protocol] = handler for handler in handlers
          return

        connect: ->
          @socket = new WebSocket @url
          @socket.onmessage = (event) =>
            msg = JSON.parse(event.data)
            handler = @handlers[msg.protocol]
            handler.onmessage(msg) if handler?
            return
          return

        send: (msg) ->
          @socket.send JSON.stringify(msg) if @readyState() is WebSocket.OPEN
          return

        readyState: ->
          @socket?.readyState ? WebSocket.CLOSED

      return new UserWebSocket options.url
    ]