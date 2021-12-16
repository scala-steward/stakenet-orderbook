package controllers.codecs.protobuf

import java.time.Instant
import java.util.UUID

import com.google.protobuf.ByteString
import io.stakenet.orderbook.actors.peers
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.lnd.{ChannelFeePayment, PaymentRHash, RefundablePayment}
import io.stakenet.orderbook.models.trading.{Resolution, Trade}
import io.stakenet.orderbook.models.{ChannelId, OrderId, OrderMessage, Satoshis}
import io.stakenet.orderbook.protos
import io.stakenet.orderbook.utils.Extensions.OptionExt

import scala.util.Try

trait PeerCommandCodecs extends CommonProtoCodecs {

  type CommandCodec = ProtoCodec[protos.api.Command, peers.ws.WebSocketIncomingMessage]
  type PingCommandCodec = ProtoCodec[protos.commands.PingCommand, peers.protocol.Command.Ping]
  type PlaceOrderCommandCodec = ProtoCodec[protos.commands.PlaceOrderCommand, peers.protocol.Command.PlaceOrder]

  type CancelOrderCommandCodec =
    ProtoCodec[protos.commands.CancelOpenOrderCommand, peers.protocol.Command.CancelOpenOrder]

  type GetTradingOrdersCommandCodec =
    ProtoCodec[protos.commands.GetOpenOrdersCommand, peers.protocol.Command.GetOpenOrders]
  type SubscribeCommandCodec = ProtoCodec[protos.commands.SubscribeCommand, peers.protocol.Command.Subscribe]
  type UnsubscribeCommandCodec = ProtoCodec[protos.commands.UnsubscribeCommand, peers.protocol.Command.Unsubscribe]

  type SendOrderMessageCommandCodec =
    ProtoCodec[protos.commands.SendOrderMessageCommand, peers.protocol.Command.SendOrderMessage]

  type CancelMatchedOrderCommandCodec =
    ProtoCodec[protos.commands.CancelMatchedOrderCommand, peers.protocol.Command.CancelMatchedOrder]

  type GetHistoricTradesCommandCodec =
    ProtoCodec[protos.commands.GetHistoricTradesCommand, peers.protocol.Command.GetHistoricTrades]

  type GetBarsPricesCommandCodec =
    ProtoCodec[protos.commands.GetBarsPricesCommand, peers.protocol.Command.GetBarsPrices]

  type GetTradingOrderByIdCommandCodec =
    ProtoCodec[protos.commands.GetOpenOrderByIdCommand, peers.protocol.Command.GetOpenOrderById]

  type GetTradingPairsCommandCodec =
    ProtoCodec[protos.commands.GetTradingPairsCommand, peers.protocol.Command.GetTradingPairs]

  type CleanTradingPairOrdersCommandCodec =
    ProtoCodec[protos.commands.CleanTradingPairOrdersCommand, peers.protocol.Command.CleanTradingPairOrders]

  type GetLndPaymentInvoiceCommandCodec =
    ProtoCodec[protos.commands.GetLndPaymentInvoiceCommand, peers.protocol.Command.GetInvoicePayment]

  type GenerateInvoiceToRentChannelCommandCodec =
    ProtoCodec[protos.commands.GenerateInvoiceToRentChannelCommand, peers.protocol.Command.GenerateInvoiceToRentChannel]

  type RentChannelCommandCodec =
    ProtoCodec[protos.commands.RentChannelCommand, peers.protocol.Command.RentChannel]

  type GetChannelStatusCommandCodec =
    ProtoCodec[protos.commands.GetChannelStatusCommand, peers.protocol.Command.GetChannelStatus]

  type GetFeeToRentChannelCommandCodec =
    ProtoCodec[protos.commands.GetFeeToRentChannelCommand, peers.protocol.Command.GetFeeToRentChannel]

  type RefundFeeCommandCodec =
    ProtoCodec[protos.commands.RefundFeeCommand, peers.protocol.Command.RefundFee]

  type GetRefundableAmountCommandCodec =
    ProtoCodec[protos.commands.GetRefundableAmountCommand, peers.protocol.Command.GetRefundableAmount]

  type GetFeeToExtendRentedChannelCommandCodec =
    ProtoCodec[protos.commands.GetFeeToExtendRentedChannelCommand, peers.protocol.Command.GetFeeToExtendRentedChannel]

  type GenerateInvoiceToExtendRentedChannelCommandCodec =
    ProtoCodec[
      protos.commands.GenerateInvoiceToExtendRentedChannelCommand,
      peers.protocol.Command.GenerateInvoiceToExtendRentedChannel
    ]

  type GeneratePaymentHashToExtendConnextRentedChannelCommandCodec = ProtoCodec[
    protos.commands.GeneratePaymentHashToExtendConnextRentedChannelCommand,
    peers.protocol.Command.GeneratePaymentHashToExtendConnextRentedChannel
  ]

  type ExtendRentedChannelTimeCommandCodec =
    ProtoCodec[protos.commands.ExtendRentedChannelTimeCommand, peers.protocol.Command.ExtendRentedChannelTime]

  type RegisterPublicKeyCommandCodec =
    ProtoCodec[protos.commands.RegisterPublicKeyCommand, peers.protocol.Command.RegisterPublicKey]

  type RegisterPublicIdentifierCommandCodec =
    ProtoCodec[protos.commands.RegisterPublicIdentifierCommand, peers.protocol.Command.RegisterPublicIdentifier]

