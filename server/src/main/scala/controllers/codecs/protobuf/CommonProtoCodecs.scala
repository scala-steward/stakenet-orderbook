package controllers.codecs.protobuf

import java.time.Instant
import java.util.UUID

import com.google.protobuf.ByteString
import io.stakenet.orderbook.config.TradingPairsConfig
import io.stakenet.orderbook.models.lnd.{PaymentRHash, RefundablePayment}
import io.stakenet.orderbook.models.trading._
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.protos
import io.stakenet.orderbook.protos.models.BigInclusiveInterval
import io.stakenet.orderbook.utils.Extensions.OptionExt

import scala.util.Try

trait CommonProtoCodecs {

  // TODO: This is a hack removing flexibility for the sake of shipping it fast, take this out.
  def tradingPairsConfig: TradingPairsConfig

  // Note: This class is only required to represent the protobuf model
  private[protobuf] case class OrderDetails(id: Option[OrderId], funds: Satoshis, price: Option[Satoshis])

  type SatoshisCodec = ProtoCodec[protos.models.BigInteger, Satoshis]
  type SatoshisInclusiveIntervalCodec = ProtoCodec[protos.models.BigInclusiveInterval, Satoshis.InclusiveInterval]
  type OrderDetailsCodec = ProtoCodec[protos.models.OrderDetails, OrderDetails]
  type OrderCodec = ProtoCodec[protos.models.Order, TradingOrder]
  type TradeCodec = ProtoCodec[protos.models.Trade, Trade]
  type BarsCodec = ProtoCodec[protos.models.BarPrices, Bars]
  type TradingPairCodec = ProtoCodec[protos.models.TradingPair, TradingPair]
  type RefundablePaymentCodec = ProtoCodec[protos.models.RefundablePayment, RefundablePayment]
  type OrderSummaryCodec = ProtoCodec[protos.models.OrderSummary, OrderSummary]
  type PaymentRHashCodec = ProtoCodec[ByteString, PaymentRHash]

  def decodeTradingPair(string: String): TradingPair = {
    TradingPair
      .withNameInsensitiveOption(string)
      .filter(tradingPairsConfig.enabled.contains)
      .getOrThrow("Missing or invalid trading pair")
  }

  def decodeCurrency(string: String): Currency = {
    Currency.withNameInsensitiveOption(string).getOrThrow("Missing or invalid currency")
  }

  implicit val satoshisCodec: SatoshisCodec = new SatoshisCodec {

    // NOTE: We decode the string directly to avoid allocating a huge BigInt unnecessarily
    override def decode(proto: protos.models.BigInteger): Satoshis = {
      Satoshis.from(proto.value, 8).getOrThrow("Invalid satoshis")
    }

    override def encode(model: Satoshis): protos.models.BigInteger = {
      protos.models.BigInteger(model.value(8).toString)
    }
  }

  implicit val paymentRHashCodec: PaymentRHashCodec = new PaymentRHashCodec {

    override def decode(bytes: ByteString): PaymentRHash = {
      PaymentRHash.untrusted(bytes.toByteArray).getOrThrow("Invalid Payment hash")
    }

    override def encode(model: PaymentRHash): ByteString = {
      ByteString.copyFrom(model.value.toArray)
    }
  }

  implicit def orderDetailsCodec(implicit satoshisCodec: SatoshisCodec): OrderDetailsCodec = {
    new OrderDetailsCodec {
      override def decode(proto: protos.models.OrderDetails): OrderDetails = {
        val fundsProto = proto.funds.getOrThrow("Missing funds")
        val price = proto.price.map(satoshisCodec.decode)
        val funds = satoshisCodec.decode(fundsProto)
        val id = OrderId.from(proto.orderId)
        OrderDetails(id = id, funds = funds, price = price)
      }

      override def encode(model: OrderDetails): protos.models.OrderDetails = {
        protos.models
          .OrderDetails(price = model.price.map(satoshisCodec.encode))
          .withOrderId(model.id.map(_.value.toString).getOrElse(""))
          .withFunds(satoshisCodec.encode(model.funds))
      }
    }
  }

