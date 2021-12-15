package io.stakenet.orderbook.models.trading

import java.time.Instant
import java.util.UUID

import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.services.validators.Fees

case class Trade(
    id: Trade.Id,
    pair: TradingPair,
    price: Satoshis,
    size: Satoshis,
    existingOrder: OrderId,
    executingOrder: OrderId,
    executingOrderSide: OrderSide,
    executedOn: Instant,
    existingOrderFunds: Satoshis
) {
  lazy val orders = List(existingOrder, executingOrder)
  def buyingCurrency: Currency = pair.principal
  def sellingCurrency: Currency = pair.secondary
  def buyingOrderId: OrderId = if (executingOrderSide == OrderSide.Buy) executingOrder else existingOrder
  def sellingOrderId: OrderId = if (executingOrderSide == OrderSide.Sell) executingOrder else existingOrder

  def buyOrderFunds: Satoshis = size * price
  def buyOrderFee: Satoshis = Fees.getFeeValue(buyOrderFunds, pair.buyFee)
  def sellOrderFee: Satoshis = Fees.getFeeValue(size, pair.sellFee)
}

object Trade {

  case class Id(value: UUID) extends AnyVal

  /**
   * Construct a trade from two orders, the executingOrder order is the most recent one,
   * and the existingOrder is the one that was already stored on the orderbook.
   *
   * This means that we'll use the existingOrder order price.
   *
   * This doesn't validate whether the orders are compatible to be traded, that's supposed to be already verified.
   */
  def from(pair: TradingPair)(
      executingOrder: pair.Order,
      existingOrder: pair.LimitOrder
  ): Trade = {
    require(executingOrder.side != existingOrder.side)
    // assume pair = XSN_BTC
    val price = existingOrder.details.price // BTC

    // we must calculate the size of the buy order to get the amount in the same currency as sell order
    val (buyOrderSize, sellOrderSize) =
      if (executingOrder.side == OrderSide.Buy) (executingOrder.funds / price, existingOrder.funds)
      else (executingOrder.funds, existingOrder.funds / price)

    val size = sellOrderSize min buyOrderSize // XSN
    Trade(
      id = Id(UUID.randomUUID()),
      pair = pair,
      executingOrder = executingOrder.id,
      existingOrder = existingOrder.id,
      price = price,
      size = size,
      executingOrderSide = executingOrder.side,
      executedOn = Instant.now(),
      existingOrderFunds = existingOrder.funds
    )
  }
}
