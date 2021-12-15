package io.stakenet.orderbook.models

import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.Trade

case class MakerPayment(
    id: MakerPaymentId,
    tradeId: Trade.Id,
    clientId: ClientId,
    amount: Satoshis,
    currency: Currency,
    status: MakerPaymentStatus
)
