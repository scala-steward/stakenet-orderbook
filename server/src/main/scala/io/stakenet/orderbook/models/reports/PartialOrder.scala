package io.stakenet.orderbook.models.reports

import java.time.Instant

import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.models.lnd.PaymentRHash

case class PartialOrder(
    orderId: OrderId,
    ownerId: ClientId,
    paymentHash: Option[PaymentRHash],
    currency: Currency,
    tradedAmount: Satoshis,
    createdAt: Instant
)
