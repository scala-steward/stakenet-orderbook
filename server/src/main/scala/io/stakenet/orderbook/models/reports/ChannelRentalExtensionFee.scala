package io.stakenet.orderbook.models.reports

import java.time.Instant

import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.models.{Currency, Satoshis}

case class ChannelRentalExtensionFee(
    paymentHash: PaymentRHash,
    payingCurrency: Currency,
    rentedCurrency: Currency,
    amount: Satoshis,
    createdAt: Instant
)
