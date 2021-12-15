package io.stakenet.orderbook.models.lnd

import java.time.Instant

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class FeeInvoice(paymentHash: PaymentRHash, currency: Currency, amount: Satoshis, requestedAt: Instant)
