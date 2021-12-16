package controllers.codecs.protobuf

import com.google.protobuf.ByteString
import io.stakenet.orderbook.actors.peers
import io.stakenet.orderbook.models.OrderId
import io.stakenet.orderbook.utils.Extensions.OptionExt
import io.stakenet.orderbook.protos

trait ServerEventCodecs extends CommonProtoCodecs {

  type ServerEventCodec = ProtoCodec[protos.api.Event.ServerEvent, peers.protocol.Event.ServerEvent]

  type MyOrderMatchedCodec = ProtoCodec[protos.events.MyOrderMatched, peers.protocol.Event.ServerEvent.MyOrderMatched]

  type MatchedOrderCanceledCodec =
    ProtoCodec[protos.events.MyMatchedOrderCanceled, peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled]
  type OrderPlacedCodec = ProtoCodec[protos.events.OrderPlaced, peers.protocol.Event.ServerEvent.OrderPlaced]
  type OrderCanceledCodec = ProtoCodec[protos.events.OrderCanceled, peers.protocol.Event.ServerEvent.OrderCanceled]
  type OrdersMatchedCodec = ProtoCodec[protos.events.OrdersMatched, peers.protocol.Event.ServerEvent.OrdersMatched]

  type NewOrderMessageCodec =
    ProtoCodec[protos.events.NewOrderMessage, peers.protocol.Event.ServerEvent.NewOrderMessage]

  type SwapSuccessCodec = ProtoCodec[protos.events.SwapSuccess, peers.protocol.Event.ServerEvent.SwapSuccess]
  type SwapFailureCodec = ProtoCodec[protos.events.SwapFailure, peers.protocol.Event.ServerEvent.SwapFailure]

  type MaintenanceStartedCodec =
    ProtoCodec[protos.events.MaintenanceInProgress, peers.protocol.Event.ServerEvent.MaintenanceInProgress]

  type MaintenanceCompletedCodec =
    ProtoCodec[protos.events.MaintenanceCompleted, peers.protocol.Event.ServerEvent.MaintenanceCompleted]

  implicit val myOrderMatchedCodec: MyOrderMatchedCodec = new MyOrderMatchedCodec {

    override def decode(proto: protos.events.MyOrderMatched): peers.protocol.Event.ServerEvent.MyOrderMatched = {
      val tradeProto = proto.trade.getOrThrow("Missing or invalid trade")
      val trade = tradeCodec.decode(tradeProto)
      val orderMatchedWith = orderCodec.decode(proto.orderMatchedWith.getOrThrow("Missing or invalid order"))
      peers.protocol.Event.ServerEvent.MyOrderMatched(trade, orderMatchedWith)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.MyOrderMatched): protos.events.MyOrderMatched = {
      val trade = tradeCodec.encode(model.trade)
      val order = orderCodec.encode(model.orderMatchedWith)
      protos.events.MyOrderMatched().withTrade(trade).withOrderMatchedWith(order)
    }
  }

  implicit val matchedOrderCanceledCodec: MatchedOrderCanceledCodec = new MatchedOrderCanceledCodec {

    override def decode(
        proto: protos.events.MyMatchedOrderCanceled
    ): peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled = {
      val tradeProto = proto.trade.getOrThrow("Missing or invalid trade")
      val trade = tradeCodec.decode(tradeProto)

      peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled(trade)
    }

    override def encode(
        model: peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled
    ): protos.events.MyMatchedOrderCanceled = {
      val trade = tradeCodec.encode(model.trade)
      protos.events.MyMatchedOrderCanceled().withTrade(trade)
    }
  }

  implicit val orderPlacedCodec: OrderPlacedCodec = new OrderPlacedCodec {

    override def decode(proto: protos.events.OrderPlaced): peers.protocol.Event.ServerEvent.OrderPlaced = {
      val orderProto = proto.order.getOrThrow("Missing or invalid order")
      val order = orderCodec.decode(orderProto)
      peers.protocol.Event.ServerEvent.OrderPlaced(order)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.OrderPlaced): protos.events.OrderPlaced = {
      val order = orderCodec.encode(model.order)
      protos.events.OrderPlaced().withOrder(order).withTradingPair(model.order.pair.toString)
    }
  }

