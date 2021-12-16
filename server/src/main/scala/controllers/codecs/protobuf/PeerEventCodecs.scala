package controllers.codecs.protobuf

import io.stakenet.orderbook.actors.peers
import io.stakenet.orderbook.actors.peers.protocol.TaggedCommandResponse
import io.stakenet.orderbook.protos

trait PeerEventCodecs extends CommandResponseCodecs with ServerEventCodecs {

  type EventCodec = ProtoCodec[protos.api.Event, peers.ws.WebSocketOutgoingMessage]

  implicit val eventCodec: EventCodec = new EventCodec {

    override def decode(proto: protos.api.Event): peers.ws.WebSocketOutgoingMessage = {
      val (requestId, e) = proto.value match {
        case protos.api.Event.Value.Empty => throw new RuntimeException("Missing or invalid event")
        case protos.api.Event.Value.Response(value) =>
          val x = commandResponseCodec.decode(value)
          (Some(x.requestId), x.value)
        case protos.api.Event.Value.Event(value) =>
          val x = serverEventCodec.decode(value)
          (None, x)
      }

      peers.ws.WebSocketOutgoingMessage(proto.messageCounter, requestId, e)
    }

    override def encode(model: peers.ws.WebSocketOutgoingMessage): protos.api.Event = {
      val event: protos.api.Event.Value = (model.clientMessageId, model.event) match {
        case (Some(requestId), x: peers.protocol.Event.CommandResponse) =>
          val tagged = TaggedCommandResponse(requestId, x)
          val y = commandResponseCodec.encode(tagged)
          protos.api.Event.Value.Response(y)

        case (None, x: peers.protocol.Event.ServerEvent) =>
          val y = serverEventCodec.encode(x)
          protos.api.Event.Value.Event(y)

        case _ => throw new RuntimeException("Invalid model")
      }

      protos.api.Event(messageCounter = model.messageCounter, event)
    }
  }
}