  type GetConnextPaymentInformationCommandCodec =
    ProtoCodec[protos.commands.GetConnextPaymentInformationCommand, peers.protocol.Command.GetConnextPaymentInformation]

  type GeneratePaymentHashToRentChannelCommandCodec = ProtoCodec[
    protos.commands.GeneratePaymentHashToRentChannelCommand,
    peers.protocol.Command.GeneratePaymentHashToRentChannel
  ]

  type RegisterConnextChannelContractDeploymentFeeCommandCodec = ProtoCodec[
    protos.commands.RegisterConnextChannelContractDeploymentFeeCommand,
    peers.protocol.Command.RegisterConnextChannelContractDeploymentFee
  ]

  type GetConnextChannelContractDeploymentFeeCommandCodec = ProtoCodec[
    protos.commands.GetConnextChannelContractDeploymentFeeCommand,
    peers.protocol.Command.GetConnextChannelContractDeploymentFee
  ]

  implicit lazy val pingCommandCodec: PingCommandCodec = {
    new PingCommandCodec {
      override def decode(proto: protos.commands.PingCommand): peers.protocol.Command.Ping = {
        peers.protocol.Command.Ping()
      }

      override def encode(model: peers.protocol.Command.Ping): protos.commands.PingCommand = {
        protos.commands.PingCommand()
      }
    }
  }

  implicit lazy val placeOrderCommandCodec: PlaceOrderCommandCodec = {
    new PlaceOrderCommandCodec {
      override def decode(proto: protos.commands.PlaceOrderCommand): peers.protocol.Command.PlaceOrder = {
        val orderProto = proto.order.getOrThrow("Missing or invalid order")
        val details = orderProto.details.getOrThrow("Missing or invalid details")
        val id = OrderId.random()
        val orderWithId = orderProto.withDetails(
          details.copy(orderId = id.value.toString)
        )
        val order = orderCodec.decode(orderWithId)
        val paymentHash = Option(proto.paymentHash).filterNot(_.isEmpty).map(paymentRHashCodec.decode)

        peers.protocol.Command.PlaceOrder(order, paymentHash)
      }

      override def encode(model: peers.protocol.Command.PlaceOrder): protos.commands.PlaceOrderCommand = {
        val orderProto = orderCodec.encode(model.order)
        protos.commands
          .PlaceOrderCommand()
          .withOrder(orderProto)
          .withPaymentHash(ByteString.copyFrom(model.paymentHash.value.map(_.value.toArray).getOrElse(Array.empty)))
      }
    }
  }

  implicit lazy val cancelOrderCommandCodec: CancelOrderCommandCodec = new CancelOrderCommandCodec {

    override def decode(proto: protos.commands.CancelOpenOrderCommand): peers.protocol.Command.CancelOpenOrder = {
      val orderId = OrderId.from(proto.orderId).getOrThrow("Missing or invalid order id")
      peers.protocol.Command.CancelOpenOrder(orderId)
    }

    override def encode(model: peers.protocol.Command.CancelOpenOrder): protos.commands.CancelOpenOrderCommand = {
      protos.commands.CancelOpenOrderCommand(model.id.value.toString)
    }
  }

  implicit lazy val getTradingOrdersCommandCodec: GetTradingOrdersCommandCodec = {
    new GetTradingOrdersCommandCodec {
      override def decode(proto: protos.commands.GetOpenOrdersCommand): peers.protocol.Command.GetOpenOrders = {
        val tradingPair = decodeTradingPair(proto.tradingPair)
        peers.protocol.Command.GetOpenOrders(tradingPair)
      }

      override def encode(model: peers.protocol.Command.GetOpenOrders): protos.commands.GetOpenOrdersCommand = {
        protos.commands.GetOpenOrdersCommand().withTradingPair(model.pair.toString)
      }
    }
  }

  implicit lazy val subscribeCommandCodec: SubscribeCommandCodec = {
    new SubscribeCommandCodec {
      override def decode(proto: protos.commands.SubscribeCommand): peers.protocol.Command.Subscribe = {
        val tradingPair = decodeTradingPair(proto.tradingPair)
        peers.protocol.Command.Subscribe(tradingPair, proto.retrieveOrdersSummary)
      }

      override def encode(model: peers.protocol.Command.Subscribe): protos.commands.SubscribeCommand = {
        protos.commands
          .SubscribeCommand()
          .withTradingPair(model.pair.toString)
          .withRetrieveOrdersSummary(model.retrieveOrdersSummary)
      }
    }
  }

  implicit lazy val unsubscribeCommandCodec: UnsubscribeCommandCodec = {
    new UnsubscribeCommandCodec {
      override def decode(proto: protos.commands.UnsubscribeCommand): peers.protocol.Command.Unsubscribe = {
        val tradingPair = decodeTradingPair(proto.tradingPair)
        peers.protocol.Command.Unsubscribe(tradingPair)
      }

      override def encode(model: peers.protocol.Command.Unsubscribe): protos.commands.UnsubscribeCommand = {
        protos.commands.UnsubscribeCommand().withTradingPair(model.pair.toString)
      }
    }
  }

  implicit lazy val sendOrderMessageCommandCodec: SendOrderMessageCommandCodec = new SendOrderMessageCommandCodec {

    override def decode(proto: protos.commands.SendOrderMessageCommand): peers.protocol.Command.SendOrderMessage = {
      val orderId = OrderId.from(proto.orderId).getOrThrow("Missing or invalid order id")
      peers.protocol.Command.SendOrderMessage(OrderMessage(proto.message.toByteArray.toVector, orderId))
    }

    override def encode(model: peers.protocol.Command.SendOrderMessage): protos.commands.SendOrderMessageCommand = {
      protos.commands
        .SendOrderMessageCommand(
          model.orderMessage.orderId.value.toString,
          ByteString.copyFrom(model.orderMessage.message.toArray)
        )
    }
  }

