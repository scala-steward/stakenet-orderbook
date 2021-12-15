package io.stakenet.orderbook.models.connext

import io.stakenet.orderbook.models.ChannelIdentifier.ConnextChannelAddress
import io.stakenet.orderbook.models.Currency

case class ConnextChannel(channelAddress: ConnextChannelAddress, transactionHash: String, currency: Currency)
