#
# WebSocket Helper.
#
angular.module 'ws.helper', []

#
# WebSocket Options.
#
# @option options [Boolean] autoConnect whether connect to server automatically
#
.factory 'WebSocketOptions', ->
  options = {}
  return options

#
# WebSocket Helper.
#
.factory 'UserWebSocket', [
  '$timeout'
  'WebSocketOptions'
  ($timeout, options) ->

    #
    # WebSocket Helper that could has mulitple protocol handlers.
    #
    class UserWebSocket

      ###
      Construct a websocket helper.

      @url [String] the url this helper connect to
      ###
      constructor: (url) ->
        @url = url
        @handlers = {}
        $timeout @connect, 2000 if options.autoConnect ? true

      ###
      Register handlers that could deal with messages received.

      @param [Array<Object>] handlers
      @option handler [Object] protocol the protocol name
      @option handler [Object] onmessage the function that deal with message

      @example
        protocol  : 'chat'
        onmessage : (msg) -> # deal with the 'msg'
      ###
      register: (handlers...) ->
        @handlers[handler.protocol] = handler for handler in handlers
        return

      ###
      Connect to server.

      ###
      connect: =>
        @socket = new WebSocket @url
        @socket.onmessage = (event) =>
          msg = JSON.parse(event.data)
          handler = @handlers[msg.protocol]
          handler.onmessage(msg) if handler?
          return

        # reconnect after 10 seconds
        @socket.onclose = (event) =>
          $timeout @connect, 10000
          return

        return

      ###
      Send a message to server.

      @param msg [Object] the message that to send to server
      ###
      send: (msg) ->
        @socket.send JSON.stringify(msg) if @readyState() is WebSocket.OPEN
        return

      ###
      Get the current state of websocket

      ###
      readyState: ->
        @socket?.readyState ? WebSocket.CLOSED

    return new UserWebSocket options.url
  ]