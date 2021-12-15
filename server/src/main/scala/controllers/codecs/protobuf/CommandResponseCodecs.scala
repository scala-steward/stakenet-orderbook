package controllers.codecs.protobuf

import java.time.Instant

import com.google.protobuf.ByteString
import io.stakenet.orderbook.actors.peers.protocol
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse.CommandFailed
import io.stakenet.orderbook.actors.peers.protocol.TaggedCommandResponse
import io.stakenet.orderbook.actors.peers.results.PlaceOrderResult
import io.stakenet.orderbook.models.ChannelIdentifier.{ConnextChannelAddress, LndOutpoint}
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{ChannelId, ConnextChannelStatus, OrderId, Satoshis}
import io.stakenet.orderbook.protos
import io.stakenet.orderbook.utils.Extensions.OptionExt

trait CommandResponseCodecs extends CommonProtoCodecs {

  type CommandResponseCodec = ProtoCodec[protos.api.Event.CommandResponse, protocol.TaggedCommandResponse]

  type CommandFailedCodec =
    ProtoCodec[protos.commands.CommandFailed, protocol.Event.CommandResponse.CommandFailed]
  type PingResponseCodec = ProtoCodec[protos.commands.PingResponse, protocol.Event.CommandResponse.PingResponse]

  type GetTradingPairsResponseCodec =
    ProtoCodec[protos.commands.GetTradingPairsResponse, protocol.Event.CommandResponse.GetTradingPairsResponse]

  type SubscribeResponseCodec =
    ProtoCodec[protos.commands.SubscribeResponse, protocol.Event.CommandResponse.SubscribeResponse]

  type UnsubscribeResponseCodec =
    ProtoCodec[protos.commands.UnsubscribeResponse, protocol.Event.CommandResponse.UnsubscribeResponse]

  type GetOpenOrdersResponseCodec =
    ProtoCodec[protos.commands.GetOpenOrdersResponse, protocol.Event.CommandResponse.GetOpenOrdersResponse]

  type GetHistoricTradesResponseCodec =
    ProtoCodec[protos.commands.GetHistoricTradesResponse, protocol.Event.CommandResponse.GetHistoricTradesResponse]

  type GetBarsPricesResponseCodec =
    ProtoCodec[protos.commands.GetBarsPricesResponse, protocol.Event.CommandResponse.GetBarsPricesResponse]

  type PlaceOrderResponseCodec =
    ProtoCodec[protos.commands.PlaceOrderResponse, protocol.Event.CommandResponse.PlaceOrderResponse]

  type GetOpenOrderByIdResponseCodec =
    ProtoCodec[protos.commands.GetOpenOrderByIdResponse, protocol.Event.CommandResponse.GetOpenOrderByIdResponse]

  type CancelOpenOrderResponseCodec =
    ProtoCodec[protos.commands.CancelOpenOrderResponse, protocol.Event.CommandResponse.CancelOpenOrderResponse]

  type SendOrderMessageResponseCodec =
    ProtoCodec[protos.commands.SendOrderMessageResponse, protocol.Event.CommandResponse.SendOrderMessageResponse]

  type CancelMatchedOrderResponseCodec =
    ProtoCodec[protos.commands.CancelMatchedOrderResponse, protocol.Event.CommandResponse.CancelMatchedOrderResponse]

  type CleanTradingPairOrdersResponseCodec =
    ProtoCodec[
      protos.commands.CleanTradingPairOrdersResponse,
      protocol.Event.CommandResponse.CleanTradingPairOrdersResponse
    ]

  type GetLndPaymentInvoiceResponseCodec =
    ProtoCodec[protos.commands.GetLndPaymentInvoiceResponse, protocol.Event.CommandResponse.GetInvoicePaymentResponse]

  type GenerateInvoiceToRentChannelResponseCodec =
    ProtoCodec[
      protos.commands.GenerateInvoiceToRentChannelResponse,
      protocol.Event.CommandResponse.GenerateInvoiceToRentChannelResponse
    ]

  type RentChannelResponseCodec =
    ProtoCodec[protos.commands.RentChannelResponse, protocol.Event.CommandResponse.RentChannelResponse]

  type GetChannelStatusResponseCodec =
    ProtoCodec[protos.commands.GetChannelStatusResponse, protocol.Event.CommandResponse.GetChannelStatusResponse]

  type GetFeeToRentChannelResponseCodec =
    ProtoCodec[protos.commands.GetFeeToRentChannelResponse, protocol.Event.CommandResponse.GetFeeToRentChannelResponse]

  type RefundFeeResponseCodec =
    ProtoCodec[protos.commands.RefundFeeResponse, protocol.Event.CommandResponse.RefundFeeResponse]

  type GetRefundableAmountResponseCodec =
    ProtoCodec[protos.commands.GetRefundableAmountResponse, protocol.Event.CommandResponse.GetRefundableAmountResponse]

  type GetFeeToExtendRentedChannelResponseCodec = ProtoCodec[
    protos.commands.GetFeeToExtendRentedChannelResponse,
    protocol.Event.CommandResponse.GetFeeToExtendRentedChannelResponse
  ]

  type GenerateInvoiceToExtendRentedChannelResponseCodec = ProtoCodec[
    protos.commands.GenerateInvoiceToExtendRentedChannelResponse,
    protocol.Event.CommandResponse.GenerateInvoiceToExtendRentedChannelResponse
  ]

  type GeneratePaymentHashToExtendConnextRentedChannelResponseCodec = ProtoCodec[
    protos.commands.GeneratePaymentHashToExtendConnextRentedChannelResponse,
    protocol.Event.CommandResponse.GeneratePaymentHashToExtendConnextRentedChannelResponse
  ]

  type ExtendRentedChannelTimeResponseCodec = ProtoCodec[
    protos.commands.ExtendRentedChannelTimeResponse,
    protocol.Event.CommandResponse.ExtendRentedChannelTimeResponse
  ]

  type RegisterPublicKeyResponseCodec = ProtoCodec[
    protos.commands.RegisterPublicKeyResponse,
    protocol.Event.CommandResponse.RegisterPublicKeyResponse
  ]

  type RegisterPublicIdentifierResponseCodec = ProtoCodec[
    protos.commands.RegisterPublicIdentifierResponse,
    protocol.Event.CommandResponse.RegisterPublicIdentifierResponse
  ]

  type GetConnextPaymentInformationResponseCodec = ProtoCodec[
    protos.commands.GetConnextPaymentInformationResponse,
    protocol.Event.CommandResponse.GetConnextPaymentInformationResponse
  ]

