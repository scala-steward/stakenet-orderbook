package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.orders.PeerOrder
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.models.trading.{Trade, TradingOrder, TradingPair}

private[peers] case class PeerState(
    messageCounter: Long,
    private val myOpenOrders: List[TradingOrder],
    matched: List[PeerTrade]
) {

  def add(order: TradingOrder): PeerState = copy(myOpenOrders = order :: myOpenOrders)

  def findOpenOrder(id: OrderId): Option[TradingOrder] = {
    myOpenOrders.find(_.value.id == id)
  }

  def cancelOpenOrder(id: OrderId): PeerState = {
    val newOpenOrders = myOpenOrders.filter(_.value.id != id)
    copy(myOpenOrders = newOpenOrders)
  }

  def countActiveOrders: Int = myOpenOrders.size

  def removeMatched(myOrderId: OrderId): PeerState = {
    val newMatched =
      matched.filterNot(x => (x.trade.orders contains myOrderId))
    copy(matched = newMatched)
  }

  def removeOrders(tradingPair: TradingPair): PeerState = {
    val newOpenOrders = myOpenOrders.filterNot(_.pair == tradingPair)
    val newMatched = matched.filterNot(x => x.trade.pair == tradingPair)
    copy(myOpenOrders = newOpenOrders, matched = newMatched)
  }

  // TODO: validate existing orders
  def `match`(trade: Trade, matchedOrder: PeerOrder): PeerState = {
    val newActiveOrders = myOpenOrders.filterNot(trade.orders contains _.value.id)
    val newMatchedPair = PeerTrade(trade, matchedOrder)
    val newMatched = newMatchedPair :: matched
    copy(myOpenOrders = newActiveOrders, matched = newMatched)
  }

  def consumeMessage: PeerState = copy(messageCounter = messageCounter + 1)

  def removeTrade(tradeId: Trade.Id): PeerState = {
    val newMatched = matched.filterNot(_.trade.id == tradeId)
    copy(matched = newMatched)
  }
}

object PeerState {
  val empty: PeerState = PeerState(1, List.empty, List.empty)
}
