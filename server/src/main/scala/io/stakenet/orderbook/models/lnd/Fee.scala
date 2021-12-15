package io.stakenet.orderbook.models.lnd

import java.time.Instant

import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}

case class Fee(
    currency: Currency,
    paymentRHash: PaymentRHash,
    paidAmount: Satoshis,
    lockedForOrderId: Option[OrderId],
    paidAt: Instant,
    feePercent: BigDecimal
) {

  val refundableFeeAmount: Satoshis = {
    val amount = (paidAmount.toBigDecimal * feePercent).setScale(8, BigDecimal.RoundingMode.DOWN)

    Satoshis
      .from(amount)
      .getOrElse(
        throw new RuntimeException("Impossible, the value should be satoshis")
      )
  }
}