  type GeneratePaymentHashToRentChannelResponseCodec = ProtoCodec[
    protos.commands.GeneratePaymentHashToRentChannelResponse,
    protocol.Event.CommandResponse.GeneratePaymentHashToRentChannelResponse
  ]

  type RegisterConnextChannelContractDeploymentFeeResponseCodec = ProtoCodec[
    protos.commands.RegisterConnextChannelContractDeploymentFeeResponse,
    protocol.Event.CommandResponse.RegisterConnextChannelContractDeploymentFeeResponse
  ]

  type GetConnextChannelContractDeploymentFeeResponseCodec = ProtoCodec[
    protos.commands.GetConnextChannelContractDeploymentFeeResponse,
    protocol.Event.CommandResponse.GetConnextChannelContractDeploymentFeeResponse
  ]

  implicit val commandFailedCodec: CommandFailedCodec = new CommandFailedCodec {
    override def decode(proto: protos.commands.CommandFailed): protocol.Event.CommandResponse.CommandFailed = {
      proto.value match {
        case protos.commands.CommandFailed.Value.Empty =>
          throw new RuntimeException("Missing or invalid command failed value")
        case protos.commands.CommandFailed.Value.Reason(value) =>
          CommandFailed.Reason(value)
        case protos.commands.CommandFailed.Value.ServerInMaintenance(_) =>
          CommandFailed.ServerInMaintenance()
      }
    }

    override def encode(model: protocol.Event.CommandResponse.CommandFailed): protos.commands.CommandFailed = {
      model match {
        case CommandFailed.Reason(reason) =>
          val value = protos.commands.CommandFailed.Value.Reason(reason)
          protos.commands.CommandFailed(value)
        case CommandFailed.ServerInMaintenance() =>
          val value = protos.commands.CommandFailed.Value.ServerInMaintenance(protos.models.ServerInMaintenance())
          protos.commands.CommandFailed(value)
      }
    }
  }

  implicit val pingResponseCodec: PingResponseCodec = new PingResponseCodec {
    override def decode(proto: protos.commands.PingResponse): protocol.Event.CommandResponse.PingResponse = {
      protocol.Event.CommandResponse.PingResponse()
    }

    override def encode(model: protocol.Event.CommandResponse.PingResponse): protos.commands.PingResponse = {
      protos.commands.PingResponse()
    }
  }

  implicit val getTradingPairsResponseCodec: GetTradingPairsResponseCodec = new GetTradingPairsResponseCodec {
    override def decode(
        proto: protos.commands.GetTradingPairsResponse
    ): protocol.Event.CommandResponse.GetTradingPairsResponse = {
      val pairs = proto.tradingPairs.map(tradingPairCodec.decode)
      protocol.Event.CommandResponse.GetTradingPairsResponse(pairs.toList, proto.paysFees)
    }

    override def encode(
        model: protocol.Event.CommandResponse.GetTradingPairsResponse
    ): protos.commands.GetTradingPairsResponse = {
      val tradingPairs = model.tradingPairs.map(tradingPairCodec.encode)
      protos.commands.GetTradingPairsResponse(tradingPairs, model.paysFees)
    }
  }

  implicit val subscribeResponseCodec: SubscribeResponseCodec = new SubscribeResponseCodec {
    override def decode(
        proto: protos.commands.SubscribeResponse
    ): protocol.Event.CommandResponse.SubscribeResponse = {
      val pair = decodeTradingPair(proto.tradingPair)
      val bidsSummary = proto.summaryBids.map(orderSummaryCodec.decode).toList
      val asksSummary = proto.summaryAsks.map(orderSummaryCodec.decode).toList
      protocol.Event.CommandResponse.SubscribeResponse(pair, bidsSummary, asksSummary)
    }

    override def encode(
        model: protocol.Event.CommandResponse.SubscribeResponse
    ): protos.commands.SubscribeResponse = {
      val summaryBids = model.bidsSummary.map(orderSummaryCodec.encode)
      val summaryAsks = model.asksSummary.map(orderSummaryCodec.encode)
      protos.commands
        .SubscribeResponse()
        .withTradingPair(model.pair.toString)
        .withSummaryBids(summaryBids)
        .withSummaryAsks(summaryAsks)
    }
  }

  implicit val unsubscribeResponseCodec: UnsubscribeResponseCodec = new UnsubscribeResponseCodec {
    override def decode(
        proto: protos.commands.UnsubscribeResponse
    ): protocol.Event.CommandResponse.UnsubscribeResponse = {
      val pair = decodeTradingPair(proto.tradingPair)
      protocol.Event.CommandResponse.UnsubscribeResponse(pair)
    }

    override def encode(
        model: protocol.Event.CommandResponse.UnsubscribeResponse
    ): protos.commands.UnsubscribeResponse = {
      protos.commands.UnsubscribeResponse(model.pair.toString)
    }
  }

  implicit val getOpenOrdersResponseCodec: GetOpenOrdersResponseCodec = new GetOpenOrdersResponseCodec {
    override def decode(
        proto: protos.commands.GetOpenOrdersResponse
    ): protocol.Event.CommandResponse.GetOpenOrdersResponse = {
      val tradingPair = decodeTradingPair(proto.tradingPair)
      val orderSummaryBids = proto.summaryBids.map(orderSummaryCodec.decode).toList
      val orderSummaryAsks = proto.summaryAsks.map(orderSummaryCodec.decode).toList
      protocol.Event.CommandResponse
        .GetOpenOrdersResponse(tradingPair, bidsSummary = orderSummaryBids, asksSummary = orderSummaryAsks)
    }

    override def encode(
        model: protocol.Event.CommandResponse.GetOpenOrdersResponse
    ): protos.commands.GetOpenOrdersResponse = {
      val summaryBids = model.bidsSummary.map(orderSummaryCodec.encode)
      val summaryAsks = model.asksSummary.map(orderSummaryCodec.encode)
      protos.commands
        .GetOpenOrdersResponse()
        .withTradingPair(model.pair.toString)
        .withSummaryBids(summaryBids)
        .withSummaryAsks(summaryAsks)
    }
  }

  implicit val getHistoricTradesResponseCodec: GetHistoricTradesResponseCodec = new GetHistoricTradesResponseCodec {
    override def decode(
        proto: protos.commands.GetHistoricTradesResponse
    ): protocol.Event.CommandResponse.GetHistoricTradesResponse = {
      val trades = proto.trades.map(tradeCodec.decode)
      protocol.Event.CommandResponse.GetHistoricTradesResponse(trades.toList)
    }

    override def encode(
        model: protocol.Event.CommandResponse.GetHistoricTradesResponse
    ): protos.commands.GetHistoricTradesResponse = {
      val trades = model.trades.map(tradeCodec.encode)
      protos.commands.GetHistoricTradesResponse(trades)
    }
  }

