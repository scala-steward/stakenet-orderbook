package io.stakenet.orderbook.models.lnd

import io.stakenet.orderbook.models.Satoshis

case class RefundablePayment(paymentRHash: PaymentRHash, paidAmount: Satoshis)
