package io.stakenet.orderbook.actors.peers.protocol

/** Just a command response tagged with a request id
  */
case class TaggedCommandResponse(requestId: String, value: Event.CommandResponse)
