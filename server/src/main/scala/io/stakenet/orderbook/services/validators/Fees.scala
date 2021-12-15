package io.stakenet.orderbook.services.validators

import io.stakenet.orderbook.models.trading.TradingOrder
import io.stakenet.orderbook.models.{Currency, Satoshis}

object Fees {

  def getCurrencyPayment(order: TradingOrder): Currency = {
    order.value.feeCurrency
  }

  def getFeeAmount(order: TradingOrder): Satoshis = {
    getFeeValue(order.value.funds, order.value.feePercent)
  }

  def getFeeValue(funds: Satoshis, percent: BigDecimal): Satoshis = {
    val amount = (funds.toBigDecimal * percent).setScale(18, BigDecimal.RoundingMode.DOWN)

    Satoshis
      .from(amount)
      .map(Satoshis.One.max)
      .getOrElse(
        throw new RuntimeException("Impossible, the value should be satoshis")
      )
  }

  def getPaidAmount(fee: Satoshis, percent: BigDecimal): Satoshis = {
    val amount = (fee.toBigDecimal / percent).setScale(18, BigDecimal.RoundingMode.UP)

    Satoshis
      .from(amount)
      .getOrElse(
        throw new RuntimeException("Impossible, the value should be satoshis")
      )
  }
}
