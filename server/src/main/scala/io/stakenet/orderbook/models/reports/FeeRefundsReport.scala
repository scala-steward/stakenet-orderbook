package io.stakenet.orderbook.models.reports

import java.time.Instant

import io.stakenet.orderbook.models.lnd.FeeRefund
import io.stakenet.orderbook.models.{Currency, Satoshis}

case class FeeRefundsReport(
    refundId: FeeRefund.Id,
    currency: Currency,
    amount: Satoshis,
    refundedOn: Instant
)