  implicit val getBarsPricesResponseCodec: GetBarsPricesResponseCodec = new GetBarsPricesResponseCodec {
    override def decode(
        proto: protos.commands.GetBarsPricesResponse
    ): protocol.Event.CommandResponse.GetBarsPricesResponse = {
      val bars = proto.barPrices.map(barsCodec.decode)
      protocol.Event.CommandResponse.GetBarsPricesResponse(bars.toList)
    }

    override def encode(
        model: protocol.Event.CommandResponse.GetBarsPricesResponse
    ): protos.commands.GetBarsPricesResponse = {
      val prices = model.bars.map(barsCodec.encode)
      protos.commands
        .GetBarsPricesResponse()
        .withBarPrices(prices)
    }
  }

  implicit val placeOrderResponseCodec: PlaceOrderResponseCodec = new PlaceOrderResponseCodec {
    override def decode(
        proto: protos.commands.PlaceOrderResponse
    ): protocol.Event.CommandResponse.PlaceOrderResponse = {
      val result = proto.value match {
        case protos.commands.PlaceOrderResponse.Value.Empty =>
          throw new RuntimeException("Missing place order response result")
        case protos.commands.PlaceOrderResponse.Value.MyOrderPlaced(value) =>
          val orderProto = value.order.getOrThrow("Missing or invalid order")
          val order = orderCodec.decode(orderProto)
          PlaceOrderResult.OrderPlaced(order)
        case protos.commands.PlaceOrderResponse.Value.MyOrderRejected(value) =>
          PlaceOrderResult.OrderRejected(value.reason)

        case protos.commands.PlaceOrderResponse.Value.MyOrderMatched(value) =>
          val tradeProto = value.trade.getOrThrow("Missing or invalid trade")
          val trade = tradeCodec.decode(tradeProto)
          val orderMatchedWith = orderCodec.decode(value.orderMatchedWith.getOrThrow("Missing or invalid order"))
          PlaceOrderResult.OrderMatched(trade, orderMatchedWith)
      }

      protocol.Event.CommandResponse.PlaceOrderResponse(result)
    }

    override def encode(
        model: protocol.Event.CommandResponse.PlaceOrderResponse
    ): protos.commands.PlaceOrderResponse = {
      val result = model.result match {
        case PlaceOrderResult.OrderRejected(reason) =>
          val value = protos.commands.PlaceOrderResponse.MyOrderRejected(reason)
          protos.commands.PlaceOrderResponse.Value.MyOrderRejected(value)
        case PlaceOrderResult.OrderMatched(trade, orderMatchedWith) =>
          val tradeProto = tradeCodec.encode(trade)
          val order = orderCodec.encode(orderMatchedWith)
          val value =
            protos.commands.PlaceOrderResponse.MyOrderMatched().withTrade(tradeProto).withOrderMatchedWith(order)
          protos.commands.PlaceOrderResponse.Value.MyOrderMatched(value)
        case PlaceOrderResult.OrderPlaced(order) =>
          val orderProto = orderCodec.encode(order)
          val value = protos.commands.PlaceOrderResponse.MyOrderPlaced().withOrder(orderProto)
          protos.commands.PlaceOrderResponse.Value.MyOrderPlaced(value)
      }
      protos.commands.PlaceOrderResponse(result)
    }
  }

  implicit val getOpenOrderByIdResponseCodec: GetOpenOrderByIdResponseCodec = new GetOpenOrderByIdResponseCodec {
    override def decode(
        proto: protos.commands.GetOpenOrderByIdResponse
    ): protocol.Event.CommandResponse.GetOpenOrderByIdResponse = {
      val orderMaybe = proto.order.map(orderCodec.decode)
      protocol.Event.CommandResponse.GetOpenOrderByIdResponse(orderMaybe)
    }

    override def encode(
        model: protocol.Event.CommandResponse.GetOpenOrderByIdResponse
    ): protos.commands.GetOpenOrderByIdResponse = {
      val orderMaybe = model.result.map(orderCodec.encode)
      protos.commands.GetOpenOrderByIdResponse(order = orderMaybe)
    }
  }

  implicit val cancelOpenOrderResponseCodec: CancelOpenOrderResponseCodec = new CancelOpenOrderResponseCodec {
    override def decode(
        proto: protos.commands.CancelOpenOrderResponse
    ): protocol.Event.CommandResponse.CancelOpenOrderResponse = {
      val orderMaybe = proto.order.map(orderCodec.decode)
      protocol.Event.CommandResponse.CancelOpenOrderResponse(orderMaybe)
    }

    override def encode(
        model: protocol.Event.CommandResponse.CancelOpenOrderResponse
    ): protos.commands.CancelOpenOrderResponse = {
      val orderMaybe = model.result.map(orderCodec.encode)
      protos.commands.CancelOpenOrderResponse(orderMaybe)
    }
  }

  implicit val sendOrderMessageResponseCodec: SendOrderMessageResponseCodec = new SendOrderMessageResponseCodec {
    override def decode(
        proto: protos.commands.SendOrderMessageResponse
    ): protocol.Event.CommandResponse.SendOrderMessageResponse = {
      protocol.Event.CommandResponse.SendOrderMessageResponse()
    }

    override def encode(
        model: protocol.Event.CommandResponse.SendOrderMessageResponse
    ): protos.commands.SendOrderMessageResponse = {
      protos.commands.SendOrderMessageResponse()
    }
  }

  implicit val cancelMatchedOrderResponseCodec: CancelMatchedOrderResponseCodec = new CancelMatchedOrderResponseCodec {
    override def decode(
        proto: protos.commands.CancelMatchedOrderResponse
    ): protocol.Event.CommandResponse.CancelMatchedOrderResponse = {
      val tradeMaybe = proto.trade.map(tradeCodec.decode)
      protocol.Event.CommandResponse.CancelMatchedOrderResponse(tradeMaybe)
    }

    override def encode(
        model: protocol.Event.CommandResponse.CancelMatchedOrderResponse
    ): protos.commands.CancelMatchedOrderResponse = {
      val tradeMaybe = model.result.map(tradeCodec.encode)
      protos.commands.CancelMatchedOrderResponse(tradeMaybe)
    }
  }