  implicit lazy val cancelMatchedOrderCommandCodec: CancelMatchedOrderCommandCodec =
    new CancelMatchedOrderCommandCodec {

      override def decode(
          proto: protos.commands.CancelMatchedOrderCommand
      ): peers.protocol.Command.CancelMatchedOrder = {
        val orderId = OrderId.from(proto.orderId).getOrThrow("Missing or invalid order id")
        peers.protocol.Command.CancelMatchedOrder(orderId)
      }

      override def encode(
          model: peers.protocol.Command.CancelMatchedOrder
      ): protos.commands.CancelMatchedOrderCommand = {
        protos.commands.CancelMatchedOrderCommand(model.id.value.toString)
      }
    }

  implicit lazy val getHistoricTradesCommandCodec: GetHistoricTradesCommandCodec = new GetHistoricTradesCommandCodec {

    override def decode(proto: protos.commands.GetHistoricTradesCommand): peers.protocol.Command.GetHistoricTrades = {
      val lastSeenTradeId = Try(UUID.fromString(proto.lastSeenTradeId)).map(Trade.Id.apply).toOption
      val tradingPair = decodeTradingPair(proto.tradingPair)
      peers.protocol.Command.GetHistoricTrades(proto.limit, lastSeenTradeId, tradingPair)
    }

    override def encode(model: peers.protocol.Command.GetHistoricTrades): protos.commands.GetHistoricTradesCommand = {
      protos.commands
        .GetHistoricTradesCommand()
        .withLimit(model.limit)
        .withLastSeenTradeId(model.lastSeenTrade.map(_.value.toString).getOrElse(""))
        .withTradingPair(model.tradingPair.toString)
    }
  }

  implicit lazy val getBarsPricesCommandCodec: GetBarsPricesCommandCodec = {
    new GetBarsPricesCommandCodec {
      override def decode(proto: protos.commands.GetBarsPricesCommand): peers.protocol.Command.GetBarsPrices = {
        val tradingPair = decodeTradingPair(proto.tradingPair)
        val resolution = Resolution.from(proto.resolution).getOrThrow("Invalid resolution")
        val from = Instant.ofEpochSecond(proto.from)
        val to = Instant.ofEpochSecond(proto.to)
        val limit = proto.limit
        peers.protocol.Command.GetBarsPrices(tradingPair, resolution, from, to, limit)
      }

      override def encode(model: peers.protocol.Command.GetBarsPrices): protos.commands.GetBarsPricesCommand = {
        protos.commands
          .GetBarsPricesCommand()
          .withTradingPair(model.tradingPair.toString)
          .withResolution(model.resolution.toString)
          .withFrom(model.from.getEpochSecond)
          .withTo(model.to.getEpochSecond)
          .withLimit(model.limit)
      }
    }
  }

  implicit lazy val getTradingOrderByIdCommandCodec: GetTradingOrderByIdCommandCodec =
    new GetTradingOrderByIdCommandCodec {

      override def decode(proto: protos.commands.GetOpenOrderByIdCommand): peers.protocol.Command.GetOpenOrderById = {
        val orderId = OrderId.from(proto.orderId).getOrThrow("Missing or invalid order id")
        peers.protocol.Command.GetOpenOrderById(orderId)
      }

      override def encode(model: peers.protocol.Command.GetOpenOrderById): protos.commands.GetOpenOrderByIdCommand = {
        protos.commands.GetOpenOrderByIdCommand(model.id.value.toString)
      }
    }

  implicit lazy val getTradingPairsCommandCodec: GetTradingPairsCommandCodec = {
    new GetTradingPairsCommandCodec {
      override def decode(proto: protos.commands.GetTradingPairsCommand): peers.protocol.Command.GetTradingPairs = {
        peers.protocol.Command.GetTradingPairs()
      }

      override def encode(model: peers.protocol.Command.GetTradingPairs): protos.commands.GetTradingPairsCommand = {
        protos.commands.GetTradingPairsCommand()
      }
    }
  }

  implicit lazy val cleanTradingPairOrdersCommandCodec: CleanTradingPairOrdersCommandCodec = {
    new CleanTradingPairOrdersCommandCodec {
      override def decode(
          proto: protos.commands.CleanTradingPairOrdersCommand
      ): peers.protocol.Command.CleanTradingPairOrders = {
        val tradingPair = decodeTradingPair(proto.tradingPair)
        peers.protocol.Command.CleanTradingPairOrders(tradingPair)
      }

      override def encode(
          model: peers.protocol.Command.CleanTradingPairOrders
      ): protos.commands.CleanTradingPairOrdersCommand = {
        protos.commands.CleanTradingPairOrdersCommand().withTradingPair(model.pair.toString)
      }
    }
  }

  implicit lazy val getInvoicePaymentCommandCodec: GetLndPaymentInvoiceCommandCodec = {
    new GetLndPaymentInvoiceCommandCodec {
      override def decode(
          proto: protos.commands.GetLndPaymentInvoiceCommand
      ): peers.protocol.Command.GetInvoicePayment = {
        val currency = decodeCurrency(proto.currency)
        val amount = satoshisCodec.decode(proto.amount.getOrThrow("Missing amount"))
        peers.protocol.Command.GetInvoicePayment(currency = currency, amount = amount)
      }

      override def encode(
          model: peers.protocol.Command.GetInvoicePayment
      ): protos.commands.GetLndPaymentInvoiceCommand = {
        protos.commands
          .GetLndPaymentInvoiceCommand()
          .withCurrency(model.currency.toString)
          .withAmount(satoshisCodec.encode(model.amount))
      }
    }
  }

