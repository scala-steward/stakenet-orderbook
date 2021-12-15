package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelTransactionFee(
    rentedCurrency: Currency,
    fundingTransactionFee: Satoshis,
    closingTransactionFee: Satoshis,
    numRentals: Int,
    lifeTimeSeconds: Long,
    totalCapacity: Satoshis
)

object ChannelTransactionFee {

  def empty(rentedCurrency: Currency): ChannelTransactionFee =
    ChannelTransactionFee(rentedCurrency, Satoshis.Zero, Satoshis.Zero, 0, 0, Satoshis.Zero)
}
