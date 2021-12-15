package controllers.codecs.protobuf

import java.time.{Instant, LocalDate}

import helpers.Helpers
import io.stakenet.orderbook.actors.peers.protocol.{Event, TaggedCommandResponse}
import io.stakenet.orderbook.actors.peers.results.PlaceOrderResult
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.helpers.{SampleChannels, SampleOrders}
import io.stakenet.orderbook.models.lnd.{ChannelStatus, RefundablePayment}
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.models.{ChannelId, Currency}
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class CommandResponseCodecsSpec extends AnyWordSpec with CommandResponseCodecs {
  import SampleOrders._
  import TradingPair._

  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  val pair = TradingPair.XSN_BTC
  val limitOrder = XSN_BTC_BUY_LIMIT_2
  val marketOrder = XSN_BTC_SELL_MARKET
  val trade = Trade.from(pair)(pair.use(marketOrder.value).value, pair.useLimitOrder(limitOrder.value).value)
  val resolution = new Resolution(days = 1)

  val bars = List(
    Bars(Instant.now(), trade.price, trade.price, trade.price, trade.price, 10),
    Bars(Instant.now(), trade.price, trade.price, trade.price, trade.price, 7)
  )

  val summaryOrders = List(
    OrderSummary(trade.price, trade.size)
  )

  val tradePrices = TradingPairDailyPrices(
    XSN_BTC,
    List(
      DailyPrices(
        LocalDate.now(),
        getSatoshis(300),
        getSatoshis(800),
        getSatoshis(255),
        getSatoshis(400)
      )
    )
  )

  val channelFeePayment = SampleChannels.newChannelFeePayment()
  val channel = SampleChannels.newChannel()
  val connextChannel = SampleChannels.newConnextChannel()

  val validPayment = Helpers.randomPaymentHash()
  val nodePubKey = Helpers.randomPublicKey()
  val publicIdentifier = Helpers.randomPublicIdentifier()

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
    "GetOpenOrdersResponse" -> Event.CommandResponse.GetOpenOrdersResponse(TradingPair.XSN_BTC, List.empty, List.empty),
    "GetOpenOrdersResponse2" -> Event.CommandResponse
      .GetOpenOrdersResponse(TradingPair.XSN_BTC, summaryOrders, summaryOrders),
    "SubscribeResponse 1" -> Event.CommandResponse.SubscribeResponse(XSN_BTC, List.empty, List.empty),
    "SubscribeResponse 2" -> Event.CommandResponse.SubscribeResponse(LTC_BTC, summaryOrders, summaryOrders),
    "UnsubscribeResponse 1" -> Event.CommandResponse.UnsubscribeResponse(XSN_BTC),
    "UnsubscribeResponse 2" -> Event.CommandResponse.UnsubscribeResponse(LTC_BTC),
    "GetHistoricTradesResponse" -> Event.CommandResponse.GetHistoricTradesResponse(List(trade)),
    "GetBarsPricesResponse 1" -> Event.CommandResponse
      .GetBarsPricesResponse(List.empty),
    "GetBarsPricesResponse 2" -> Event.CommandResponse
      .GetBarsPricesResponse(bars),
    "GetOpenOrderByIdResponse found" -> Event.CommandResponse.GetOpenOrderByIdResponse(Some(limitOrder)),
    "GetOpenOrderByIdResponse none" -> Event.CommandResponse.GetOpenOrderByIdResponse(None),
    "GetTradingPairsResponse 1" -> Event.CommandResponse.GetTradingPairsResponse(List(LTC_BTC, XSN_BTC), true),
    "GetTradingPairsResponse 2" -> Event.CommandResponse.GetTradingPairsResponse(List(XSN_BTC), false),
    "GetTradingPairsResponse 3" -> Event.CommandResponse.GetTradingPairsResponse(List.empty, false),
    "PingResponse" -> Event.CommandResponse.PingResponse(),
    "SendOrderMessageResponse" -> Event.CommandResponse.SendOrderMessageResponse(),
    "CleanTradingPairOrdersResponse" -> Event.CommandResponse
      .CleanTradingPairOrdersResponse(limitOrder.pair, List.empty, List.empty),
    "GetInvoicePaymentResponse 1" -> Event.CommandResponse
      .GetInvoicePaymentResponse(Currency.XSN, noFeeRequired = true, None),
    "GetInvoicePaymentResponse 2" -> Event.CommandResponse
      .GetInvoicePaymentResponse(Currency.XSN, noFeeRequired = false, Some("paymentrequest")),
    "GenerateInvoiceToRentChannelResponse" -> Event.CommandResponse
      .GenerateInvoiceToRentChannelResponse(channelFeePayment, "paymentrequest"),
    "GeneratePaymentHashToRentChannelResponse" -> Event.CommandResponse
      .GeneratePaymentHashToRentChannelResponse(channelFeePayment, validPayment),
    "RentChannelResponse(lnd)" -> Event.CommandResponse
      .RentChannelResponse(validPayment, nodePubKey, channel.channelId, Helpers.randomOutpoint()),
    "RentChannelResponse(connext)" -> Event.CommandResponse
      .RentChannelResponse(validPayment, publicIdentifier, connextChannel.channelId, Helpers.randomChannelAddress()),
    "GetChannelStatusResponse - lnd 1" -> Event.CommandResponse
      .GetChannelStatusResponse(
        channel.channelId,
        Event.CommandResponse.ChannelStatus.Lnd(
          ChannelStatus.Opening,
          Some(Instant.ofEpochSecond(133254621)),
          Some("expired"),
          Some("REMOTE"),
          Some(Instant.ofEpochSecond(1587261375))
        )
      ),
    "GetChannelStatusResponse - lnd 2" -> Event.CommandResponse
      .GetChannelStatusResponse(
        channel.channelId,
        Event.CommandResponse.ChannelStatus.Lnd(
          ChannelStatus.Opening,
          None,
          None,
          None,
          None
        )
      ),
    "GetFeeToRentChannelResponse" -> Event.CommandResponse.GetFeeToRentChannelResponse(
      channelFeePayment.fees.totalFee,
      channelFeePayment.fees.rentingFee + channelFeePayment.fees.forceClosingFee,
      channelFeePayment.fees.transactionFee
    ),
    "RefundFee" -> Event.CommandResponse
      .RefundFeeResponse(
        Currency.XSN,
        getSatoshis(1),
        List(RefundablePayment(validPayment, getSatoshis(1))),
        Instant.ofEpochSecond(1587261375)
      ),
    "GetRefundableAmountResponse" -> Event.CommandResponse
      .GetRefundableAmountResponse(Currency.XSN, getSatoshis(1)),
    "GetFeeToExtendRentedChannelResponse" -> Event.CommandResponse.GetFeeToExtendRentedChannelResponse(getSatoshis(1)),
    "GenerateInvoiceToExtendRentedChannelResponse" -> Event.CommandResponse
      .GenerateInvoiceToExtendRentedChannelResponse(
        channel.channelId,
        channelFeePayment.payingCurrency,
        100L,
        "invoice"
      ),
    "GeneratePaymentHashToExtendRentedChannelResponse" -> Event.CommandResponse
      .GeneratePaymentHashToExtendConnextRentedChannelResponse(
        ChannelId.ConnextChannelId.random(),
        channelFeePayment.payingCurrency,
        100L,
        Helpers.randomPaymentHash()
      ),
    "ExtendRentedChannelTimeResponse" -> Event.CommandResponse
      .ExtendRentedChannelTimeResponse(validPayment, channel.channelId, 100L),
    "CommandFailed.Reason" -> Event.CommandResponse.CommandFailed("error"),
    "CommandFailed.ServerInMaintenance" -> Event.CommandResponse.CommandFailed.ServerInMaintenance(),
    "RegisterPublicKeyResponse" -> Event.CommandResponse.RegisterPublicKeyResponse(Currency.XSN, nodePubKey),
    "RegisterPublicIdentifierResponse" -> Event.CommandResponse
      .RegisterPublicIdentifierResponse(Currency.XSN, publicIdentifier),
    "GetConnextPaymentInformationResponse" -> Event.CommandResponse
      .GetConnextPaymentInformationResponse(
        Currency.XSN,
        false,
        "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj",
        Some(validPayment)
      ),
    "RegisterConnextChannelContractDeploymentFeeResponse" -> Event.CommandResponse
      .RegisterConnextChannelContractDeploymentFeeResponse("hash"),
    "GetConnextChannelContractDeploymentFeeResponse" -> Event.CommandResponse
      .GetConnextChannelContractDeploymentFeeResponse("address", getSatoshis(123))
  )

  val codec = implicitly[CommandResponseCodec]

  "EventCodec" should {
    tests.foreach {
      case (name, evt) =>
        s"encode and decode the same CommandResponse: $name" in {

          val model = TaggedCommandResponse("response-Id", evt)

          val proto = codec.encode(model)
          val decoded = codec.decode(proto)
          decoded must be(model)
        }
    }
  }
}
