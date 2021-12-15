package io.stakenet.orderbook.actors.orders

import akka.actor.ActorRef
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.TradingOrder

case class PeerOrder(clientId: ClientId, peer: ActorRef, order: TradingOrder) {
  def orderId: OrderId = order.value.id
}