  implicit val cleanTradingPairOrdersResponseCodec: CleanTradingPairOrdersResponseCodec =
    new CleanTradingPairOrdersResponseCodec {
      override def decode(
          proto: protos.commands.CleanTradingPairOrdersResponse
      ): protocol.Event.CommandResponse.CleanTradingPairOrdersResponse = {
        val tradingPair = decodeTradingPair(proto.tradingPair)
        val openOrdersRemoved = proto.openOrdersRemoved.flatMap(OrderId.from).toList
        val matchedOrdersRemoved = proto.matchedOrdersRemoved.flatMap(OrderId.from).toList
        protocol.Event.CommandResponse
          .CleanTradingPairOrdersResponse(tradingPair, openOrdersRemoved, matchedOrdersRemoved)
      }

      override def encode(
          model: protocol.Event.CommandResponse.CleanTradingPairOrdersResponse
      ): protos.commands.CleanTradingPairOrdersResponse = {
        val openOrdersRemoved = model.openOrdersRemoved.map(_.toString)
        val matchedOrdersRemoved = model.matchedOrdersRemoved.map(_.toString)

        protos.commands
          .CleanTradingPairOrdersResponse()
          .withTradingPair(model.pair.toString)
          .withOpenOrdersRemoved(openOrdersRemoved)
          .withMatchedOrdersRemoved(matchedOrdersRemoved)
      }
    }

  implicit val getLndPaymentInvoiceResponseCodec: GetLndPaymentInvoiceResponseCodec =
    new GetLndPaymentInvoiceResponseCodec {
      override def decode(
          proto: protos.commands.GetLndPaymentInvoiceResponse
      ): protocol.Event.CommandResponse.GetInvoicePaymentResponse = {
        val currency = decodeCurrency(proto.currency)

        val paymentRequest = Option(proto.paymentRequest).filter(_.nonEmpty)

        protocol.Event.CommandResponse
          .GetInvoicePaymentResponse(currency, noFeeRequired = proto.noFeeRequired, paymentRequest = paymentRequest)
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetInvoicePaymentResponse
      ): protos.commands.GetLndPaymentInvoiceResponse = {

        protos.commands
          .GetLndPaymentInvoiceResponse()
          .withCurrency(model.currency.toString)
          .withPaymentRequest(model.paymentRequest.getOrElse(""))
          .withNoFeeRequired(model.noFeeRequired)
      }
    }

  implicit val generateInvoiceToRentChannelResponseCodec: GenerateInvoiceToRentChannelResponseCodec =
    new GenerateInvoiceToRentChannelResponseCodec {
      override def decode(
          proto: protos.commands.GenerateInvoiceToRentChannelResponse
      ): protocol.Event.CommandResponse.GenerateInvoiceToRentChannelResponse = {
        val currency = decodeCurrency(proto.currency)
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        val capacity = satoshisCodec.decode(proto.capacity.getOrThrow("Invalid capacity"))
        val lifeTimeSeconds = proto.lifetimeSeconds
        val channelPayment = ChannelFeePayment(currency, payingCurrency, capacity, lifeTimeSeconds, Satoshis.Zero)
        val paymentRequest = proto.paymentRequest
        protocol.Event.CommandResponse
          .GenerateInvoiceToRentChannelResponse(channelPayment, paymentRequest)
      }

      override def encode(
          model: protocol.Event.CommandResponse.GenerateInvoiceToRentChannelResponse
      ): protos.commands.GenerateInvoiceToRentChannelResponse = {
        val channelPayment = model.channelFeePayment
        protos.commands
          .GenerateInvoiceToRentChannelResponse()
          .withCurrency(channelPayment.currency.toString)
          .withPayingCurrency(channelPayment.payingCurrency.toString)
          .withCapacity(satoshisCodec.encode(channelPayment.capacity))
          .withLifetimeSeconds(channelPayment.lifeTimeSeconds)
          .withPaymentRequest(model.paymentRequest)
      }
    }

  implicit val rentChannelResponseCodec: RentChannelResponseCodec =
    new RentChannelResponseCodec {
      override def decode(
          proto: protos.commands.RentChannelResponse
      ): protocol.Event.CommandResponse.RentChannelResponse = {
        val paymentRHash = PaymentRHash.untrusted(proto.paymentHash.toByteArray).getOrThrow("Invalid paymentHash")
        val channel = proto.channel.getOrThrow("Invalid channel")

        channel.value match {
          case protos.models.RentedChannel.Value.LndChannel(channel) =>
            val nodePubKey = Identifier.LndPublicKey
              .untrusted(channel.nodePublicKey.toByteArray)
              .getOrThrow("Invalid node public key")
            val channelId = ChannelId.LndChannelId.from(channel.channelId).getOrThrow("Invalid channel id")
            val fundingTxidBytes = LndTxid.fromLnd(channel.fundingTransaction.toByteArray)
            val outPoint = LndOutpoint(fundingTxidBytes, channel.outputIndex)

            protocol.Event.CommandResponse.RentChannelResponse(paymentRHash, nodePubKey, channelId, outPoint)

          case protos.models.RentedChannel.Value.ConnextChannel(channel) =>
            val nodePublicIdentifier = Identifier.ConnextPublicIdentifier
              .untrusted(channel.nodePublicIdentifier)
              .getOrThrow("Invalid node public key")
            val channelId = ChannelId.ConnextChannelId.from(channel.channelId).getOrThrow("Invalid channel id")
            val address = ConnextChannelAddress(channel.channelAddress).getOrThrow("invalid channel address")

            protocol.Event.CommandResponse.RentChannelResponse(paymentRHash, nodePublicIdentifier, channelId, address)

          case protos.models.RentedChannel.Value.Empty =>
            throw new RuntimeException("Invalid channel")
        }
      }

      override def encode(
          model: protocol.Event.CommandResponse.RentChannelResponse
      ): protos.commands.RentChannelResponse = {
        (model.clientIdentifier, model.channelIdentifier) match {
          case (Identifier.LndPublicKey(publicKey), LndOutpoint(txid, index)) =>
            val value = protos.models
              .LndChannel()
              .withFundingTransaction(ByteString.copyFrom(txid.lndBytes))
              .withOutputIndex(index)
              .withFundingTxidStr(txid.toString)
              .withNodePublicKey(ByteString.copyFrom(publicKey.toArray))
              .withChannelId(model.channelId.toString)

            val channel = protos.models.RentedChannel.Value.LndChannel(value)

            protos.commands
              .RentChannelResponse()
              .withPaymentHash(ByteString.copyFrom(model.paymentHash.value.toArray))
              .withNodePublicKey(ByteString.copyFrom(publicKey.toArray))
              .withChannelId(model.channelId.toString)
              .withFundingTransaction(ByteString.copyFrom(txid.lndBytes))
              .withOutputIndex(index)
              .withFundingTxidStr(txid.toString)
              .withChannel(protos.models.RentedChannel().withValue(channel))

          case (Identifier.ConnextPublicIdentifier(publicIdentifier), address: ConnextChannelAddress) =>
            val value = protos.models
              .ConnextChannel()
              .withNodePublicIdentifier(publicIdentifier)
              .withChannelId(model.channelId.toString)
              .withChannelAddress(address.toString)

            val channel = protos.models.RentedChannel.Value.ConnextChannel(value)

            protos.commands
              .RentChannelResponse()
              .withPaymentHash(ByteString.copyFrom(model.paymentHash.value.toArray))
              .withChannel(protos.models.RentedChannel().withValue(channel))

          case _ =>
            throw new RuntimeException("Invalid RentChannelResponse")
        }
      }
    }

