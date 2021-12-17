package controllers.codecs.protobuf

import java.time.Instant
import java.util.UUID

import helpers.Helpers
import io.stakenet.orderbook.actors.peers.protocol._
import io.stakenet.orderbook.actors.peers.ws.WebSocketIncomingMessage
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.helpers.{CustomMatchers, SampleChannels, SampleOrders}
import io.stakenet.orderbook.models.lnd.{PaymentRHash, RefundablePayment}
import io.stakenet.orderbook.models.trading.{Resolution, Trade, TradingPair}
import io.stakenet.orderbook.models.{ChannelId, Currency, OrderMessage, Satoshis}
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class PeerCommandCodecsSpec extends AnyWordSpec with PeerCommandCodecs {

  import SampleOrders._
  import TradingPair._

  override val tradingPairsConfig: TradingPairsConfig = TradingPairsConfig(TradingPair.values.toSet)

  val pair = TradingPair.LTC_BTC
  val limitOrder = XSN_BTC_BUY_LIMIT_2
  val marketOrder = XSN_BTC_SELL_MARKET
  val reason = "There aren't orders to fulfill your market order, try later"
  val message = "Hello".getBytes().toVector
  val from = Instant.ofEpochSecond(1234567890)
  val to = Instant.ofEpochSecond(1234587890)
  val channelFeePayment = SampleChannels.newChannelFeePayment()
  val channel = SampleChannels.newChannel()
  val rhash = Helpers.randomPaymentHash()
  val nodePubKey = Helpers.randomPublicKey()
  val refundablePayment = RefundablePayment(rhash, Helpers.asSatoshis("1"))
  val publicIdentifier = "vector7mAydt3S3dDPWJMYSHZPdRo16Pru145qTNQYFoS8TrpXWW8HAj"
  // TODO: Use scalacheck instead

  val tests = List(
    "Ping" -> Command.Ping(),
    "Cancel Order" -> Command.CancelOpenOrder(limitOrder.value.id),
    "Get open orders for LTC_BTC" -> Command.GetOpenOrders(LTC_BTC),
    "Get open orders for XSN_BTC" -> Command.GetOpenOrders(XSN_BTC),
    "Subscribe to LTC_BTC" -> Command.Subscribe(LTC_BTC, retrieveOrdersSummary = true),
    "Unsubscribe to XSN_BTC" -> Command.Unsubscribe(XSN_BTC),
    "Send order message" -> Command.SendOrderMessage(OrderMessage(message, limitOrder.value.id)),
    "Get historic trades" -> Command.GetHistoricTrades(100, None, XSN_BTC),
    "Get historic trades paginated" -> Command.GetHistoricTrades(1, Some(Trade.Id(UUID.randomUUID())), XSN_BTC),
    "Get bars prices" -> Command.GetBarsPrices(XSN_BTC, new Resolution(days = 1), from, to, 10),
    "Get open order by id" -> Command.GetOpenOrderById(limitOrder.value.id),
    "Get trading pairs" -> Command.GetTradingPairs(),
    "Clean Orders for a trading pair" -> Command.CleanTradingPairOrders(limitOrder.pair),
    "Get a new payment request" -> Command.GetInvoicePayment(Currency.BTC, Satoshis.Zero),
    "Get a payment request for new channel" -> Command.GenerateInvoiceToRentChannel(channelFeePayment),
    "Get a payment hash for a connext rental" -> Command.GeneratePaymentHashToRentChannel(channelFeePayment),
    "Create a new rented channel" -> Command.RentChannel(rhash, Currency.XSN),
    "Get lnd channel status " -> Command.GetChannelStatus(SampleChannels.newChannel().channelId.value),
    "Get connext channel status " -> Command.GetChannelStatus(SampleChannels.newConnextChannel().channelId.value),
    "Get the fee for a channel renting " -> Command.GetFeeToRentChannel(SampleChannels.newChannelFeePayment()),
    "Refund fee" -> Command.RefundFee(Currency.XSN, List(RefundablePayment(rhash, Satoshis.Zero))),
    "Get the refundable amount" -> Command.GetRefundableAmount(Currency.BTC, List(refundablePayment)),
    "Get the refundable amount 2" -> Command.GetRefundableAmount(Currency.BTC, List.empty),
    "Get the fee to extend rented channel" -> Command
      .GetFeeToExtendRentedChannel(channel.channelId.value, Currency.XSN, 123),
    "Generate invoice to extend rented channel" -> Command
      .GenerateInvoiceToExtendRentedChannel(channel.channelId, Currency.XSN, 123),
    "Generate payment hash to extend rented channel" -> Command
      .GeneratePaymentHashToExtendConnextRentedChannel(ChannelId.ConnextChannelId.random(), Currency.USDC, 123),
    "Extend rented channel time" -> Command.ExtendRentedChannelTime(rhash, Currency.XSN),
    "Register Public Key" -> Command.RegisterPublicKey(Currency.XSN, nodePubKey),
    "Get Connext Payment Information" -> Command.GetConnextPaymentInformation(Currency.XSN),
    "RegisterConnextChannelContractDeploymentFee" -> Command.RegisterConnextChannelContractDeploymentFee("hash"),
    "GetConnextChannelContractDeploymentFee" -> Command.GetConnextChannelContractDeploymentFee()
  )

  val testPlaceOrder = List(
    "Limit Order" -> Command.PlaceOrder(limitOrder, PaymentRHash.untrusted("test")),
    "Limit Order with rhash" -> Command.PlaceOrder(limitOrder, Some(rhash)),
    "Market Order" -> Command.PlaceOrder(marketOrder, PaymentRHash.untrusted("test")),
    "Market Order with rhash" -> Command.PlaceOrder(marketOrder, Some(rhash))
  )

  private val codec: CommandCodec = implicitly[CommandCodec]

  "CommandCodec" should {
    tests.foreach { case (name, cmd) =>
      s"encode and decode the same command: $name" in {
        val model = WebSocketIncomingMessage("requestId", cmd)
        val proto = codec.encode(model)
        val decoded = codec.decode(proto)
        decoded must be(model)
      }
    }

    testPlaceOrder.foreach { case (name, cmd) =>
      s"encode and decode the placeOrder command: $name ignoring the order id" in {
        val model = WebSocketIncomingMessage("requestId", cmd)
        val proto = codec.encode(model)
        val decoded = codec.decode(proto)
        decoded.clientMessageId mustBe (model.clientMessageId)
        (decoded.command, model.command) match {
          case (decodedCommand: Command.PlaceOrder, modelCommand: Command.PlaceOrder) =>
            decodedCommand.paymentHash mustBe modelCommand.paymentHash
            decodedCommand.order.pair mustBe modelCommand.order.pair
            CustomMatchers.matchOrderIgnoreId(decodedCommand.order, modelCommand.order)

          case _ => fail("only PlaceOrder command is allowed")
        }
      }
    }
  }
}