  implicit lazy val generateInvoiceToRentChannelCommandCodec: GenerateInvoiceToRentChannelCommandCodec = {
    new GenerateInvoiceToRentChannelCommandCodec {
      override def decode(
          proto: protos.commands.GenerateInvoiceToRentChannelCommand
      ): peers.protocol.Command.GenerateInvoiceToRentChannel = {
        val currency = decodeCurrency(proto.currency)
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        val capacity = satoshisCodec.decode(proto.capacity.getOrThrow("Invalid capacity"))
        val lifeTimeSeconds = proto.lifetimeSeconds
        val channelPayment = ChannelFeePayment(currency, payingCurrency, capacity, lifeTimeSeconds, Satoshis.Zero)
        peers.protocol.Command.GenerateInvoiceToRentChannel(channelPayment)
      }

      override def encode(
          model: peers.protocol.Command.GenerateInvoiceToRentChannel
      ): protos.commands.GenerateInvoiceToRentChannelCommand = {
        val channel = model.channelFeePayment
        protos.commands
          .GenerateInvoiceToRentChannelCommand()
          .withCurrency(channel.currency.toString)
          .withPayingCurrency(channel.payingCurrency.toString)
          .withCapacity(satoshisCodec.encode(channel.capacity))
          .withLifetimeSeconds(channel.lifeTimeSeconds)
      }
    }
  }

  implicit lazy val rentChannelCommandCodec: RentChannelCommandCodec = {
    new RentChannelCommandCodec {
      override def decode(
          proto: protos.commands.RentChannelCommand
      ): peers.protocol.Command.RentChannel = {

        val paymentRHash = paymentRHashCodec.decode(proto.paymentHash)
        val currency = decodeCurrency(proto.payingCurrency)
        peers.protocol.Command.RentChannel(paymentRHash, currency)
      }

      override def encode(
          model: peers.protocol.Command.RentChannel
      ): protos.commands.RentChannelCommand = {
        protos.commands
          .RentChannelCommand()
          .withPaymentHash(paymentRHashCodec.encode(model.paymentRHash))
          .withPayingCurrency(model.payingCurrency.entryName)
      }
    }
  }

  implicit lazy val getChannelStatusCommandCodec: GetChannelStatusCommandCodec = {
    new GetChannelStatusCommandCodec {
      override def decode(
          proto: protos.commands.GetChannelStatusCommand
      ): peers.protocol.Command.GetChannelStatus = {
        val channelId = UUID.fromString(proto.channelId)
        peers.protocol.Command.GetChannelStatus(channelId)
      }

      override def encode(
          model: peers.protocol.Command.GetChannelStatus
      ): protos.commands.GetChannelStatusCommand = {
        protos.commands
          .GetChannelStatusCommand()
          .withChannelId(model.channelId.toString)
      }
    }
  }

  implicit lazy val getFeeToRentChannelCommandCodec: GetFeeToRentChannelCommandCodec = {
    new GetFeeToRentChannelCommandCodec {
      override def decode(
          proto: protos.commands.GetFeeToRentChannelCommand
      ): peers.protocol.Command.GetFeeToRentChannel = {
        val currency = decodeCurrency(proto.currency)
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        val capacity = satoshisCodec.decode(proto.capacity.getOrThrow("Invalid capacity"))
        val lifeTimeSeconds = proto.lifetimeSeconds
        val channelPayment = ChannelFeePayment(currency, payingCurrency, capacity, lifeTimeSeconds, Satoshis.Zero)
        peers.protocol.Command.GetFeeToRentChannel(channelPayment)
      }

      override def encode(
          model: peers.protocol.Command.GetFeeToRentChannel
      ): protos.commands.GetFeeToRentChannelCommand = {
        val channel = model.channelFeePayment
        protos.commands
          .GetFeeToRentChannelCommand()
          .withCurrency(channel.currency.toString)
          .withPayingCurrency(channel.payingCurrency.toString)
          .withCapacity(satoshisCodec.encode(channel.capacity))
          .withLifetimeSeconds(channel.lifeTimeSeconds)
      }
    }
  }

  implicit lazy val refundFeeCommandCodec: RefundFeeCommandCodec = {
    new RefundFeeCommandCodec {
      override def decode(
          proto: protos.commands.RefundFeeCommand
      ): peers.protocol.Command.RefundFee = {
        val currency = decodeCurrency(proto.currency)
        val paymentRHash = PaymentRHash.untrusted(proto.refundedPaymentHash.toByteArray)
        val refundedFees = paymentRHash match {
          case Some(value) => List(RefundablePayment(value, Satoshis.Zero))
          case None => proto.refundedFees.map(refundablePaymentCodec.decode).toList
        }

        peers.protocol.Command.RefundFee(currency, refundedFees)
      }

      override def encode(
          model: peers.protocol.Command.RefundFee
      ): protos.commands.RefundFeeCommand = {
        protos.commands
          .RefundFeeCommand()
          .withCurrency(model.currency.entryName)
          .withRefundedFees(model.refundedFees.map(refundablePaymentCodec.encode))
      }
    }
  }