  implicit val orderCanceledCodec: OrderCanceledCodec = new OrderCanceledCodec {

    override def decode(proto: protos.events.OrderCanceled): peers.protocol.Event.ServerEvent.OrderCanceled = {
      val order = orderCodec.decode(proto.order.getOrThrow("Missing or invalid order"))
      peers.protocol.Event.ServerEvent.OrderCanceled(order)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.OrderCanceled): protos.events.OrderCanceled = {
      val order = orderCodec.encode(model.order)
      protos.events
        .OrderCanceled()
        .withOrderId(model.order.value.id.toString)
        .withTradingPair(model.order.pair.toString)
        .withOrder(order)
    }
  }

  implicit val ordersMatchedCodec: OrdersMatchedCodec = new OrdersMatchedCodec {

    override def decode(proto: protos.events.OrdersMatched): peers.protocol.Event.ServerEvent.OrdersMatched = {
      val tradeProto = proto.trade.getOrThrow("Missing or invalid trade")
      val trade = tradeCodec.decode(tradeProto)
      peers.protocol.Event.ServerEvent.OrdersMatched(trade)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.OrdersMatched): protos.events.OrdersMatched = {
      val trade = tradeCodec.encode(model.trade)
      protos.events.OrdersMatched().withTrade(trade).withTradingPair(model.trade.pair.toString)
    }
  }

  implicit val newOrderMessageCodec: NewOrderMessageCodec = new NewOrderMessageCodec {

    override def decode(proto: protos.events.NewOrderMessage): peers.protocol.Event.ServerEvent.NewOrderMessage = {
      val orderId = OrderId.from(proto.orderId).getOrThrow("Invalid or missing order id")
      val message = proto.message.toByteArray.toVector
      peers.protocol.Event.ServerEvent.NewOrderMessage(orderId, message)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.NewOrderMessage): protos.events.NewOrderMessage = {
      protos.events.NewOrderMessage(model.orderId.toString, ByteString.copyFrom(model.message.toArray))
    }
  }

  implicit val swapSuccessCodec: SwapSuccessCodec = new SwapSuccessCodec {

    override def decode(proto: protos.events.SwapSuccess): peers.protocol.Event.ServerEvent.SwapSuccess = {
      val tradeProto = proto.trade.getOrThrow("Missing or invalid trade")
      val trade = tradeCodec.decode(tradeProto)
      peers.protocol.Event.ServerEvent.SwapSuccess(trade)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.SwapSuccess): protos.events.SwapSuccess = {
      val trade = tradeCodec.encode(model.trade)
      protos.events.SwapSuccess().withTrade(trade).withTradingPair(model.trade.pair.toString)
    }
  }

  implicit val swapFailureCodec: SwapFailureCodec = new SwapFailureCodec {

    override def decode(proto: protos.events.SwapFailure): peers.protocol.Event.ServerEvent.SwapFailure = {
      val tradeProto = proto.trade.getOrThrow("Missing or invalid trade")
      val trade = tradeCodec.decode(tradeProto)
      peers.protocol.Event.ServerEvent.SwapFailure(trade)
    }

    override def encode(model: peers.protocol.Event.ServerEvent.SwapFailure): protos.events.SwapFailure = {
      val trade = tradeCodec.encode(model.trade)
      protos.events.SwapFailure().withTrade(trade).withTradingPair(model.trade.pair.toString)
    }
  }

  implicit val maintenanceStartedCodec: MaintenanceStartedCodec = new MaintenanceStartedCodec {

    override def decode(
        proto: protos.events.MaintenanceInProgress
    ): peers.protocol.Event.ServerEvent.MaintenanceInProgress = {
      peers.protocol.Event.ServerEvent.MaintenanceInProgress()
    }

    override def encode(
        model: peers.protocol.Event.ServerEvent.MaintenanceInProgress
    ): protos.events.MaintenanceInProgress = {
      protos.events.MaintenanceInProgress()
    }
  }

