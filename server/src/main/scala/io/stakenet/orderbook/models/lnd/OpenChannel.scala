package io.stakenet.orderbook.models.lnd

import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.clients.Identifier

case class OpenChannel(outPoint: LndOutpoint, active: Boolean, publicKey: Identifier.LndPublicKey)
