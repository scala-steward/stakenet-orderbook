package io.stakenet.orderbook.actors.peers.ws

import io.stakenet.orderbook.actors.peers.protocol.Command

/**
 * This is what gets transferred from the web socket client
 */
case class WebSocketIncomingMessage(clientMessageId: String, command: Command)
