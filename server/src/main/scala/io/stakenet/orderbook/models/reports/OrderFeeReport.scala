package io.stakenet.orderbook.models.reports

import java.time.Instant

import io.stakenet.orderbook.models.{Currency, Satoshis}

case class OrderFeeReport(currency: Currency, from: Instant, to: Instant, fee: Satoshis, refundedAmount: Satoshis)
