package io.stakenet.orderbook.models.reports

import java.time.Instant

import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.{Currency, Satoshis}

case class OrderFeePayment(
    paymentRHash: PaymentRHash,
    currency: Currency,
    fundsAmount: Satoshis,
    feeAmount: Satoshis,
    feePercent: BigDecimal,
    createdAt: Instant
)
