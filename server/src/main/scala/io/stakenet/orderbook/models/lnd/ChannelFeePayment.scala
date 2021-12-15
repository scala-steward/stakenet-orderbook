package io.stakenet.orderbook.models.lnd

import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.services.validators.Fees

import scala.concurrent.duration._

case class ChannelFeePayment(
    currency: Currency,
    payingCurrency: Currency,
    capacity: Satoshis,
    lifeTimeSeconds: Long,
    paidFee: Satoshis
) {
  val lifeTimeHours: BigDecimal = lifeTimeSeconds.seconds.toHours
  val lifeTimeDays: BigDecimal = (lifeTimeHours / 24).setScale(2, BigDecimal.RoundingMode.DOWN)

  def fees: ChannelFees = {
    ChannelFees(
      currency = currency,
      rentingFee = rentingFee,
      transactionFee = currency.networkFee,
      forceClosingFee = forceClosingFee
    )
  }

  private def hourlyFee: Satoshis = {
    Fees.getFeeValue(capacity, currency.rentChannelFeePercentage)
  }

  private def rentingFee: Satoshis = {
    val fee = (hourlyFee.toBigDecimal * lifeTimeHours).setScale(18, BigDecimal.RoundingMode.DOWN)

    Satoshis.from(fee).getOrElse(throw new RuntimeException("invalid channel fee"))
  }

  // The forceChannelCloseAverageTime is the time to close a channel with the force flag.
  private def forceClosingFee: Satoshis = {
    val hours = currency.forceChannelCloseAverageTime.toHours
    val fee = (hourlyFee.toBigDecimal * hours).setScale(18, BigDecimal.RoundingMode.DOWN)

    Satoshis.from(fee).getOrElse(throw new RuntimeException("invalid channel fee"))
  }
}
