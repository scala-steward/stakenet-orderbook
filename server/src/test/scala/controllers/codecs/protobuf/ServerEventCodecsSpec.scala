package controllers.codecs.protobuf

import io.stakenet.orderbook.actors.peers.protocol.Event
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.helpers.SampleOrders
import io.stakenet.orderbook.models.trading._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._
import org.scalatest.wordspec.AnyWordSpec

class ServerEventCodecsSpec extends AnyWordSpec with ServerEventCodecs {
  import SampleOrders._

  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  val pair = TradingPair.XSN_BTC
  val limitOrder = XSN_BTC_BUY_LIMIT_2
  val marketOrder = XSN_BTC_SELL_MARKET
  val trade = Trade.from(pair)(pair.use(marketOrder.value).value, pair.useLimitOrder(limitOrder.value).value)
  val message = "Hello".getBytes().toVector

  // TODO: Use scalacheck instead
  val tests = List(
    "ServerEvent.MyOrderMatched" -> Event.ServerEvent.MyOrderMatched(trade, limitOrder),
    "ServerEvent.MyOrderMatched2" -> Event.ServerEvent.MyOrderMatched(trade, marketOrder),
    "ServerEvent.MyMatchedOrderCanceled" -> Event.ServerEvent.MyMatchedOrderCanceled(trade),
    "ServerEvent.OrderPlaced" -> Event.ServerEvent.OrderPlaced(limitOrder),
    "ServerEvent.OrderCanceled" -> Event.ServerEvent.OrderCanceled(limitOrder),
    "ServerEvent.OrdersMatched" -> Event.ServerEvent.OrdersMatched(trade),
    "ServerEvent.NewOrderMessage" -> Event.ServerEvent.NewOrderMessage(limitOrder.value.id, message),
    "ServerEvent.SwapSuccess" -> Event.ServerEvent.SwapSuccess(trade),
    "ServerEvent.SwapFailure" -> Event.ServerEvent.SwapFailure(trade),
    "ServerEvent.MaintenanceStarted" -> Event.ServerEvent.MaintenanceInProgress(),
    "ServerEvent.MaintenanceCompleted" -> Event.ServerEvent.MaintenanceCompleted()
  )

  val codec = implicitly[ServerEventCodec]

  "EventCodec" should {
    tests.foreach {
      case (name, evt) =>
        s"encode and decode the same Server Event: $name" in {

          val model = evt
          val proto = codec.encode(model)
          val decoded = codec.decode(proto)
          decoded must be(model)
        }
    }
  }
}
