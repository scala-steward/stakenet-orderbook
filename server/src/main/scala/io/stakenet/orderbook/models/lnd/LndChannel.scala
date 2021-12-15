package io.stakenet.orderbook.models.lnd

import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.{ChannelId, Currency}

case class LndChannel(
    channelId: ChannelId.LndChannelId,
    currency: Currency,
    fundingTransaction: LndTxid,
    outputIndex: Int,
    lifeTimeSeconds: Long,
    channelStatus: ChannelStatus
) {

  def getPoint: LndOutpoint = LndOutpoint(fundingTransaction, outputIndex)
}
