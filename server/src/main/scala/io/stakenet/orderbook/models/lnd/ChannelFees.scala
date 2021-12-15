package io.stakenet.orderbook.models.lnd

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelFees(currency: Currency, rentingFee: Satoshis, transactionFee: Satoshis, forceClosingFee: Satoshis) {
  def totalFee: Satoshis = (rentingFee + transactionFee + forceClosingFee).max(Satoshis.One)
  def extensionFee: Satoshis = rentingFee.max(Satoshis.One)
}