  // TODO: remove payment hash compatibility
  implicit lazy val getRefundableAmountCommandCodec: GetRefundableAmountCommandCodec = {
    new GetRefundableAmountCommandCodec {
      override def decode(
          proto: protos.commands.GetRefundableAmountCommand
      ): peers.protocol.Command.GetRefundableAmount = {
        val currency = decodeCurrency(proto.currency)
        val paymentRHash = PaymentRHash.untrusted(proto.paymentHash.toByteArray)
        val refundablePaymentList = paymentRHash match {
          case Some(value) => List(RefundablePayment(value, Satoshis.Zero))
          case None => proto.refundablePayments.map(refundablePaymentCodec.decode).toList
        }
        peers.protocol.Command.GetRefundableAmount(currency, refundablePaymentList)
      }

      override def encode(
          model: peers.protocol.Command.GetRefundableAmount
      ): protos.commands.GetRefundableAmountCommand = {
        val refundablePaymentList = model.refundablePaymentList.map(refundablePaymentCodec.encode)
        protos.commands
          .GetRefundableAmountCommand()
          .withCurrency(model.currency.entryName)
          .withRefundablePayments(refundablePaymentList)
      }
    }
  }

  implicit lazy val getFeeToExtendRentedChannelCommandCodec: GetFeeToExtendRentedChannelCommandCodec = {
    new GetFeeToExtendRentedChannelCommandCodec {
      override def decode(
          proto: protos.commands.GetFeeToExtendRentedChannelCommand
      ): peers.protocol.Command.GetFeeToExtendRentedChannel = {
        val channelId = UUID.fromString(proto.channelId)
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        peers.protocol.Command.GetFeeToExtendRentedChannel(channelId, payingCurrency, proto.lifetimeSeconds)
      }

      override def encode(
          model: peers.protocol.Command.GetFeeToExtendRentedChannel
      ): protos.commands.GetFeeToExtendRentedChannelCommand = {
        protos.commands
          .GetFeeToExtendRentedChannelCommand()
          .withChannelId(model.channelId.toString)
          .withPayingCurrency(model.payingCurrency.entryName)
          .withLifetimeSeconds(model.lifetimeSeconds)
      }
    }
  }

  implicit lazy val generateInvoiceToExtendRentedChannelCommandCodec
      : GenerateInvoiceToExtendRentedChannelCommandCodec = {
    new GenerateInvoiceToExtendRentedChannelCommandCodec {
      override def decode(
          proto: protos.commands.GenerateInvoiceToExtendRentedChannelCommand
      ): peers.protocol.Command.GenerateInvoiceToExtendRentedChannel = {
        val channelId = ChannelId.LndChannelId.from(proto.channelId).getOrThrow("Invalid channel id")
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        peers.protocol.Command.GenerateInvoiceToExtendRentedChannel(channelId, payingCurrency, proto.lifetimeSeconds)
      }

      override def encode(
          model: peers.protocol.Command.GenerateInvoiceToExtendRentedChannel
      ): protos.commands.GenerateInvoiceToExtendRentedChannelCommand = {
        protos.commands
          .GenerateInvoiceToExtendRentedChannelCommand()
          .withChannelId(model.channelId.value.toString)
          .withPayingCurrency(model.payingCurrency.entryName)
          .withLifetimeSeconds(model.lifetimeSeconds)
      }
    }
  }

  implicit lazy val generatePaymentHashToExtendConnextRentedChannelCommandCodec
      : GeneratePaymentHashToExtendConnextRentedChannelCommandCodec = {
    new GeneratePaymentHashToExtendConnextRentedChannelCommandCodec {
      override def decode(
          proto: protos.commands.GeneratePaymentHashToExtendConnextRentedChannelCommand
      ): peers.protocol.Command.GeneratePaymentHashToExtendConnextRentedChannel = {
        val channelId = ChannelId.ConnextChannelId.from(proto.channelId).getOrThrow("Invalid channel id")
        val payingCurrency = decodeCurrency(proto.payingCurrency)

        peers.protocol.Command.GeneratePaymentHashToExtendConnextRentedChannel(
          channelId,
          payingCurrency,
          proto.lifetimeSeconds
        )
      }

      override def encode(
          model: peers.protocol.Command.GeneratePaymentHashToExtendConnextRentedChannel
      ): protos.commands.GeneratePaymentHashToExtendConnextRentedChannelCommand = {
        protos.commands
          .GeneratePaymentHashToExtendConnextRentedChannelCommand()
          .withChannelId(model.channelId.value.toString)
          .withPayingCurrency(model.payingCurrency.entryName)
          .withLifetimeSeconds(model.lifetimeSeconds)
      }
    }
  }

  implicit lazy val extendRentedChannelTimeCommandCodec: ExtendRentedChannelTimeCommandCodec = {
    new ExtendRentedChannelTimeCommandCodec {
      override def decode(
          proto: protos.commands.ExtendRentedChannelTimeCommand
      ): peers.protocol.Command.ExtendRentedChannelTime = {
        peers.protocol.Command.ExtendRentedChannelTime(
          paymentRHashCodec.decode(proto.paymentHash),
          decodeCurrency(proto.payingCurrency)
        )
      }

      override def encode(
          model: peers.protocol.Command.ExtendRentedChannelTime
      ): protos.commands.ExtendRentedChannelTimeCommand = {
        protos.commands
          .ExtendRentedChannelTimeCommand()
          .withPaymentHash(paymentRHashCodec.encode(model.paymentHash))
          .withPayingCurrency(model.payingCurrency.entryName)
      }
    }
  }

