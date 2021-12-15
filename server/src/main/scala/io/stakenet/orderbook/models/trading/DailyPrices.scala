package io.stakenet.orderbook.models.trading

import java.time.LocalDate

import io.stakenet.orderbook.models.Satoshis

case class DailyPrices(
    date: LocalDate,
    open: Satoshis,
    high: Satoshis,
    low: Satoshis,
    close: Satoshis
) {}
