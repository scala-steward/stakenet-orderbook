package io.stakenet.orderbook.actors.peers

import io.stakenet.orderbook.actors.peers.protocol.Command.GetChannelStatus
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.GetChannelStatusResponse
import io.stakenet.orderbook.actors.peers.ws.{WebSocketIncomingMessage, WebSocketOutgoingMessage}
import io.stakenet.orderbook.actors.PeerSpecBase
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse
import io.stakenet.orderbook.helpers.SampleChannels
import io.stakenet.orderbook.models.ChannelId
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock

class GetChannelStatusSpec extends PeerSpecBase("GetChannelStatusSpec") {
  "GetChannelStatus" should {
    "respond with GetChannelStatusResponse for lnd channel" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channel = SampleChannels.newChannel()
        val expected = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          GetChannelStatusResponse(
            channel.channelId,
            CommandResponse.ChannelStatus.Lnd(
              channel.status,
              channel.expiresAt,
              channel.closingType,
              channel.closedBy,
              channel.closedOn
            )
          )
        )

        when(channelsRepository.findChannel(channel.channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(requestId, GetChannelStatus(channel.channelId.value))
        alice.client.expectMsg(expected)
      }
    }

    "respond with GetChannelStatusResponse for connext channel" in {
      val channelsRepository = mock[ChannelsRepository.Blocking]

      withSinglePeer(channelsRepository = channelsRepository) { alice =>
        val requestId = "id"
        val channel = SampleChannels.newConnextChannel()
        val expected = WebSocketOutgoingMessage(
          1,
          Some(requestId),
          GetChannelStatusResponse(
            channel.channelId,
            CommandResponse.ChannelStatus.Connext(
              channel.status,
              channel.expiresAt
            )
          )
        )

        when(channelsRepository.findChannel(ChannelId.LndChannelId(channel.channelId.value))).thenReturn(None)
        when(channelsRepository.findConnextChannel(channel.channelId)).thenReturn(Some(channel))

        alice.actor ! WebSocketIncomingMessage(requestId, GetChannelStatus(channel.channelId.value))
        alice.client.expectMsg(expected)
      }
    }
  }
}