  implicit lazy val registerPublicKeyCodec: RegisterPublicKeyCommandCodec = {
    new RegisterPublicKeyCommandCodec {
      override def decode(
          proto: protos.commands.RegisterPublicKeyCommand
      ): peers.protocol.Command.RegisterPublicKey = {
        val publicKey = Identifier.LndPublicKey.untrusted(proto.publicKey.toByteArray).getOrThrow("Invalid public key")

        peers.protocol.Command.RegisterPublicKey(
          decodeCurrency(proto.currency),
          publicKey
        )
      }

      override def encode(
          model: peers.protocol.Command.RegisterPublicKey
      ): protos.commands.RegisterPublicKeyCommand = {
        protos.commands
          .RegisterPublicKeyCommand()
          .withCurrency(model.currency.entryName)
          .withPublicKey(ByteString.copyFrom(model.nodePublicKey.value.toArray))
      }
    }
  }

  implicit lazy val registerPublicIdentifierCommandCodec: RegisterPublicIdentifierCommandCodec = {
    new RegisterPublicIdentifierCommandCodec {
      override def decode(
          proto: protos.commands.RegisterPublicIdentifierCommand
      ): peers.protocol.Command.RegisterPublicIdentifier = {
        val publicIdentifier = ConnextPublicIdentifier
          .untrusted(proto.publicIdentifier)
          .getOrThrow("Invalid public identifier")

        peers.protocol.Command.RegisterPublicIdentifier(
          decodeCurrency(proto.currency),
          publicIdentifier
        )
      }

      override def encode(
          model: peers.protocol.Command.RegisterPublicIdentifier
      ): protos.commands.RegisterPublicIdentifierCommand = {
        protos.commands
          .RegisterPublicIdentifierCommand()
          .withCurrency(model.currency.entryName)
          .withPublicIdentifier(model.publicIdentifier.value)
      }
    }
  }

  implicit lazy val getConnextPaymentInformationCommandCodec: GetConnextPaymentInformationCommandCodec = {
    new GetConnextPaymentInformationCommandCodec {
      override def decode(
          proto: protos.commands.GetConnextPaymentInformationCommand
      ): peers.protocol.Command.GetConnextPaymentInformation = {
        peers.protocol.Command.GetConnextPaymentInformation(
          decodeCurrency(proto.currency)
        )
      }

      override def encode(
          model: peers.protocol.Command.GetConnextPaymentInformation
      ): protos.commands.GetConnextPaymentInformationCommand = {
        protos.commands
          .GetConnextPaymentInformationCommand()
          .withCurrency(model.currency.entryName)
      }
    }
  }

  implicit lazy val generatePaymentHashToRentChannelCommandCodec: GeneratePaymentHashToRentChannelCommandCodec = {
    new GeneratePaymentHashToRentChannelCommandCodec {
      override def decode(
          proto: protos.commands.GeneratePaymentHashToRentChannelCommand
      ): peers.protocol.Command.GeneratePaymentHashToRentChannel = {
        val currency = decodeCurrency(proto.currency)
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        val capacity = satoshisCodec.decode(proto.capacity.getOrThrow("Invalid capacity"))
        val lifeTimeSeconds = proto.lifetimeSeconds
        val channelPayment = ChannelFeePayment(currency, payingCurrency, capacity, lifeTimeSeconds, Satoshis.Zero)
        peers.protocol.Command.GeneratePaymentHashToRentChannel(channelPayment)
      }

      override def encode(
          model: peers.protocol.Command.GeneratePaymentHashToRentChannel
      ): protos.commands.GeneratePaymentHashToRentChannelCommand = {
        val channel = model.channelFeePayment
        protos.commands
          .GeneratePaymentHashToRentChannelCommand()
          .withCurrency(channel.currency.toString)
          .withPayingCurrency(channel.payingCurrency.toString)
          .withCapacity(satoshisCodec.encode(channel.capacity))
          .withLifetimeSeconds(channel.lifeTimeSeconds)
      }
    }
  }

  implicit lazy val registerConnextChannelContractDeploymentFeeCommandCodec
      : RegisterConnextChannelContractDeploymentFeeCommandCodec = {
    new RegisterConnextChannelContractDeploymentFeeCommandCodec {
      override def decode(
          proto: protos.commands.RegisterConnextChannelContractDeploymentFeeCommand
      ): peers.protocol.Command.RegisterConnextChannelContractDeploymentFee = {
        peers.protocol.Command.RegisterConnextChannelContractDeploymentFee(proto.transactionHash)
      }

      override def encode(
          model: peers.protocol.Command.RegisterConnextChannelContractDeploymentFee
      ): protos.commands.RegisterConnextChannelContractDeploymentFeeCommand = {
        protos.commands
          .RegisterConnextChannelContractDeploymentFeeCommand()
          .withTransactionHash(model.transactionHash)
      }
    }
  }

  implicit lazy val getConnextChannelContractDeploymentFeeCommandCodec
      : GetConnextChannelContractDeploymentFeeCommandCodec = {
    new GetConnextChannelContractDeploymentFeeCommandCodec {
      override def decode(
          proto: protos.commands.GetConnextChannelContractDeploymentFeeCommand
      ): peers.protocol.Command.GetConnextChannelContractDeploymentFee = {
        peers.protocol.Command.GetConnextChannelContractDeploymentFee()
      }

      override def encode(
          model: peers.protocol.Command.GetConnextChannelContractDeploymentFee
      ): protos.commands.GetConnextChannelContractDeploymentFeeCommand = {
        protos.commands
          .GetConnextChannelContractDeploymentFeeCommand()
      }
    }
  }

