package io.stakenet.orderbook.models.lnd

import java.time.Instant

import io.stakenet.orderbook.models.Satoshis

case class PaymentData(amount: Satoshis, settledAt: Instant)