  implicit val maintenanceCompletedCodec: MaintenanceCompletedCodec = new MaintenanceCompletedCodec {

    override def decode(
        proto: protos.events.MaintenanceCompleted
    ): peers.protocol.Event.ServerEvent.MaintenanceCompleted = {
      peers.protocol.Event.ServerEvent.MaintenanceCompleted()
    }

    override def encode(
        model: peers.protocol.Event.ServerEvent.MaintenanceCompleted
    ): protos.events.MaintenanceCompleted = {
      protos.events.MaintenanceCompleted()
    }
  }

  implicit val serverEventCodec: ServerEventCodec = new ServerEventCodec {

    override def decode(proto: protos.api.Event.ServerEvent): peers.protocol.Event.ServerEvent = {
      val event = proto.value match {
        case protos.api.Event.ServerEvent.Value.Empty => throw new RuntimeException("Missing or invalid server event")
        case protos.api.Event.ServerEvent.Value.MyOrderMatched(value) => myOrderMatchedCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.MyMatchedOrderCanceled(value) => matchedOrderCanceledCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.OrderPlaced(value) => orderPlacedCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.OrderCanceled(value) => orderCanceledCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.OrdersMatched(value) => ordersMatchedCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.NewOrderMessage(value) => newOrderMessageCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.SwapSuccess(value) => swapSuccessCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.SwapFailure(value) => swapFailureCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.MaintenanceInProgress(value) => maintenanceStartedCodec.decode(value)
        case protos.api.Event.ServerEvent.Value.MaintenanceCompleted(value) => maintenanceCompletedCodec.decode(value)
      }
      event
    }

    override def encode(model: peers.protocol.Event.ServerEvent): protos.api.Event.ServerEvent = {
      val x = model match {
        case e: peers.protocol.Event.ServerEvent.OrderPlaced =>
          val event = orderPlacedCodec.encode(e)
          protos.api.Event.ServerEvent.Value.OrderPlaced(event)
        case e: peers.protocol.Event.ServerEvent.OrderCanceled =>
          val event = orderCanceledCodec.encode(e)
          protos.api.Event.ServerEvent.Value.OrderCanceled(event)
        case e: peers.protocol.Event.ServerEvent.OrdersMatched =>
          val event = ordersMatchedCodec.encode(e)
          protos.api.Event.ServerEvent.Value.OrdersMatched(event)
        case e: peers.protocol.Event.ServerEvent.MyMatchedOrderCanceled =>
          val event = matchedOrderCanceledCodec.encode(e)
          protos.api.Event.ServerEvent.Value.MyMatchedOrderCanceled(event)
        case e: peers.protocol.Event.ServerEvent.MyOrderMatched =>
          val event = myOrderMatchedCodec.encode(e)
          protos.api.Event.ServerEvent.Value.MyOrderMatched(event)
        case e: peers.protocol.Event.ServerEvent.NewOrderMessage =>
          val event = newOrderMessageCodec.encode(e)
          protos.api.Event.ServerEvent.Value.NewOrderMessage(event)
        case e: peers.protocol.Event.ServerEvent.SwapSuccess =>
          val event = swapSuccessCodec.encode(e)
          protos.api.Event.ServerEvent.Value.SwapSuccess(event)
        case e: peers.protocol.Event.ServerEvent.SwapFailure =>
          val event = swapFailureCodec.encode(e)
          protos.api.Event.ServerEvent.Value.SwapFailure(event)
        case e: peers.protocol.Event.ServerEvent.MaintenanceInProgress =>
          val event = maintenanceStartedCodec.encode(e)
          protos.api.Event.ServerEvent.Value.MaintenanceInProgress(event)
        case e: peers.protocol.Event.ServerEvent.MaintenanceCompleted =>
          val event = maintenanceCompletedCodec.encode(e)
          protos.api.Event.ServerEvent.Value.MaintenanceCompleted(event)
      }
      protos.api.Event.ServerEvent(x)
    }
  }
}
