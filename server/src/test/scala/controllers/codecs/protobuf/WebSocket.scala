package controllers.codecs.protobuf

import controllers.WebSocketControllerSpec.ActiveWebSocket
import play.api.test.WsTestClient.InternalWSClient

class WebSocket(client: InternalWSClient, socket: ActiveWebSocket) {
  def getSocket: ActiveWebSocket = socket

  def close(): Unit = {
    client.close()
  }
}

object WebSocket {

  def apply(client: InternalWSClient, socket: ActiveWebSocket): WebSocket = {
    new WebSocket(client, socket)
  }
}
