package io.stakenet.orderbook.repositories.fees.requests

import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}

case class BurnFeeRequest(orderId: OrderId, currency: Currency, amount: Satoshis)