  implicit val getChannelStatusResponseCodec: GetChannelStatusResponseCodec =
    new GetChannelStatusResponseCodec {
      override def decode(
          proto: protos.commands.GetChannelStatusResponse
      ): protocol.Event.CommandResponse.GetChannelStatusResponse = {
        val channelId = ChannelId.LndChannelId.from(proto.channelId).getOrThrow("Invalid channel id")

        proto.status match {
          case protos.commands.GetChannelStatusResponse.Status.Empty =>
            throw new RuntimeException("Missing or invalid channel status value")

          case protos.commands.GetChannelStatusResponse.Status.Lnd(value) =>
            val status = protocol.Event.CommandResponse.ChannelStatus.Lnd(
              status = ChannelStatus.withNameInsensitiveOption(value.status).getOrThrow("Invalid channel status"),
              expiresAt = Option.when(value.expiresAt > 0)(Instant.ofEpochSecond(value.expiresAt)),
              closingType = Option.unless(value.closingType.isEmpty)(value.closingType),
              closedBy = Option.unless(value.closedBy.isEmpty)(value.closedBy),
              closedOn = Option.when(value.closedOn > 0)(Instant.ofEpochSecond(value.closedOn))
            )

            protocol.Event.CommandResponse.GetChannelStatusResponse(channelId, status)

          case protos.commands.GetChannelStatusResponse.Status.Connext(value) =>
            val status = protocol.Event.CommandResponse.ChannelStatus.Connext(
              status = ConnextChannelStatus.withNameInsensitiveOption(value.status).getOrThrow("Invalid channel status"),
              expiresAt = Option.when(value.expiresAt > 0)(Instant.ofEpochSecond(value.expiresAt))
            )

            protocol.Event.CommandResponse.GetChannelStatusResponse(channelId, status)
        }
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetChannelStatusResponse
      ): protos.commands.GetChannelStatusResponse = {
        val status = model.status match {
          case s: protocol.Event.CommandResponse.ChannelStatus.Lnd =>
            val expiresAt: Long = s.expiresAt.map(_.getEpochSecond).getOrElse(0L)
            val closingType = s.closingType.getOrElse("")
            val closedBy = s.closedBy.getOrElse("")
            val closedOn = s.closedOn.map(_.getEpochSecond).getOrElse(0L)

            val value = protos.models
              .LndChannelStatus()
              .withStatus(s.status.entryName)
              .withExpiresAt(expiresAt)
              .withClosingType(closingType)
              .withClosedBy(closedBy)
              .withClosedOn(closedOn)

            protos.commands.GetChannelStatusResponse.Status.Lnd(value)

          case s: protocol.Event.CommandResponse.ChannelStatus.Connext =>
            val expiresAt: Long = s.expiresAt.map(_.getEpochSecond).getOrElse(0L)

            val value = protos.models
              .ConnextChannelStatus()
              .withStatus(s.status.entryName)
              .withExpiresAt(expiresAt)

            protos.commands.GetChannelStatusResponse.Status.Connext(value)
        }

        protos.commands
          .GetChannelStatusResponse()
          .withChannelId(model.channelId.toString)
          .withStatus(status)
      }
    }

