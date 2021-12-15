package io.stakenet.orderbook.repositories.fees.requests

import java.time.Instant

import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}

case class LinkFeeToOrderRequest(
    hash: PaymentRHash,
    currency: Currency,
    amount: Satoshis,
    orderId: OrderId,
    paidAt: Instant,
    feePercent: BigDecimal
)
