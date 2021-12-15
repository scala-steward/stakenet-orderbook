package io.stakenet.orderbook.models.reports

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelRentalReport(
    currency: Currency,
    rentingFees: Satoshis,
    transactionFees: Satoshis,
    forceClosingFees: Satoshis,
    extensionsRevenue: Satoshis,
    fundingTxFee: Satoshis,
    closingTxFee: Satoshis,
    numRentals: Int,
    numExtensions: Int,
    lifeTimeSeconds: Long,
    totalCapacity: Satoshis
) {

  val profit: BigDecimal = {
    rentingFees.toBigDecimal + transactionFees.toBigDecimal + forceClosingFees.toBigDecimal +
      extensionsRevenue.toBigDecimal - fundingTxFee.toBigDecimal - closingTxFee.toBigDecimal
  }
}

object ChannelRentalReport {

  def empty(currency: Currency): ChannelRentalReport =
    ChannelRentalReport(
      currency,
      Satoshis.Zero,
      Satoshis.Zero,
      Satoshis.Zero,
      Satoshis.Zero,
      Satoshis.Zero,
      Satoshis.Zero,
      0,
      0,
      0,
      Satoshis.Zero
    )
}
