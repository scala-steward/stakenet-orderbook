package io.stakenet.orderbook.actors.peers

import java.time.Instant
import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers.PeerActor.InternalMessage
import io.stakenet.orderbook.actors.peers.protocol.Event
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse
import io.stakenet.orderbook.actors.peers.protocol.Event.CommandResponse._
import io.stakenet.orderbook.config.{ChannelRentalConfig, FeatureFlags}
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.Identifier.ConnextPublicIdentifier
import io.stakenet.orderbook.models.clients.{ClientId, ClientIdentifier, Identifier}
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.reports.PartialOrder
import io.stakenet.orderbook.models.trading.{Trade, TradingOrder, TradingPair}
import io.stakenet.orderbook.services.FeeService.Errors._
import io.stakenet.orderbook.services._
import io.stakenet.orderbook.services.validators.OrderValidator
import io.stakenet.orderbook.services.validators.OrderValidator.Error._
import io.stakenet.orderbook.utils.Extensions._
import javax.inject.Inject
import kamon.Kamon

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[peers] class PeerActorOps @Inject() (
    orderManager: OrderManagerActor.Ref,
    orderValidator: OrderValidator,
    feeService: FeeService,
    paymentService: PaymentService,
    featureFlags: FeatureFlags,
    channelService: ChannelService,
    discordHelper: DiscordHelper,
    clientService: ClientService,
    makerPaymentService: MakerPaymentService,
    connextHelper: ConnextHelper,
    channelRentalConfig: ChannelRentalConfig
)(implicit ec: ExecutionContext) {

  private def trace[A](operation: String)(block: => A): A = {
    val timer = Kamon.timer(operation).withoutTags().start()

    val result = block

    timer.stop()

    result
  }

  private def traceAsync[A](operation: String)(block: => Future[A]): Future[A] = {
    val timer = Kamon.timer(operation).withoutTags().start()

    val result = block

    result.onComplete(_ => timer.stop())

    result
  }

  def generateOrderFeeInvoice(currency: Currency, amount: Satoshis): Future[CommandResponse] = {
    if (featureFlags.feesEnabled) {
      if (Currency.forLnd.contains(currency)) {
        feeService.createInvoice(currency, amount).map {
          case Right(invoice) =>
            Event.CommandResponse.GetInvoicePaymentResponse(currency, noFeeRequired = false, Some(invoice))
          case Left(error) => CommandFailed(error)
        }
      } else {
        Future.successful(CommandFailed(s"$currency not supported"))
      }
    } else {
      val response =
        Event.CommandResponse.GetInvoicePaymentResponse(currency, noFeeRequired = true, paymentRequest = None)
      Future.successful(response)
    }
  }

  def getConnextPaymentInformation(currency: Currency): Future[CommandResponse] = {
    val publicIdentifier = connextHelper.getPublicIdentifier(currency)

    if (featureFlags.feesEnabled) {
      paymentService.generatePaymentHash(currency).map { paymentHash =>
        GetConnextPaymentInformationResponse(currency, noFeeRequired = false, publicIdentifier, Some(paymentHash))
      }
    } else {
      Future.successful(
        GetConnextPaymentInformationResponse(currency, noFeeRequired = true, publicIdentifier, None)
      )
    }
  }

  def placeOrder(order: TradingOrder, paymentHash: Option[PaymentRHash], clientId: ClientId, self: ActorRef)(implicit
      state: PeerState,
      peerUser: PeerUser,
      timeout: Timeout
  ): Future[Event.CommandResponse] = traceAsync("placeOrder") {
    def validateAmountOfOrders: Either[Event.CommandResponse.PlaceOrderResponse, Unit] =
      trace("validateAmountOfOrders") {
        if (state.countActiveOrders >= peerUser.maxAllowedOrders) {
          val message =
            s"You aren't allowed to place more than ${peerUser.maxAllowedOrders} orders, if you need to do so, contact the admins"
          val response = Event.CommandResponse.PlaceOrderResponse(results.PlaceOrderResult.OrderRejected(message))
          Left(response)
        } else {
          Right(())
        }
      }

    def validatePaymentProvided: Either[Event.CommandResponse.PlaceOrderResponse, Unit] =
      trace("validatePaymentProvided") {
        (featureFlags.feesEnabled, TradingPair.feeless.contains(order.pair), paymentHash) match {
          case (true, false, None) =>
            val message = "A fee is required but no payment was provided"
            val left = Event.CommandResponse.PlaceOrderResponse(results.PlaceOrderResult.OrderRejected(message))
            Left(left)
          case _ => Right(())
        }
      }

    def validateOrder: Future[Either[Event.CommandResponse.PlaceOrderResponse, Unit]] = traceAsync("validateOrder") {
      orderValidator
        .validate(order)
        .map { result =>
          result.left
            .map {
              case InvalidFunds(funds, interval) =>
                s"The $funds funds provided are outside the accepted range [${interval.from}, ${interval.to}]"
              case InvalidPrice(price, interval) =>
                s"The $price price provided is outside the accepted range [${interval.from}, ${interval.to}]"
              case MaxValueExceeded() =>
                "Order value too large"
              case CouldNotVerifyOrderValue() =>
                "There was a problem verifying the value of the order, please try again later."
            }
            .left
            .map { message =>
              Event.CommandResponse.PlaceOrderResponse(results.PlaceOrderResult.OrderRejected(message))
            }
        }
    }

    def takeFeeIfNecessary() = traceAsync("takeFeeIfNecessary") {
      (featureFlags.feesEnabled, TradingPair.feeless.contains(order.pair), paymentHash) match {
        case (true, false, None) =>
          Future.failed(new RuntimeException("A fee is required but no payment was provided"))
        case (true, false, Some(hash)) =>
          feeService.takeFee(clientId, order, hash).map {
            case Right(result) => result
            case Left(CouldNotTakeFee(reason)) => throw new RuntimeException(reason)
          }
        case _ =>
          Future.unit
      }
    }

    def internalPlaceOrder(): Future[Event.CommandResponse.PlaceOrderResponse] = traceAsync("internalPlaceOrder") {
      val f = for {
        _ <- takeFeeIfNecessary()
        x <- orderManager.ref ? OrderManagerActor.Command.PlaceOrder(order, clientId, self)
      } yield x match {
        case e: OrderManagerActor.Event.MyOrderMatched =>
          self ! InternalMessage.OrderMatched(e.trade, e.orderMatchedWith)
          e
        case e: OrderManagerActor.Event.OrderPlaced =>
          self ! InternalMessage.OrderPlaced(e.order)
          e
        case e => e
      }

      f.collect {
        case e: OrderManagerActor.Event.MyOrderMatched =>
          Event.CommandResponse.PlaceOrderResponse(
            results.PlaceOrderResult.OrderMatched(e.trade, e.orderMatchedWith.order)
          )
        case e: OrderManagerActor.Event.OrderPlaced =>
          Event.CommandResponse.PlaceOrderResponse(results.PlaceOrderResult.OrderPlaced(e.order))
        case e: Event.CommandResponse.CommandFailed.Reason =>
          Event.CommandResponse.PlaceOrderResponse(results.PlaceOrderResult.OrderRejected(e.reason))
      }
    }

    val result = for {
      _ <- validateAmountOfOrders.toFutureEither()
      _ <- validateOrder.toFutureEither()
      _ <- validatePaymentProvided.toFutureEither()
    } yield internalPlaceOrder()

    result.toFuture.flatMap {
      case Left(value) => Future.successful(value)
      case Right(value) => value
    }
  }

  def tryCancelingPayment(orderId: OrderId)(log: LoggingAdapter)(implicit peerUser: PeerUser): Unit = {
    feeService.unlink(orderId).onComplete {
      case Failure(ex) => log.error(ex, s"${peerUser.name}: Failed to unlink order from fee, order = $orderId")
      case Success(_) => log.info(s"${peerUser.name}: Payment canceled on order = $orderId")
    }
  }

  def updateSwapSuccess(peerTrade: PeerTrade, self: ActorRef)(
      log: LoggingAdapter
  )(implicit peerUser: PeerUser): Future[Unit] = {
    orderManager.ref ! OrderManagerActor.Command.UpdateSwapSuccess(peerTrade.trade)
    peerTrade.secondOrder.peer ! InternalMessage.SwapSuccess(peerTrade.trade.id)
    self ! InternalMessage.SwapSuccess(peerTrade.trade.id)

    // TODO: For the moment we will always try to burn fees because we currently don't know if the other peer taking
    //       part on the trade has to pay fees or not
    tryBurningFee(peerTrade)(log)
  }

  def updateSwapFailure(peerTrade: PeerTrade, self: ActorRef)(
      log: LoggingAdapter
  )(implicit peerUser: PeerUser): Future[Unit] = {
    orderManager.ref ! OrderManagerActor.Command.UpdateSwapFailure(peerTrade.trade)
    peerTrade.secondOrder.peer ! InternalMessage.SwapFailure(peerTrade.trade.id)
    self ! InternalMessage.SwapFailure(peerTrade.trade.id)

    // TODO: For the moment we will always try to unlink fees because we currently don't know if the other peer taking
    //       part on the trade paid a fee or not
    tryUnlinkingFees(peerTrade.trade)(log)
  }

  def tryBurningFee(peerTrade: PeerTrade)(log: LoggingAdapter)(implicit peerUser: PeerUser): Future[Unit] = {
    def unsafeBurnFee(orderId: OrderId, currency: Currency, amount: Satoshis): Future[Unit] = {
      val result = feeService.burn(orderId, currency, amount).transform {
        case Failure(ex) =>
          log.error(
            ex,
            s"${peerUser.name}: Failed to burn fee from order = $orderId, amount = ${amount.toString(currency)}"
          )
          Success(())

        case Success(_) =>
          log.info(s"${peerUser.name}: Fee burnt on $orderId, amount = ${amount.toString(currency)}")
          Success(())
      }

      result.foreach { _ =>
        // This is when the fee actually belongs to us as it becomes non-refundable
        // Kamon APM doesn't support doubles, hence, we need to log satoshis instead
        Kamon
          .gauge("revenue.orderFees")
          .withTag("currency", currency.entryName)
          .increment(amount.valueFor(currency).toDouble)
      }

      result
    }

    // Some orders aren't paying fees, before trying to burn them, we check whether a fee
    // actually exists, otherwise, there is no reason to burn them, as it will throw an error
    // polluting our logs with noisy information.
    // also we have to store the partial order which was traded, we need to do this here because the order id will be removed.
    def burnFeeIfExists(orderId: OrderId, ownerId: ClientId, currency: Currency, amount: Satoshis): Future[Unit] = {
      feeService.find(orderId, currency).flatMap {
        case Some(fee) =>
          savePartialOrder(orderId, ownerId, currency, amount, Some(fee.paymentRHash))
          unsafeBurnFee(orderId, currency, amount)
        case None =>
          savePartialOrder(orderId, ownerId, currency, amount, None)
          Future.unit
      }
    }

    def savePartialOrder(
        orderId: OrderId,
        ownerId: ClientId,
        currency: Currency,
        amount: Satoshis,
        paymentRHash: Option[PaymentRHash]
    ): Unit = {
      val partialOrder = PartialOrder(orderId, ownerId, paymentRHash, currency, amount, Instant.now())
      feeService.savePartialOrder(partialOrder).recover { case e =>
        log.error(e, s"Failed to create the partial order register $partialOrder")
      }
      ()
    }

    val clientId = peerUser match {
      case user: PeerUser.Bot => user.id
      case user: PeerUser.Wallet => user.id
      case _ => throw new RuntimeException(s"Invalid state, order owned by an invalid user($peerUser)")
    }

    val trade = peerTrade.trade

    val (buyingOrderOwnerId, sellingOrderOwnerId) = if (trade.buyingOrderId == peerTrade.secondOrder.orderId) {
      (peerTrade.secondOrder.clientId, clientId)
    } else {
      (clientId, peerTrade.secondOrder.clientId)
    }

    makerPaymentService.payMaker(clientId, peerTrade).transform {
      case Success(result) =>
        log.info(s"pay maker result $result")
        Success(())

      case Failure(error) =>
        log.error(error, "pay maker failed")
        Success(())
    }

    val burnResults = List(
      burnFeeIfExists(trade.buyingOrderId, buyingOrderOwnerId, trade.buyingCurrency, trade.buyOrderFunds),
      burnFeeIfExists(trade.sellingOrderId, sellingOrderOwnerId, trade.sellingCurrency, trade.size)
    )

    Future.sequence(burnResults).map(_ => ())
  }

  def tryUnlinkingFees(trade: Trade)(log: LoggingAdapter)(implicit peerUser: PeerUser): Future[Unit] = {
    val requests = trade.orders.map { orderId =>
      feeService.unlink(orderId).transform {
        case Failure(ex) =>
          log.error(ex, s"${peerUser.name}: Failed to unlink fee from order = $orderId")

          Success(())
        case Success(_) =>
          log.info(s"${peerUser.name}: Fees unlinked from $orderId")

          Success(())
      }
    }

    Future.sequence(requests).map(_ => ())
  }

  def generateInvoiceToRentChannel(channelFeePayment: ChannelFeePayment): Future[Event.CommandResponse] = {
    val validPayingCurrency = Currency.forLnd.contains(channelFeePayment.payingCurrency)

    if (validPayingCurrency) {
      val result = for {
        fees <- channelService.getFeeAmount(channelFeePayment).toFutureEither()
        _ <- channelService
          .validateChannelRentalCapacity(channelFeePayment.capacity, channelFeePayment.currency)
          .toFutureEither()

        memo = "Fee for renting channel"
        paymentRequest <- paymentService
          .createPaymentRequest(channelFeePayment.payingCurrency, fees.totalFee, memo)
          .map(_.left.map(_.reason))
          .toFutureEither()

        _ <- createChannelPayment(channelFeePayment, paymentRequest, fees).map(Right[String, Unit]).toFutureEither()
      } yield Event.CommandResponse.GenerateInvoiceToRentChannelResponse(channelFeePayment, paymentRequest)

      result.toFuture.map {
        case Right(response) => response
        case Left(error) => CommandFailed(error)
      }
    } else {
      Future.successful(CommandFailed(s"${channelFeePayment.payingCurrency} not supported"))
    }
  }

  def generateConnextRentPayment(channelFeePayment: ChannelFeePayment): Future[CommandResponse] = {
    val validPayingCurrency = !Currency.forLnd.contains(channelFeePayment.payingCurrency)

    if (validPayingCurrency) {
      val result = for {
        fees <- channelService.getFeeAmount(channelFeePayment).toFutureEither()

        paymentHash <- paymentService
          .generatePaymentHash(channelFeePayment.payingCurrency)
          .map(Right(_).withLeft[String])
          .toFutureEither()

        _ <- channelService
          .createChannelFeePayment(channelFeePayment, paymentHash, fees)
          .map(Right(_).withLeft[String])
          .toFutureEither()
      } yield GeneratePaymentHashToRentChannelResponse(channelFeePayment, paymentHash)

      result.toFuture.map {
        case Right(response) => response
        case Left(error) => CommandFailed(error)
      }
    } else {
      Future.successful(CommandFailed(s"${channelFeePayment.payingCurrency} not supported"))
    }
  }

  private def createChannelPayment(
      channelFeePayment: ChannelFeePayment,
      paymentRequest: String,
      fee: ChannelFees
  ): Future[Unit] = {
    for {
      paymentRHash <- paymentService.getPaymentHash(channelFeePayment.payingCurrency, paymentRequest)
      _ <- channelService.createChannelFeePayment(channelFeePayment, paymentRHash, fee)
    } yield ()
  }

  def rentChannel(
      clientId: ClientId,
      paymentRHash: PaymentRHash,
      payingCurrency: Currency
  ): Future[Event.CommandResponse] = {
    channelService.findChannel(paymentRHash, payingCurrency).flatMap {
      case Some(channel: Channel.LndChannel) =>
        val response = for {
          fundingTransaction <- channel.fundingTransaction
          outputIndex <- channel.outputIndex
          outpoint = ChannelIdentifier.LndOutpoint(fundingTransaction, outputIndex)
        } yield RentChannelResponse(paymentRHash, channel.publicKey, channel.channelId, outpoint)

        Future.successful(response.getOrElse(CommandFailed("Channel is already being opened")))

      case Some(channel: Channel.ConnextChannel) =>
        val response = channel.channelAddress.map { channelAddress =>
          RentChannelResponse(paymentRHash, channel.publicIdentifier, channel.channelId, channelAddress)
        }

        Future.successful(response.getOrElse(CommandFailed("Invalid state, channel without an address")))

      case None =>
        for {
          feePaymentMaybe <- channelService.findChannelFeePayment(paymentRHash, payingCurrency)
          feePayment = feePaymentMaybe.getOrElse(throw new RuntimeException("The payment hash does not exist"))

          clientIdentifierMaybe <- clientService.findClientIdentifier(clientId, feePayment.currency)
          clientIdentifier = clientIdentifierMaybe.getOrElse(
            throw new RuntimeException(s"You have no public identifier registered for ${feePayment.currency}")
          )

          // for connext channels clients need to pay this additional fee
          _ <- clientIdentifier match {
            case _: ClientIdentifier.ClientConnextPublicIdentifier =>
              channelService.findConnextChannelContractDeploymentFee(clientId).map {
                case Some(_) => ()
                case None => throw new RuntimeException(s"Channel contract fee not paid")
              }

            case _: ClientIdentifier.ClientLndPublicKey =>
              Future.unit
          }

          paymentDataMaybe <- feeService.getPaymentData(clientId, payingCurrency, paymentRHash)
          paymentData = paymentDataMaybe match {
            case Right(data) => data
            case Left(error) => throw new RuntimeException(error)
          }

          channel = clientIdentifier match {
            case clientPublicKey: ClientIdentifier.ClientLndPublicKey =>
              Channel.LndChannel.from(
                paymentRHash,
                feePayment.payingCurrency,
                clientPublicKey.key,
                clientPublicKey.clientPublicKeyId,
                lnd.ChannelStatus.Opening
              )

            case clientPublicIdentifier: ClientIdentifier.ClientConnextPublicIdentifier =>
              Channel.ConnextChannel.from(
                paymentRHash,
                feePayment.payingCurrency,
                clientPublicIdentifier.identifier,
                clientPublicIdentifier.clientPublicIdentifierId,
                ConnextChannelStatus.Confirming,
                feePayment.lifeTimeSeconds
              )
          }

          channelIdentifier <- channelService.openChannel(
            channel,
            feePayment.currency,
            feePayment.capacity,
            feePayment.lifeTimeSeconds
          )
        } yield {
          discordHelper.sendMessage(
            s"New channel fee paid: ${paymentData.amount
                .toString(payingCurrency)}, for currency = ${feePayment.currency}, capacity = ${feePayment.capacity
                .toString(feePayment.currency)}, lifetime = ${feePayment.lifeTimeDays} days"
          )

          Event.CommandResponse.RentChannelResponse(
            paymentRHash,
            clientIdentifier.identifier,
            channel.channelId,
            channelIdentifier
          )
        }
    }
  }

  def getChannelStatus(channelId: UUID): Future[Event.CommandResponse] = {
    val channel = for {
      lndChannel <- channelService.findChannel(ChannelId.LndChannelId(channelId))
      connextChannel <- channelService.findChannel(ChannelId.ConnextChannelId(channelId))
    } yield lndChannel.orElse(connextChannel)

    channel.map {
      case Some(channel: Channel.LndChannel) =>
        val status = Event.CommandResponse.ChannelStatus.Lnd(
          channel.status,
          channel.expiresAt,
          channel.closingType,
          channel.closedBy,
          channel.closedOn
        )

        Event.CommandResponse.GetChannelStatusResponse(channel.channelId, status)

      case Some(channel: Channel.ConnextChannel) =>
        val status = Event.CommandResponse.ChannelStatus.Connext(
          channel.status,
          channel.expiresAt
        )

        Event.CommandResponse.GetChannelStatusResponse(channel.channelId, status)

      case None =>
        Event.CommandResponse.CommandFailed("Channel not found")
    }
  }

  def refundFee(
      clientId: ClientId,
      currency: Currency,
      refundedFees: List[RefundablePayment]
  ): Future[Event.CommandResponse] = {
    feeService.refund(clientId, currency, refundedFees).map {
      case Right((amount, refundedOn)) =>
        RefundFeeResponse(currency, amount, refundedFees, refundedOn)
      case Left(CouldNotRefundFee(reason)) =>
        CommandFailed(reason)
    }
  }

  def getRefundableAmount(
      currency: Currency,
      paymentRHashList: List[RefundablePayment]
  ): Future[Event.CommandResponse] = {
    if (Currency.forLnd.contains(currency)) {
      feeService.getRefundableAmount(currency, paymentRHashList).map {
        case Right(amount) => Event.CommandResponse.GetRefundableAmountResponse(currency, amount)
        case Left(CouldNotCalculateRefundableAmount(reason)) => Event.CommandResponse.CommandFailed(reason)
      }
    } else {
      Future.successful(CommandFailed(s"$currency not supported"))
    }
  }

  def getFeeToRentChannel(channelFeePayment: ChannelFeePayment): Future[Event.CommandResponse] = {
    channelService.getFeeAmount(channelFeePayment).map {
      case Right(fees) =>
        Event.CommandResponse.GetFeeToRentChannelResponse(
          fee = fees.totalFee,
          rentingFee = fees.rentingFee + fees.forceClosingFee,
          onChainFees = fees.transactionFee
        )
      case Left(error) => CommandFailed(error)
    }
  }

  def getFeeToExtendRentedChannel(
      channelId: UUID,
      payingCurrency: Currency,
      lifetimeSeconds: Long
  ): Future[Event.CommandResponse] = {
    channelService.getExtensionFeeAmount(channelId, payingCurrency, lifetimeSeconds).map {
      case Right(feeAmount) =>
        Event.CommandResponse.GetFeeToExtendRentedChannelResponse(feeAmount)

      case Left(error) =>
        CommandFailed(error)
    }
  }

  def generateInvoiceToExtendRentedChannel(
      channelId: ChannelId.LndChannelId,
      payingCurrency: Currency,
      seconds: Long
  ): Future[Event.CommandResponse] = {
    val result = for {
      _ <- Either.cond(Currency.forLnd.contains(payingCurrency), (), s"$payingCurrency not supported").toFutureEither()
      _ <- channelService.canExtendRentedTime(channelId).toFutureEither()
      fee <- channelService.getExtensionFeeAmount(channelId.value, payingCurrency, seconds).toFutureEither()

      memo = "Fee to extend rented channel"
      paymentRequest <- paymentService
        .createPaymentRequest(payingCurrency, fee, memo)
        .map(_.left.map(_.reason))
        .toFutureEither()

      hash <- paymentService.getPaymentHash(payingCurrency, paymentRequest).map(Right.apply).toFutureEither()
      _ <- channelService
        .requestRentedChannelExtension(channelId, payingCurrency, seconds, fee, hash)
        .map(Right.apply)
        .toFutureEither()
    } yield GenerateInvoiceToExtendRentedChannelResponse(channelId, payingCurrency, seconds, paymentRequest)

    result.toFuture.map {
      case Right(response) => response
      case Left(error) => CommandFailed(error)
    }
  }

  def generatePaymentHashToExtendRentedChannel(
      channelId: ChannelId.ConnextChannelId,
      payingCurrency: Currency,
      seconds: Long
  ): Future[Event.CommandResponse] = {
    val result = for {
      _ <- Either.cond(!Currency.forLnd.contains(payingCurrency), (), s"$payingCurrency not supported").toFutureEither()
      _ <- channelService.canExtendRentedTime(channelId).toFutureEither()
      fee <- channelService.getExtensionFeeAmount(channelId.value, payingCurrency, seconds).toFutureEither()
      hash <- paymentService.generatePaymentHash(payingCurrency).map(Right.apply).toFutureEither()
      _ <- channelService
        .requestRentedChannelExtension(channelId, payingCurrency, seconds, fee, hash)
        .map(Right.apply)
        .toFutureEither()
    } yield GeneratePaymentHashToExtendConnextRentedChannelResponse(channelId, payingCurrency, seconds, hash)

    result.toFuture.map {
      case Right(response) => response
      case Left(error) => CommandFailed(error)
    }
  }

  def extendRentedChannel(
      clientId: ClientId,
      paymentHash: PaymentRHash,
      payingCurrency: Currency
  ): Future[Event.CommandResponse] = {
    channelService.extendRentedChannel(clientId, paymentHash, payingCurrency).map {
      case Right((channelId, newExpiration)) =>
        ExtendRentedChannelTimeResponse(paymentHash, channelId, newExpiration.getEpochSecond)
      case Left(error) =>
        CommandFailed(error)
    }
  }

  def registerPublicKey(
      clientId: ClientId,
      publicKey: Identifier.LndPublicKey,
      currency: Currency
  ): Future[Event.CommandResponse] = {
    if (Currency.forLnd.contains(currency)) {
      clientService.findPublicKey(clientId, currency).flatMap {
        case Some(foundKey) if foundKey.key == publicKey =>
          Future.successful(RegisterPublicKeyResponse(currency, publicKey))
        case Some(foundKey) =>
          Future.successful(
            CommandFailed(s"Client already has a different key(${foundKey.key}) registered for $currency")
          )
        case None =>
          clientService.registerPublicKey(clientId, publicKey, currency).map { _ =>
            CommandResponse.RegisterPublicKeyResponse(currency, publicKey)
          }
      }
    } else {
      Future.successful(CommandFailed(s"$currency not supported"))
    }
  }

  def registerPublicIdentifier(
      clientId: ClientId,
      publicIdentifier: ConnextPublicIdentifier,
      currency: Currency
  ): Future[Event.CommandResponse] = {
    clientService.findPublicIdentifier(clientId, currency).flatMap {
      case Some(foundIdentifier) if foundIdentifier.identifier == publicIdentifier =>
        Future.successful(RegisterPublicIdentifierResponse(currency, publicIdentifier))
      case Some(foundIdentifier) =>
        Future.successful(
          CommandFailed(
            s"Client already has a different identifier(${foundIdentifier.identifier}) registered for $currency"
          )
        )
      case None =>
        clientService.registerPublicIdentifier(clientId, publicIdentifier, currency).map { _ =>
          CommandResponse.RegisterPublicIdentifierResponse(currency, publicIdentifier)
        }
    }
  }

  def createConnextChannelContractDeploymentFee(
      transactionHash: String,
      clientId: ClientId
  ): Future[Event.CommandResponse] = {
    channelService.createConnextChannelContractDeploymentFee(transactionHash, clientId).map {
      case Right(_) =>
        RegisterConnextChannelContractDeploymentFeeResponse(transactionHash)

      case Left(error) =>
        CommandFailed(error)
    }
  }

  def getConnextChannelContractDeploymentFee(clientId: ClientId): Future[Event.CommandResponse] = {
    val hubAddress = channelRentalConfig.connextHubAddress
    val fee = channelRentalConfig.connextChannelContractFee

    channelService.findConnextChannelContractDeploymentFee(clientId).map {
      case Some(_) =>
        GetConnextChannelContractDeploymentFeeResponse(hubAddress, Satoshis.Zero)

      case None =>
        GetConnextChannelContractDeploymentFeeResponse(hubAddress, fee)
    }
  }

  def getPaysFees: Boolean = {
    featureFlags.feesEnabled
  }
}
