package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class TradesFeeReport(
    currency: Currency,
    fee: Satoshis,
    volume: Satoshis,
    totalOrders: BigInt,
    refundedFee: Satoshis
) {
  val profit: BigDecimal = fee.toBigDecimal - refundedFee.toBigDecimal
}

object TradesFeeReport {

  def empty(currency: Currency): TradesFeeReport =
    TradesFeeReport(currency, Satoshis.Zero, Satoshis.Zero, BigInt(0), Satoshis.Zero)
}
