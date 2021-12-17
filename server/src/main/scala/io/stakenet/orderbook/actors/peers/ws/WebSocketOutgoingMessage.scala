package io.stakenet.orderbook.actors.peers.ws

import io.stakenet.orderbook.actors.peers.protocol.Event

/** This is what gets transferred to the web socket client
  */
case class WebSocketOutgoingMessage(
    messageCounter: Long, // number of messages sent to the ws client
    clientMessageId: Option[String], // client id, included if event is the response to a commands
    event: Event
)
