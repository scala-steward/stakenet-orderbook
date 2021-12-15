package io.stakenet.orderbook.models

import java.time.Instant

import io.stakenet.orderbook.models.lnd.PaymentRHash

case class ChannelExtension[+A <: ChannelId](
    paymentHash: PaymentRHash,
    payingCurrency: Currency,
    channelId: A,
    fee: Satoshis,
    seconds: Long,
    requestedAt: Instant,
    paidAt: Option[Instant]
) {
  def isApplied: Boolean = paidAt.isDefined
}
