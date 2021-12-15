package io.stakenet.orderbook.models.connext

import io.stakenet.orderbook.models.ChannelIdentifier
import io.stakenet.orderbook.models.clients.Identifier

case class Channel(
    channelAddress: ChannelIdentifier.ConnextChannelAddress,
    counterPartyIdentifier: Identifier.ConnextPublicIdentifier
)
