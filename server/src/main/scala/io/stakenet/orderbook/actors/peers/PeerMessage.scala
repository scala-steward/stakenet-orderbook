package io.stakenet.orderbook.actors.peers

import akka.actor.ActorRef
import io.stakenet.orderbook.actors.peers.ws.WebSocketIncomingMessage

case class PeerMessage(peer: ActorRef, message: WebSocketIncomingMessage)
