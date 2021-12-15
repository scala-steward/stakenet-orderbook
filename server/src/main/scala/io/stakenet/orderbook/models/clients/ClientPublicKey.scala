package io.stakenet.orderbook.models.clients

import io.stakenet.orderbook.models.Currency

case class ClientPublicKey(
    clientPublicKeyId: ClientPublicKeyId,
    key: Identifier.LndPublicKey,
    currency: Currency,
    clientId: ClientId
)