  implicit val satoshisInclusiveIntervalCodec: SatoshisInclusiveIntervalCodec = new SatoshisInclusiveIntervalCodec {

    override def decode(proto: BigInclusiveInterval): Satoshis.InclusiveInterval = {
      val from = satoshisCodec.decode(proto.from.getOrThrow("Missing from"))
      val to = satoshisCodec.decode(proto.to.getOrThrow("Missing to"))
      Satoshis.InclusiveInterval(from = from, to = to)
    }

    override def encode(model: Satoshis.InclusiveInterval): BigInclusiveInterval = {
      protos.models
        .BigInclusiveInterval()
        .withFrom(satoshisCodec.encode(model.from))
        .withTo(satoshisCodec.encode(model.to))
    }
  }

  implicit def orderCodec(implicit
      satoshisCodec: SatoshisCodec,
      orderDetailsCodec: OrderDetailsCodec
  ): OrderCodec = new OrderCodec {

    override def decode(proto: protos.models.Order): TradingOrder = {
      val pair = decodeTradingPair(proto.tradingPair)
      val side = OrderSide.withNameInsensitiveOption(proto.side.toString).getOrThrow("Missing or invalid order side")
      val detailsProto = proto.details.getOrThrow("Missing order details")
      val details = orderDetailsCodec.decode(detailsProto)
      val id = details.id.getOrThrow("Missing or invalid order id")

      val order = proto.`type` match {
        case protos.models.Order.OrderType.limit =>
          val price = details.price.getOrThrow("Missing price")
          pair.Order.limit(side = side, id = id, funds = details.funds, price = price)

        case protos.models.Order.OrderType.market =>
          pair.Order.market(side = side, id = id, funds = details.funds)

        case protos.models.Order.OrderType.Unrecognized(_) => throw new RuntimeException("Unknown order type")
      }

      TradingOrder(order)
    }

    override def encode(model: TradingOrder): protos.models.Order = {
      val priceMaybe = model.asLimitOrder.map(x => satoshisCodec.encode(x.details.price))
      val details = protos.models
        .OrderDetails(price = priceMaybe)
        .withFunds(
          satoshisCodec
            .encode(model.value.funds)
        )
        .withOrderId(model.value.id.value.toString)

      val side = model.value.side match {
        case OrderSide.Buy => protos.models.Order.OrderSide.buy
        case OrderSide.Sell => protos.models.Order.OrderSide.sell
      }

      val tpe =
        model.fold(
          onLimitOrder = _ => protos.models.Order.OrderType.limit,
          onMarketOrder = _ => protos.models.Order.OrderType.market
        )

      protos.models
        .Order()
        .withType(tpe)
        .withDetails(details)
        .withSide(side)
        .withTradingPair(model.pair.toString)
    }
  }

  implicit def tradeCodec(implicit satoshisCodec: SatoshisCodec): TradeCodec = new TradeCodec {

    override def decode(proto: protos.models.Trade): Trade = {
      val id = Try(UUID.fromString(proto.id)).map(Trade.Id.apply).toOption.getOrThrow("Invalid or missing order id")
      val pair = decodeTradingPair(proto.tradingPair)
      val existingOrder = OrderId.from(proto.existingOrderId).getOrThrow("Invalid or missing existing order")
      val executingOrder = OrderId.from(proto.executingOrderId).getOrThrow("Invalid or missing executing")
      val priceProto = proto.price.getOrThrow("Invalid or missing price")
      val sizeProto = proto.size.getOrThrow("Invalid or missing size")
      val price = satoshisCodec.decode(priceProto)
      val size = satoshisCodec.decode(sizeProto)
      val executingOrderSide =
        OrderSide.withNameInsensitiveOption(proto.executingOrderSide).getOrThrow("Invalid or missing order side")
      val executedOn = Instant.ofEpochMilli(proto.executedOn)
      val existingOrderFunds = satoshisCodec.decode(proto.existingOrderFunds.getOrThrow("Invalid existing order funds"))

      Trade(
        id = id,
        pair = pair,
        price = price,
        size = size,
        existingOrder = existingOrder,
        executingOrder = executingOrder,
        executingOrderSide = executingOrderSide,
        executedOn = executedOn,
        existingOrderFunds = existingOrderFunds
      )
    }

    override def encode(model: Trade): protos.models.Trade = {
      protos.models
        .Trade()
        .withId(model.id.value.toString)
        .withTradingPair(model.pair.toString)
        .withPrice(satoshisCodec.encode(model.price))
        .withSize(satoshisCodec.encode(model.size))
        .withExistingOrderId(model.existingOrder.value.toString)
        .withExecutingOrderId(model.executingOrder.value.toString)
        .withExecutingOrderSide(model.executingOrderSide.toString)
        .withExecutedOn(model.executedOn.toEpochMilli)
        .withExistingOrderFunds(satoshisCodec.encode(model.existingOrderFunds))
    }
  }

