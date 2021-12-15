package io.stakenet.orderbook.models.clients

import io.stakenet.orderbook.models.Currency

case class ClientPublicIdentifier(
    clientPublicIdentifierId: ClientPublicIdentifierId,
    identifier: Identifier.ConnextPublicIdentifier,
    currency: Currency,
    clientId: ClientId
)