  implicit lazy val commandCodec: CommandCodec = new CommandCodec {

    override def decode(proto: protos.api.Command): peers.ws.WebSocketIncomingMessage = {
      val command = proto.value match {
        case protos.api.Command.Value.Empty => throw new RuntimeException("Missing command")
        case protos.api.Command.Value.Ping(value) => pingCommandCodec.decode(value)
        case protos.api.Command.Value.GetTradingPairs(value) => getTradingPairsCommandCodec.decode(value)
        case protos.api.Command.Value.Subscribe(value) => subscribeCommandCodec.decode(value)
        case protos.api.Command.Value.Unsubscribe(value) => unsubscribeCommandCodec.decode(value)
        case protos.api.Command.Value.GetOpenOrders(value) => getTradingOrdersCommandCodec.decode(value)
        case protos.api.Command.Value.GetHistoricTrades(value) => getHistoricTradesCommandCodec.decode(value)
        case protos.api.Command.Value.GetBarsPrices(value) => getBarsPricesCommandCodec.decode(value)
        case protos.api.Command.Value.PlaceOrder(value) => placeOrderCommandCodec.decode(value)
        case protos.api.Command.Value.GetTradingOrderById(value) => getTradingOrderByIdCommandCodec.decode(value)
        case protos.api.Command.Value.CancelOrder(value) => cancelOrderCommandCodec.decode(value)
        case protos.api.Command.Value.SendOrderMessage(value) => sendOrderMessageCommandCodec.decode(value)
        case protos.api.Command.Value.CancelMatchedOrder(value) => cancelMatchedOrderCommandCodec.decode(value)
        case protos.api.Command.Value.CleanTradingPairOrders(value) => cleanTradingPairOrdersCommandCodec.decode(value)
        case protos.api.Command.Value.GetLndPaymentInvoiceCommand(value) => getInvoicePaymentCommandCodec.decode(value)
        case protos.api.Command.Value.GenerateInvoiceToRentChannelCommand(value) =>
          generateInvoiceToRentChannelCommandCodec.decode(value)
        case protos.api.Command.Value.RentChannelCommand(value) => rentChannelCommandCodec.decode(value)
        case protos.api.Command.Value.GetChannelStatusCommand(value) => getChannelStatusCommandCodec.decode(value)
        case protos.api.Command.Value.GetFeeToRentChannelCommand(value) => getFeeToRentChannelCommandCodec.decode(value)
        case protos.api.Command.Value.RefundFeeCommand(value) => refundFeeCommandCodec.decode(value)
        case protos.api.Command.Value.GetRefundableAmountCommand(value) => getRefundableAmountCommandCodec.decode(value)
        case protos.api.Command.Value.GetFeeToExtendRentedChannelCommand(value) =>
          getFeeToExtendRentedChannelCommandCodec.decode(value)
        case protos.api.Command.Value.GenerateInvoiceToExtendRentedChannelCommand(value) =>
          generateInvoiceToExtendRentedChannelCommandCodec.decode(value)
        case protos.api.Command.Value.GeneratePaymentHashToExtendConnextRentedChannelCommand(value) =>
          generatePaymentHashToExtendConnextRentedChannelCommandCodec.decode(value)
        case protos.api.Command.Value.ExtendRentedChannelTimeCommand(value) =>
          extendRentedChannelTimeCommandCodec.decode(value)
        case protos.api.Command.Value.RegisterPublicKeyCommand(value) => registerPublicKeyCodec.decode(value)
        case protos.api.Command.Value.RegisterPublicIdentifierCommand(value) =>
          registerPublicIdentifierCommandCodec.decode(value)
        case protos.api.Command.Value.GetConnextPaymentInformationCommand(value) =>
          getConnextPaymentInformationCommandCodec.decode(value)
        case protos.api.Command.Value.GeneratePaymentHashToRentChannelCommand(value) =>
          generatePaymentHashToRentChannelCommandCodec.decode(value)
        case protos.api.Command.Value.RegisterConnextChannelContractDeploymentFeeCommand(value) =>
          registerConnextChannelContractDeploymentFeeCommandCodec.decode(value)
        case protos.api.Command.Value.GetConnextChannelContractDeploymentFeeCommand(value) =>
          getConnextChannelContractDeploymentFeeCommandCodec.decode(value)
      }

      peers.ws.WebSocketIncomingMessage(proto.clientMessageId, command)
    }

    override def encode(model: peers.ws.WebSocketIncomingMessage): protos.api.Command = {
      val value = model.command match {
        case _: peers.protocol.Command.InvalidCommand =>
          throw new RuntimeException("This is not supposed to be handled on this layer")

        case cmd: peers.protocol.Command.Ping =>
          val proto = pingCommandCodec.encode(cmd)
          protos.api.Command.Value.Ping(proto)

        case cmd: peers.protocol.Command.PlaceOrder =>
          val proto = placeOrderCommandCodec.encode(cmd)
          protos.api.Command.Value.PlaceOrder(proto)

        case cmd: peers.protocol.Command.CancelOpenOrder =>
          val proto = cancelOrderCommandCodec.encode(cmd)
          protos.api.Command.Value.CancelOrder(proto)

        case cmd: peers.protocol.Command.GetOpenOrders =>
          val proto = getTradingOrdersCommandCodec.encode(cmd)
          protos.api.Command.Value.GetOpenOrders(proto)

        case cmd: peers.protocol.Command.Subscribe =>
          val proto = subscribeCommandCodec.encode(cmd)
          protos.api.Command.Value.Subscribe(proto)

        case cmd: peers.protocol.Command.Unsubscribe =>
          val proto = unsubscribeCommandCodec.encode(cmd)
          protos.api.Command.Value.Unsubscribe(proto)

        case cmd: peers.protocol.Command.SendOrderMessage =>
          val proto = sendOrderMessageCommandCodec.encode(cmd)
          protos.api.Command.Value.SendOrderMessage(proto)

        case cmd: peers.protocol.Command.CancelMatchedOrder =>
          val proto = cancelMatchedOrderCommandCodec.encode(cmd)
          protos.api.Command.Value.CancelMatchedOrder(proto)

        case cmd: peers.protocol.Command.GetHistoricTrades =>
          val proto = getHistoricTradesCommandCodec.encode(cmd)
          protos.api.Command.Value.GetHistoricTrades(proto)

        case cmd: peers.protocol.Command.GetBarsPrices =>
          val proto = getBarsPricesCommandCodec.encode(cmd)
          protos.api.Command.Value.GetBarsPrices(proto)

        case cmd: peers.protocol.Command.GetOpenOrderById =>
          val proto = getTradingOrderByIdCommandCodec.encode(cmd)
          protos.api.Command.Value.GetTradingOrderById(proto)

        case cmd: peers.protocol.Command.GetTradingPairs =>
          val proto = getTradingPairsCommandCodec.encode(cmd)
          protos.api.Command.Value.GetTradingPairs(proto)

        case cmd: peers.protocol.Command.CleanTradingPairOrders =>
          val proto = cleanTradingPairOrdersCommandCodec.encode(cmd)
          protos.api.Command.Value.CleanTradingPairOrders(proto)

        case cmd: peers.protocol.Command.GetInvoicePayment =>
          val proto = getInvoicePaymentCommandCodec.encode(cmd)
          protos.api.Command.Value.GetLndPaymentInvoiceCommand(proto)

        case cmd: peers.protocol.Command.GenerateInvoiceToRentChannel =>
          val proto = generateInvoiceToRentChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.GenerateInvoiceToRentChannelCommand(proto)

        case cmd: peers.protocol.Command.RentChannel =>
          val proto = rentChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.RentChannelCommand(proto)

        case cmd: peers.protocol.Command.GetChannelStatus =>
          val proto = getChannelStatusCommandCodec.encode(cmd)
          protos.api.Command.Value.GetChannelStatusCommand(proto)

        case cmd: peers.protocol.Command.GetFeeToRentChannel =>
          val proto = getFeeToRentChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.GetFeeToRentChannelCommand(proto)

        case cmd: peers.protocol.Command.RefundFee =>
          val proto = refundFeeCommandCodec.encode(cmd)
          protos.api.Command.Value.RefundFeeCommand(proto)

        case cmd: peers.protocol.Command.GetRefundableAmount =>
          val proto = getRefundableAmountCommandCodec.encode(cmd)
          protos.api.Command.Value.GetRefundableAmountCommand(proto)

        case cmd: peers.protocol.Command.GetFeeToExtendRentedChannel =>
          val proto = getFeeToExtendRentedChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.GetFeeToExtendRentedChannelCommand(proto)

        case cmd: peers.protocol.Command.GenerateInvoiceToExtendRentedChannel =>
          val proto = generateInvoiceToExtendRentedChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.GenerateInvoiceToExtendRentedChannelCommand(proto)

        case cmd: peers.protocol.Command.GeneratePaymentHashToExtendConnextRentedChannel =>
          val proto = generatePaymentHashToExtendConnextRentedChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.GeneratePaymentHashToExtendConnextRentedChannelCommand(proto)

        case cmd: peers.protocol.Command.ExtendRentedChannelTime =>
          val proto = extendRentedChannelTimeCommandCodec.encode(cmd)
          protos.api.Command.Value.ExtendRentedChannelTimeCommand(proto)

        case cmd: peers.protocol.Command.RegisterPublicKey =>
          val proto = registerPublicKeyCodec.encode(cmd)
          protos.api.Command.Value.RegisterPublicKeyCommand(proto)

        case cmd: peers.protocol.Command.RegisterPublicIdentifier =>
          val proto = registerPublicIdentifierCommandCodec.encode(cmd)
          protos.api.Command.Value.RegisterPublicIdentifierCommand(proto)

        case cmd: peers.protocol.Command.GetConnextPaymentInformation =>
          val proto = getConnextPaymentInformationCommandCodec.encode(cmd)
          protos.api.Command.Value.GetConnextPaymentInformationCommand(proto)

        case cmd: peers.protocol.Command.GeneratePaymentHashToRentChannel =>
          val proto = generatePaymentHashToRentChannelCommandCodec.encode(cmd)
          protos.api.Command.Value.GeneratePaymentHashToRentChannelCommand(proto)

        case cmd: peers.protocol.Command.RegisterConnextChannelContractDeploymentFee =>
          val proto = registerConnextChannelContractDeploymentFeeCommandCodec.encode(cmd)
          protos.api.Command.Value.RegisterConnextChannelContractDeploymentFeeCommand(proto)

        case cmd: peers.protocol.Command.GetConnextChannelContractDeploymentFee =>
          val proto = getConnextChannelContractDeploymentFeeCommandCodec.encode(cmd)
          protos.api.Command.Value.GetConnextChannelContractDeploymentFeeCommand(proto)
      }

      protos.api.Command(model.clientMessageId, value)
    }
  }
}
