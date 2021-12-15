package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.models.clients.ClientId

/**
 * Represents a user connected on the websocket.
 *
 * @param name the name to identify the user
 * @param maxAllowedOrders the maximum number of orders that can be queued by this user
 */
sealed abstract class PeerUser(val name: String, val maxAllowedOrders: Int)

object PeerUser {

  final case object WebOrderbook extends PeerUser("WebOrderbook", 0)

  final case class Wallet(id: ClientId, override val name: String, override val maxAllowedOrders: Int)
      extends PeerUser(name, maxAllowedOrders)
  final case class Bot(id: ClientId, paysFees: Boolean, override val name: String, override val maxAllowedOrders: Int)
      extends PeerUser(name, maxAllowedOrders)
}
