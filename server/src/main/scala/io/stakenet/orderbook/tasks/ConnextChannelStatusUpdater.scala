package io.stakenet.orderbook.tasks

import com.google.inject.Inject
import io.stakenet.orderbook.connext.ChannelDepositMonitor
import io.stakenet.orderbook.repositories.channels.ChannelsRepository

import scala.concurrent.ExecutionContext

class ConnextChannelStatusUpdater @Inject() (
    channelsRepository: ChannelsRepository.FutureImpl,
    channelDepositMonitor: ChannelDepositMonitor
)(implicit
    ec: ExecutionContext
) {

  start()

  def start(): Unit = {
    channelsRepository.findConnextConfirmingChannels().foreach { channels =>
      channels.foreach { channel =>
        channelDepositMonitor.monitor(channel.channelAddress, channel.transactionHash, channel.currency)
      }
    }
  }
}