  implicit val getFeeToRentChannelResponseCodec: GetFeeToRentChannelResponseCodec =
    new GetFeeToRentChannelResponseCodec {
      override def decode(
          proto: protos.commands.GetFeeToRentChannelResponse
      ): protocol.Event.CommandResponse.GetFeeToRentChannelResponse = {

        val fee = satoshisCodec.decode(proto.fee.getOrThrow("Invalid fee"))
        val rentingFee = satoshisCodec.decode(proto.rentingFee.getOrThrow("Invalid renting fee"))
        val onChainFees = satoshisCodec.decode(proto.onChainFees.getOrThrow("Invalid on chain fees"))

        protocol.Event.CommandResponse.GetFeeToRentChannelResponse(
          fee = fee,
          rentingFee = rentingFee,
          onChainFees = onChainFees
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetFeeToRentChannelResponse
      ): protos.commands.GetFeeToRentChannelResponse = {
        protos.commands
          .GetFeeToRentChannelResponse()
          .withFee(satoshisCodec.encode(model.fee))
          .withRentingFee(satoshisCodec.encode(model.rentingFee))
          .withOnChainFees(satoshisCodec.encode(model.onChainFees))
      }
    }

  implicit val refundFeeResponseCodec: RefundFeeResponseCodec =
    new RefundFeeResponseCodec {
      override def decode(
          proto: protos.commands.RefundFeeResponse
      ): protocol.Event.CommandResponse.RefundFeeResponse = {
        val currency = decodeCurrency(proto.currency)
        val amount = satoshisCodec.decode(proto.amount.getOrThrow("Invalid"))
        val refundedOn = Instant.ofEpochSecond(proto.refundedOn)
        val refundedFees = proto.refundedFees.map(refundablePaymentCodec.decode).toList

        protocol.Event.CommandResponse.RefundFeeResponse(
          currency,
          amount,
          refundedFees,
          refundedOn
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.RefundFeeResponse
      ): protos.commands.RefundFeeResponse = {

        protos.commands
          .RefundFeeResponse()
          .withCurrency(model.currency.entryName)
          .withAmount(satoshisCodec.encode(model.amount))
          .withRefundedFees(model.refundedFees.map(refundablePaymentCodec.encode))
          .withRefundedOn(model.refundedOn.getEpochSecond)
      }
    }

  implicit val getRefundableAmountResponseCodec: GetRefundableAmountResponseCodec =
    new GetRefundableAmountResponseCodec {
      override def decode(
          proto: protos.commands.GetRefundableAmountResponse
      ): protocol.Event.CommandResponse.GetRefundableAmountResponse = {
        val currency = decodeCurrency(proto.currency)
        val amount = satoshisCodec.decode(proto.amount.getOrThrow("Invalid"))
        protocol.Event.CommandResponse.GetRefundableAmountResponse(
          currency,
          amount
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetRefundableAmountResponse
      ): protos.commands.GetRefundableAmountResponse = {

        protos.commands
          .GetRefundableAmountResponse()
          .withCurrency(model.currency.entryName)
          .withAmount(satoshisCodec.encode(model.amount))
      }
    }

  implicit val getFeeToExtendRentedChannelResponseCodec: GetFeeToExtendRentedChannelResponseCodec =
    new GetFeeToExtendRentedChannelResponseCodec {
      override def decode(
          proto: protos.commands.GetFeeToExtendRentedChannelResponse
      ): protocol.Event.CommandResponse.GetFeeToExtendRentedChannelResponse = {
        val fee = satoshisCodec.decode(proto.fee.getOrThrow("Invalid fee"))
        protocol.Event.CommandResponse.GetFeeToExtendRentedChannelResponse(fee)
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetFeeToExtendRentedChannelResponse
      ): protos.commands.GetFeeToExtendRentedChannelResponse = {

        protos.commands
          .GetFeeToExtendRentedChannelResponse()
          .withFee(satoshisCodec.encode(model.fee))
      }
    }

  implicit val generateInvoiceToExtendRentedChannelResponseCodec: GenerateInvoiceToExtendRentedChannelResponseCodec =
    new GenerateInvoiceToExtendRentedChannelResponseCodec {
      override def decode(
          proto: protos.commands.GenerateInvoiceToExtendRentedChannelResponse
      ): protocol.Event.CommandResponse.GenerateInvoiceToExtendRentedChannelResponse = {
        protocol.Event.CommandResponse.GenerateInvoiceToExtendRentedChannelResponse(
          ChannelId.LndChannelId.from(proto.channelId).getOrThrow("Invalid channel id"),
          decodeCurrency(proto.payingCurrency),
          proto.lifetimeSeconds,
          proto.paymentRequest
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.GenerateInvoiceToExtendRentedChannelResponse
      ): protos.commands.GenerateInvoiceToExtendRentedChannelResponse = {

        protos.commands
          .GenerateInvoiceToExtendRentedChannelResponse()
          .withChannelId(model.channelId.value.toString)
          .withPayingCurrency(model.payingCurrency.entryName)
          .withLifetimeSeconds(model.lifetimeSeconds)
          .withPaymentRequest(model.paymentRequest)
      }
    }

  implicit val generatePaymentHashToExtendConnextRentedChannelResponseCodec
      : GeneratePaymentHashToExtendConnextRentedChannelResponseCodec =
    new GeneratePaymentHashToExtendConnextRentedChannelResponseCodec {
      override def decode(
          proto: protos.commands.GeneratePaymentHashToExtendConnextRentedChannelResponse
      ): protocol.Event.CommandResponse.GeneratePaymentHashToExtendConnextRentedChannelResponse = {
        protocol.Event.CommandResponse.GeneratePaymentHashToExtendConnextRentedChannelResponse(
          ChannelId.ConnextChannelId.from(proto.channelId).getOrThrow("Invalid channel id"),
          decodeCurrency(proto.payingCurrency),
          proto.lifetimeSeconds,
          paymentRHashCodec.decode(proto.paymentHash)
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.GeneratePaymentHashToExtendConnextRentedChannelResponse
      ): protos.commands.GeneratePaymentHashToExtendConnextRentedChannelResponse = {

        protos.commands
          .GeneratePaymentHashToExtendConnextRentedChannelResponse()
          .withChannelId(model.channelId.value.toString)
          .withPayingCurrency(model.payingCurrency.entryName)
          .withLifetimeSeconds(model.lifetimeSeconds)
          .withPaymentHash(paymentRHashCodec.encode(model.paymentHash))
      }
    }

  implicit val extendRentedChannelTimeResponseCodec: ExtendRentedChannelTimeResponseCodec =
    new ExtendRentedChannelTimeResponseCodec {
      override def decode(
          proto: protos.commands.ExtendRentedChannelTimeResponse
      ): protocol.Event.CommandResponse.ExtendRentedChannelTimeResponse = {
        protocol.Event.CommandResponse.ExtendRentedChannelTimeResponse(
          paymentRHashCodec.decode(proto.paymentHash),
          ChannelId.LndChannelId.from(proto.channelId).getOrThrow("Invalid channel id"),
          proto.expiresAt
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.ExtendRentedChannelTimeResponse
      ): protos.commands.ExtendRentedChannelTimeResponse = {

        protos.commands
          .ExtendRentedChannelTimeResponse()
          .withPaymentHash(paymentRHashCodec.encode(model.paymentHash))
          .withChannelId(model.channelId.toString)
          .withExpiresAt(model.lifeTimeSeconds)
      }
    }

  implicit val registerPublicKeyResponseCodec: RegisterPublicKeyResponseCodec =
    new RegisterPublicKeyResponseCodec {
      override def decode(
          proto: protos.commands.RegisterPublicKeyResponse
      ): protocol.Event.CommandResponse.RegisterPublicKeyResponse = {
        val publicKey = Identifier.LndPublicKey.untrusted(proto.publicKey.toByteArray).getOrThrow("Invalid public key")

        protocol.Event.CommandResponse.RegisterPublicKeyResponse(
          decodeCurrency(proto.currency),
          publicKey
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.RegisterPublicKeyResponse
      ): protos.commands.RegisterPublicKeyResponse = {

        protos.commands
          .RegisterPublicKeyResponse()
          .withCurrency(model.currency.entryName)
          .withPublicKey(ByteString.copyFrom(model.nodePublicKey.value.toArray))
      }
    }

  implicit val registerPublicIdentifierResponseCodec: RegisterPublicIdentifierResponseCodec =
    new RegisterPublicIdentifierResponseCodec {
      override def decode(
          proto: protos.commands.RegisterPublicIdentifierResponse
      ): protocol.Event.CommandResponse.RegisterPublicIdentifierResponse = {
        val publicIdentifier = ConnextPublicIdentifier
          .untrusted(proto.publicIdentifier)
          .getOrThrow("Invalid public identifier")

        protocol.Event.CommandResponse.RegisterPublicIdentifierResponse(
          decodeCurrency(proto.currency),
          publicIdentifier
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.RegisterPublicIdentifierResponse
      ): protos.commands.RegisterPublicIdentifierResponse = {
        protos.commands
          .RegisterPublicIdentifierResponse()
          .withCurrency(model.currency.entryName)
          .withPublicIdentifier(model.publicIdentifier.value)
      }
    }

  implicit val getConnextPaymentInformationResponseCodec: GetConnextPaymentInformationResponseCodec =
    new GetConnextPaymentInformationResponseCodec {
      override def decode(
          proto: protos.commands.GetConnextPaymentInformationResponse
      ): protocol.Event.CommandResponse.GetConnextPaymentInformationResponse = {
        val paymentHash = Option(proto.paymentHash).filterNot(_.isEmpty).map(paymentRHashCodec.decode)

        protocol.Event.CommandResponse.GetConnextPaymentInformationResponse(
          currency = decodeCurrency(proto.currency),
          noFeeRequired = proto.noFeeRequired,
          publicIdentifier = proto.publicIdentifier,
          paymentHash = paymentHash
        )
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetConnextPaymentInformationResponse
      ): protos.commands.GetConnextPaymentInformationResponse = {
        val paymentHash = model.paymentHash.map(_.value.toArray).map(ByteString.copyFrom).getOrElse(ByteString.EMPTY)

        protos.commands
          .GetConnextPaymentInformationResponse()
          .withCurrency(model.currency.entryName)
          .withNoFeeRequired(model.noFeeRequired)
          .withPublicIdentifier(model.publicIdentifier)
          .withPaymentHash(paymentHash)
      }
    }

  implicit val generatePaymentHashToRentChannelResponseCodec: GeneratePaymentHashToRentChannelResponseCodec =
    new GeneratePaymentHashToRentChannelResponseCodec {
      override def decode(
          proto: protos.commands.GeneratePaymentHashToRentChannelResponse
      ): protocol.Event.CommandResponse.GeneratePaymentHashToRentChannelResponse = {
        val currency = decodeCurrency(proto.currency)
        val payingCurrency = decodeCurrency(proto.payingCurrency)
        val capacity = satoshisCodec.decode(proto.capacity.getOrThrow("Invalid capacity"))
        val lifeTimeSeconds = proto.lifetimeSeconds
        val channelPayment = ChannelFeePayment(currency, payingCurrency, capacity, lifeTimeSeconds, Satoshis.Zero)
        val paymentHash = paymentRHashCodec.decode(proto.paymentHash)

        protocol.Event.CommandResponse.GeneratePaymentHashToRentChannelResponse(channelPayment, paymentHash)
      }

      override def encode(
          model: protocol.Event.CommandResponse.GeneratePaymentHashToRentChannelResponse
      ): protos.commands.GeneratePaymentHashToRentChannelResponse = {
        val channelPayment = model.channelFeePayment
        val paymentHash = paymentRHashCodec.encode(model.paymentHash)

        protos.commands
          .GeneratePaymentHashToRentChannelResponse()
          .withCurrency(channelPayment.currency.toString)
          .withPayingCurrency(channelPayment.payingCurrency.toString)
          .withCapacity(satoshisCodec.encode(channelPayment.capacity))
          .withLifetimeSeconds(channelPayment.lifeTimeSeconds)
          .withPaymentHash(paymentHash)
      }
    }

  implicit val registerConnextChannelContractDeploymentFeeResponseCodec
      : RegisterConnextChannelContractDeploymentFeeResponseCodec =
    new RegisterConnextChannelContractDeploymentFeeResponseCodec {
      override def decode(
          proto: protos.commands.RegisterConnextChannelContractDeploymentFeeResponse
      ): protocol.Event.CommandResponse.RegisterConnextChannelContractDeploymentFeeResponse = {
        protocol.Event.CommandResponse.RegisterConnextChannelContractDeploymentFeeResponse(proto.transactionHash)
      }

      override def encode(
          model: protocol.Event.CommandResponse.RegisterConnextChannelContractDeploymentFeeResponse
      ): protos.commands.RegisterConnextChannelContractDeploymentFeeResponse = {
        protos.commands
          .RegisterConnextChannelContractDeploymentFeeResponse()
          .withTransactionHash(model.transactionHash)
      }
    }

  implicit val getConnextChannelContractDeploymentFeeResponseCodec
      : GetConnextChannelContractDeploymentFeeResponseCodec =
    new GetConnextChannelContractDeploymentFeeResponseCodec {
      override def decode(
          proto: protos.commands.GetConnextChannelContractDeploymentFeeResponse
      ): protocol.Event.CommandResponse.GetConnextChannelContractDeploymentFeeResponse = {
        val amount = satoshisCodec.decode(proto.amount.getOrThrow("missing amount"))

        protocol.Event.CommandResponse.GetConnextChannelContractDeploymentFeeResponse(proto.hubAddress, amount)
      }

      override def encode(
          model: protocol.Event.CommandResponse.GetConnextChannelContractDeploymentFeeResponse
      ): protos.commands.GetConnextChannelContractDeploymentFeeResponse = {
        protos.commands
          .GetConnextChannelContractDeploymentFeeResponse()
          .withHubAddress(model.hubAddress)
          .withAmount(satoshisCodec.encode(model.amount))
      }
    }

  implicit val commandResponseCodec: CommandResponseCodec = new CommandResponseCodec {
    override def decode(proto: protos.api.Event.CommandResponse): TaggedCommandResponse = {
      val response: protocol.Event.CommandResponse = proto.value match {
        case protos.api.Event.CommandResponse.Value.Empty =>
          throw new RuntimeException("Missing or invalid command response")
        case protos.api.Event.CommandResponse.Value.CommandFailed(value) => commandFailedCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.PingResponse(value) => pingResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetTradingPairsResponse(value) =>
          getTradingPairsResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.SubscribeResponse(value) => subscribeResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.UnsubscribeResponse(value) => unsubscribeResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetOpenOrdersResponse(value) =>
          getOpenOrdersResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetHistoricTradesResponse(value) =>
          getHistoricTradesResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetBarsPricesResponse(value) =>
          getBarsPricesResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.PlaceOrderResponse(value) => placeOrderResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetOpenOrderByIdResponse(value) =>
          getOpenOrderByIdResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.CancelOrderResponse(value) =>
          cancelOpenOrderResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.SendOrderMessageResponse(value) =>
          sendOrderMessageResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.CancelMatchedOrderResponse(value) =>
          cancelMatchedOrderResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.CleanTradingPairOrdersResponse(value) =>
          cleanTradingPairOrdersResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetLndPaymentInvoiceResponse(value) =>
          getLndPaymentInvoiceResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GenerateInvoiceToRentChannelResponse(value) =>
          generateInvoiceToRentChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.RentChannelResponse(value) =>
          rentChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetChannelStatusResponse(value) =>
          getChannelStatusResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetFeeToRentChannelResponse(value) =>
          getFeeToRentChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.RefundFeeResponse(value) =>
          refundFeeResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetRefundableAmountResponse(value) =>
          getRefundableAmountResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetFeeToExtendRentedChannelResponse(value) =>
          getFeeToExtendRentedChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GenerateInvoiceToExtendRentedChannelResponse(value) =>
          generateInvoiceToExtendRentedChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GeneratePaymentHashToExtendConnextRentedChannelResponse(value) =>
          generatePaymentHashToExtendConnextRentedChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.ExtendRentedChannelTimeResponse(value) =>
          extendRentedChannelTimeResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.RegisterPublicKeyResponse(value) =>
          registerPublicKeyResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.RegisterPublicIdentifierResponse(value) =>
          registerPublicIdentifierResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetConnextPaymentInformationResponse(value) =>
          getConnextPaymentInformationResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GeneratePaymentHashToRentChannelResponse(value) =>
          generatePaymentHashToRentChannelResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.RegisterConnextChannelContractDeploymentFeeResponse(value) =>
          registerConnextChannelContractDeploymentFeeResponseCodec.decode(value)
        case protos.api.Event.CommandResponse.Value.GetConnextChannelContractDeploymentFeeResponse(value) =>
          getConnextChannelContractDeploymentFeeResponseCodec.decode(value)
      }

      TaggedCommandResponse(proto.clientMessageId, response)
    }

    override def encode(model: TaggedCommandResponse): protos.api.Event.CommandResponse = {
      val response: protos.api.Event.CommandResponse.Value = model.value match {
        case r: CommandResponse.CommandFailed =>
          val event = commandFailedCodec.encode(r)
          protos.api.Event.CommandResponse.Value.CommandFailed(event)
        case r: CommandResponse.PingResponse =>
          val event = pingResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.PingResponse(event)
        case r: CommandResponse.GetTradingPairsResponse =>
          val event = getTradingPairsResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetTradingPairsResponse(event)
        case r: CommandResponse.SubscribeResponse =>
          val event = subscribeResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.SubscribeResponse(event)
        case r: CommandResponse.UnsubscribeResponse =>
          val event = unsubscribeResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.UnsubscribeResponse(event)
        case r: CommandResponse.GetOpenOrdersResponse =>
          val event = getOpenOrdersResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetOpenOrdersResponse(event)
        case r: CommandResponse.GetHistoricTradesResponse =>
          val event = getHistoricTradesResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetHistoricTradesResponse(event)
        case r: CommandResponse.GetBarsPricesResponse =>
          val event = getBarsPricesResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetBarsPricesResponse(event)
        case r: CommandResponse.PlaceOrderResponse =>
          val event = placeOrderResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.PlaceOrderResponse(event)
        case r: CommandResponse.GetOpenOrderByIdResponse =>
          val event = getOpenOrderByIdResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetOpenOrderByIdResponse(event)
        case r: CommandResponse.CancelOpenOrderResponse =>
          val event = cancelOpenOrderResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.CancelOrderResponse(event)
        case r: CommandResponse.CancelMatchedOrderResponse =>
          val event = cancelMatchedOrderResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.CancelMatchedOrderResponse(event)
        case r: CommandResponse.SendOrderMessageResponse =>
          val event = sendOrderMessageResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.SendOrderMessageResponse(event)
        case r: CommandResponse.CleanTradingPairOrdersResponse =>
          val event = cleanTradingPairOrdersResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.CleanTradingPairOrdersResponse(event)
        case r: CommandResponse.GetInvoicePaymentResponse =>
          val event = getLndPaymentInvoiceResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetLndPaymentInvoiceResponse(event)
        case r: CommandResponse.GenerateInvoiceToRentChannelResponse =>
          val event = generateInvoiceToRentChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GenerateInvoiceToRentChannelResponse(event)
        case r: CommandResponse.RentChannelResponse =>
          val event = rentChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.RentChannelResponse(event)
        case r: CommandResponse.GetChannelStatusResponse =>
          val event = getChannelStatusResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetChannelStatusResponse(event)
        case r: CommandResponse.GetFeeToRentChannelResponse =>
          val event = getFeeToRentChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetFeeToRentChannelResponse(event)
        case r: CommandResponse.RefundFeeResponse =>
          val event = refundFeeResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.RefundFeeResponse(event)
        case r: CommandResponse.GetRefundableAmountResponse =>
          val event = getRefundableAmountResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetRefundableAmountResponse(event)
        case r: CommandResponse.GetFeeToExtendRentedChannelResponse =>
          val event = getFeeToExtendRentedChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetFeeToExtendRentedChannelResponse(event)
        case r: CommandResponse.GenerateInvoiceToExtendRentedChannelResponse =>
          val event = generateInvoiceToExtendRentedChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GenerateInvoiceToExtendRentedChannelResponse(event)
        case r: CommandResponse.GeneratePaymentHashToExtendConnextRentedChannelResponse =>
          val event = generatePaymentHashToExtendConnextRentedChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GeneratePaymentHashToExtendConnextRentedChannelResponse(event)
        case r: CommandResponse.ExtendRentedChannelTimeResponse =>
          val event = extendRentedChannelTimeResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.ExtendRentedChannelTimeResponse(event)
        case r: CommandResponse.RegisterPublicKeyResponse =>
          val event = registerPublicKeyResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.RegisterPublicKeyResponse(event)
        case r: CommandResponse.RegisterPublicIdentifierResponse =>
          val event = registerPublicIdentifierResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.RegisterPublicIdentifierResponse(event)
        case r: CommandResponse.GetConnextPaymentInformationResponse =>
          val event = getConnextPaymentInformationResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetConnextPaymentInformationResponse(event)
        case r: CommandResponse.GeneratePaymentHashToRentChannelResponse =>
          val event = generatePaymentHashToRentChannelResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GeneratePaymentHashToRentChannelResponse(event)
        case r: CommandResponse.RegisterConnextChannelContractDeploymentFeeResponse =>
          val event = registerConnextChannelContractDeploymentFeeResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.RegisterConnextChannelContractDeploymentFeeResponse(event)
        case r: CommandResponse.GetConnextChannelContractDeploymentFeeResponse =>
          val event = getConnextChannelContractDeploymentFeeResponseCodec.encode(r)
          protos.api.Event.CommandResponse.Value.GetConnextChannelContractDeploymentFeeResponse(event)
      }
      protos.api.Event.CommandResponse(model.requestId, response)
    }
  }
}