  implicit def barsCodec(implicit satoshisCodec: SatoshisCodec): BarsCodec = new BarsCodec {

    override def decode(proto: protos.models.BarPrices): Bars = {
      val openProto = proto.open.getOrThrow("Missing or invalid open")
      val highProto = proto.high.getOrThrow("Missing or invalid high")
      val lowProto = proto.low.getOrThrow("Missing or invalid low")
      val closeProto = proto.close.getOrThrow("Missing or invalid close")
      val time = Instant.ofEpochMilli(proto.time)
      val open = satoshisCodec.decode(openProto)
      val high = satoshisCodec.decode(highProto)
      val low = satoshisCodec.decode(lowProto)
      val close = satoshisCodec.decode(closeProto)
      val volume = proto.volume
      Bars(
        time = time,
        low = low,
        high = high,
        open = open,
        close = close,
        volume = volume
      )
    }

    override def encode(model: Bars): protos.models.BarPrices = {
      protos.models
        .BarPrices()
        .withTime(model.time.toEpochMilli)
        .withOpen(satoshisCodec.encode(model.open))
        .withHigh(satoshisCodec.encode(model.high))
        .withLow(satoshisCodec.encode(model.low))
        .withClose(satoshisCodec.encode(model.close))
        .withVolume(model.volume)
    }
  }

  implicit val tradingPairCodec: TradingPairCodec = new TradingPairCodec {

    override def decode(proto: protos.models.TradingPair): TradingPair = {
      decodeTradingPair(proto.id)
    }

    override def encode(model: TradingPair): protos.models.TradingPair = {
      protos.models
        .TradingPair()
        .withId(model.toString)
        .withPrincipal(model.principal.toString)
        .withSecondary(model.secondary.toString)
        .withBuyFundsInterval(satoshisInclusiveIntervalCodec.encode(model.buyFundsInterval))
        .withBuyPriceInterval(satoshisInclusiveIntervalCodec.encode(model.buyPriceInterval))
        .withSellFundsInterval(satoshisInclusiveIntervalCodec.encode(model.sellFundsInterval))
        .withSellPriceInterval(satoshisInclusiveIntervalCodec.encode(model.sellPriceInterval))
        .withBuyFeePercent(model.buyFee.toString())
        .withSellFeePercent(model.sellFee.toString())
    }
  }

  implicit val refundablePaymentCodec: RefundablePaymentCodec = new RefundablePaymentCodec {

    override def decode(proto: protos.models.RefundablePayment): RefundablePayment = {
      val paymentHash = paymentRHashCodec.decode(proto.paymentHash)
      val paidAmount = satoshisCodec.decode(proto.paidAmount.getOrThrow("Invalid initial amount"))
      RefundablePayment(paymentHash, paidAmount)
    }

    override def encode(model: RefundablePayment): protos.models.RefundablePayment = {
      protos.models
        .RefundablePayment()
        .withPaidAmount(satoshisCodec.encode(model.paidAmount))
        .withPaymentHash(paymentRHashCodec.encode(model.paymentRHash))
    }
  }

  implicit val orderSummaryCodec: OrderSummaryCodec = new OrderSummaryCodec {

    override def decode(proto: protos.models.OrderSummary): OrderSummary = {
      val price = satoshisCodec.decode(proto.price.getOrThrow("Invalid price"))
      val amount = satoshisCodec.decode(proto.amount.getOrThrow("Invalid amount"))
      OrderSummary(price, amount)
    }

    override def encode(model: OrderSummary): protos.models.OrderSummary = {
      protos.models
        .OrderSummary()
        .withPrice(satoshisCodec.encode(model.price))
        .withAmount(satoshisCodec.encode(model.amount))
    }
  }
}
