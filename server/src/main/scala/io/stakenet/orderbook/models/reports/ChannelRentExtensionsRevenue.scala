package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelRentExtensionsRevenue(payingCurrency: Currency, amount: Satoshis)

object ChannelRentExtensionsRevenue {
  def empty(currency: Currency): ChannelRentExtensionsRevenue = ChannelRentExtensionsRevenue(currency, Satoshis.Zero)
}
