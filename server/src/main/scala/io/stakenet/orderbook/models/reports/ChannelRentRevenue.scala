package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelRentRevenue(
    payingCurrency: Currency,
    rentingFee: Satoshis,
    transactionFee: Satoshis,
    forceClosingFee: Satoshis
)

object ChannelRentRevenue {

  def empty(payingCurrency: Currency): ChannelRentRevenue = {
    ChannelRentRevenue(payingCurrency, Satoshis.Zero, Satoshis.Zero, Satoshis.Zero)
  }
}
