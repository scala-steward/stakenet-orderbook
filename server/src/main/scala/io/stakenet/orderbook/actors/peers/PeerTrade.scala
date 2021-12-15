package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.orders.PeerOrder
import io.stakenet.orderbook.models.trading.Trade

case class PeerTrade(trade: Trade, secondOrder: PeerOrder)
