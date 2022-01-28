package io.stakenet.orderbook.tasks

import akka.actor.ActorSystem
import com.google.inject.Inject
import io.stakenet.orderbook.connext.ChannelDepositMonitor
import io.stakenet.orderbook.repositories.channels.ChannelsRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ConnextChannelStatusUpdater @Inject() (
    channelsRepository: ChannelsRepository.FutureImpl,
    channelDepositMonitor: ChannelDepositMonitor
)(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem
) {

  actorSystem.scheduler.scheduleAtFixedRate(15.seconds, 15.minutes) { () =>
    start()
  }

  def start(): Unit = {
    channelsRepository.findConnextConfirmingChannels().foreach { channels =>
      channels.foreach { channel =>
        channelDepositMonitor.monitor(channel.channelAddress, channel.transactionHash, channel.currency)
      }
    }
  }
}
