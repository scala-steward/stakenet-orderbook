package controllers.codecs.protobuf

import java.time.Instant

import io.stakenet.orderbook.actors.peers.protocol.Event
import io.stakenet.orderbook.actors.peers.results.PlaceOrderResult
import io.stakenet.orderbook.actors.peers.ws.WebSocketOutgoingMessage
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.helpers.SampleOrders
import io.stakenet.orderbook.models.trading._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._
import org.scalatest.wordspec.AnyWordSpec

class PeerEventCodecsSpec extends AnyWordSpec with PeerEventCodecs {

  import SampleOrders._
  import TradingPair._

  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  val pair = TradingPair.XSN_BTC
  val limitOrder = XSN_BTC_BUY_LIMIT_2
  val marketOrder = XSN_BTC_SELL_MARKET
  val trade = Trade.from(pair)(pair.use(marketOrder.value).value, pair.useLimitOrder(limitOrder.value).value)
  val reason = "There aren't orders to fulfill your market order, try later"
  val message = "Hello".getBytes().toVector

  val bars = List(
    Bars(Instant.now(), trade.price, trade.price, trade.price, trade.price, 10),
    Bars(Instant.now(), trade.price, trade.price, trade.price, trade.price, 7)
  )

  // TODO: Use scalacheck instead
  val tests = List(
    "PlaceOrderResponse with market order placed" -> Event.CommandResponse
      .PlaceOrderResponse(PlaceOrderResult.OrderPlaced(marketOrder)),
    "PlaceOrderResponse with limit order placed" -> Event.CommandResponse
      .PlaceOrderResponse(PlaceOrderResult.OrderPlaced(limitOrder)),
    "PlaceOrderResponse with order matched" -> Event.CommandResponse
      .PlaceOrderResponse(PlaceOrderResult.OrderMatched(trade, limitOrder)),
    "PlaceOrderResponse with order rejected" -> Event.CommandResponse
      .PlaceOrderResponse(PlaceOrderResult.OrderRejected("whoops")),
    "CancelOrderResponse canceled" -> Event.CommandResponse.CancelOpenOrderResponse(Some(limitOrder.value)),
    "CancelOrderResponse not found" -> Event.CommandResponse.CancelOpenOrderResponse(None),
    "GetOpenOrdersResponse" -> Event.CommandResponse.GetOpenOrdersResponse(XSN_BTC, List.empty, List.empty),
    "SubscribeResponse 1" -> Event.CommandResponse.SubscribeResponse(XSN_BTC, List.empty, List.empty),
    "SubscribeResponse 2" -> Event.CommandResponse.SubscribeResponse(LTC_BTC, List.empty, List.empty),
    "UnsubscribeResponse 1" -> Event.CommandResponse.UnsubscribeResponse(XSN_BTC),
    "UnsubscribeResponse 2" -> Event.CommandResponse.UnsubscribeResponse(LTC_BTC),
    "GetHistoricTradesResponse" -> Event.CommandResponse.GetHistoricTradesResponse(List(trade)),
    "GetHistoricTradesResponse not found" -> Event.CommandResponse.GetHistoricTradesResponse(List.empty),
    "GetBarsPricesResponse 1" -> Event.CommandResponse.GetBarsPricesResponse(List.empty),
    "GetBarsPricesResponse 2" -> Event.CommandResponse.GetBarsPricesResponse(bars),
    "GetOpenOrderByIdResponse found" -> Event.CommandResponse.GetOpenOrderByIdResponse(Some(limitOrder)),
    "GetOpenOrderByIdResponse none" -> Event.CommandResponse.GetOpenOrderByIdResponse(None),
    "GetTradingPairsResponse 1" -> Event.CommandResponse.GetTradingPairsResponse(List(LTC_BTC, XSN_BTC), true),
    "GetTradingPairsResponse 2" -> Event.CommandResponse.GetTradingPairsResponse(List(XSN_BTC), false),
    "GetTradingPairsResponse 3" -> Event.CommandResponse.GetTradingPairsResponse(List.empty, false),
    "PingResponse" -> Event.CommandResponse.PingResponse(),
    "SendOrderMessageResponse" -> Event.CommandResponse.SendOrderMessageResponse(),
    "CleanTradingPairOrdersResponse" -> Event.CommandResponse
      .CleanTradingPairOrdersResponse(limitOrder.pair, List(limitOrder.value.id), List(limitOrder.value.id)),
    "CommandFailed" -> Event.CommandResponse.CommandFailed("Command failed"),
    "CancelMatchedOrderResponse canceled" -> Event.CommandResponse.CancelMatchedOrderResponse(Some(trade)),
    "CancelMatchedOrderResponse not found" -> Event.CommandResponse.CancelMatchedOrderResponse(None),
    "ServerEvent.OrderPlaced" -> Event.ServerEvent.OrderPlaced(limitOrder),
    "ServerEvent.OrderCanceled" -> Event.ServerEvent.OrderCanceled(limitOrder),
    "ServerEvent.OrdersMatched" -> Event.ServerEvent.OrdersMatched(trade),
    "ServerEvent.NewOrderMessage" -> Event.ServerEvent.NewOrderMessage(limitOrder.value.id, message),
    "ServerEvent.MyMatchedOrderCanceled" -> Event.ServerEvent.MyMatchedOrderCanceled(trade),
    "ServerEvent.MyOrderMatched" -> Event.ServerEvent.MyOrderMatched(trade, marketOrder)
  )

  val codec = implicitly[EventCodec]

  "EventCodec" should {
    tests.foreach { case (name, evt) =>
      s"encode and decode the same event: $name" in {
        val model = evt match {
          case _: Event.ServerEvent => WebSocketOutgoingMessage(10, None, evt)
          case _ => WebSocketOutgoingMessage(10, Some("id"), evt)
        }
        val proto = codec.encode(model)
        val decoded = codec.decode(proto)
        decoded must be(model)
      }
    }
  }
}
