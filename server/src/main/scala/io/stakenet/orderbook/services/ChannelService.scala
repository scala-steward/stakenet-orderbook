package io.stakenet.orderbook.services

import java.time.Instant
import java.util.UUID

import akka.actor.Scheduler
import io.stakenet.orderbook.config.{ChannelRentalConfig, RetryConfig}
import io.stakenet.orderbook.connext.{ChannelDepositMonitor, ConnextHelper}
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.lnd.channels.OpenChannelObserver
import io.stakenet.orderbook.lnd.channels.OpenChannelObserver.Exceptions
import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models._
import io.stakenet.orderbook.models.clients.{ClientId, Identifier}
import io.stakenet.orderbook.models.connext.ConnextChannelContractDeploymentFee
import io.stakenet.orderbook.models.explorer.EstimatedFee
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.reports.{ChannelRentalExtensionFee, ChannelRentalFeeDetail}
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.repositories.channels.ChannelsRepository
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.utils.Extensions.{EitherExt, FutureEitherExt}
import io.stakenet.orderbook.utils.RetryableFuture
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait ChannelService {

  def openChannel(
      channel: Channel,
      currency: Currency,
      capacity: Satoshis,
      lifeTimeSeconds: Long
  ): Future[ChannelIdentifier]
  // validate if the payment is complete

  def createChannelFeePayment(
      channelFeePayment: ChannelFeePayment,
      paymentRHash: PaymentRHash,
      fee: ChannelFees
  ): Future[Unit]
  def findChannelFeePayment(paymentRHash: PaymentRHash, currency: Currency): Future[Option[ChannelFeePayment]]
  def findChannelFeePayment(channelId: ChannelId): Future[Option[ChannelFeePayment]]
  def findChannelFeePayment(outpoint: LndOutpoint): Future[Option[ChannelFeePayment]]
  def findChannel(channelId: ChannelId): Future[Option[Channel]]
  def updateChannelStatus(channelId: ChannelId.LndChannelId, channelStatus: ChannelStatus): Future[Unit]
  def getExpiredChannels(currency: Currency): Future[List[LndChannel]]
  def getFeeAmount(channelFeePayment: ChannelFeePayment): Future[Either[String, ChannelFees]]
  def getProcessingChannels(currency: Currency): Future[List[LndChannel]]
  def updateActiveChannel(channelId: ChannelId.LndChannelId, createdAt: Instant, expiresAt: Instant): Future[Unit]
  def updateActiveChannel(outpoint: LndOutpoint, createdAt: Instant, expiresAt: Instant): Future[Unit]

  def getExtensionFeeAmount(
      channelId: UUID,
      payingCurrency: Currency,
      lifetimeSeconds: Long
  ): Future[Either[String, Satoshis]]

  def requestRentedChannelExtension(
      channelId: ChannelId,
      payingCurrency: Currency,
      seconds: Long,
      fee: Satoshis,
      paymentHash: PaymentRHash
  ): Future[Unit]

  def canExtendRentedTime(channelId: ChannelId): Future[Either[String, Unit]]

  def extendRentedChannel(
      clientId: ClientId,
      paymentHash: PaymentRHash,
      payingCurrency: Currency
  ): Future[Either[String, (ChannelId, Instant)]]

  def findChannel(outPoint: LndOutpoint): Future[Option[Channel.LndChannel]]
  def findChannel(paymentHash: PaymentRHash, currency: Currency): Future[Option[Channel]]
  def updateClosedChannel(outPoint: LndOutpoint, closingType: String, closedBy: String): Future[Unit]
  def createCloseExpiredChannelRequest(channelId: ChannelId, active: Boolean): Future[Unit]
  def validateChannelRentalCapacity(capacity: Satoshis, currency: Currency): Future[Either[String, Unit]]
  def getNodesInfo(tradingPair: TradingPair): Future[NodesInfo]

  def createConnextChannelContractDeploymentFee(
      transactionHash: String,
      clientId: ClientId
  ): Future[Either[String, Unit]]
  def findConnextChannelContractDeploymentFee(clientId: ClientId): Future[Option[ConnextChannelContractDeploymentFee]]
}

object ChannelService {

  case class FailedSettingOutPointException(
      channel: ChannelId,
      nodePublicKey: Identifier.LndPublicKey,
      fundingTransaction: LndTxid,
      outputIndex: Int,
      cause: Option[Throwable]
  ) extends RuntimeException {

    override def getMessage: String = {
      s"Failed to set outpoint for pending channel, publicKey: $nodePublicKey, txid = $fundingTransaction, outputIndex = $outputIndex"
    }

    override def getCause: Throwable = cause.orNull
  }

  case class ChannelNotFound(channelId: ChannelId) extends RuntimeException(s"Channel $channelId not found")

  case class ChannelExtensionNotFound(paymentHash: PaymentRHash, payingCurrency: Currency)
      extends RuntimeException(s"Channel extension for $paymentHash in ${payingCurrency.entryName} was not found")

  class ChannelImp @Inject() (
      lnd: MulticurrencyLndClient,
      channelsRepository: ChannelsRepository.FutureImpl,
      discordHelper: DiscordHelper,
      reportsRepository: ReportsRepository.FutureImpl,
      retryConfig: RetryConfig,
      explorerService: ExplorerService,
      currencyConverter: CurrencyConverter,
      usdConverter: UsdConverter,
      channelRentalConfig: ChannelRentalConfig,
      clientsRepository: ClientsRepository.FutureImpl,
      connextHelper: ConnextHelper,
      channelDepositMonitor: ChannelDepositMonitor,
      feeService: FeeService,
      ethService: ETHService
  )(implicit
      ec: ExecutionContext,
      scheduler: Scheduler
  ) extends ChannelService {

    private val logger = LoggerFactory.getLogger(this.getClass)

    override def openChannel(
        channel: Channel,
        currency: Currency,
        capacity: Satoshis,
        lifeTimeSeconds: Long
    ): Future[ChannelIdentifier] = {
      channel match {
        case channel: Channel.LndChannel =>
          val retrying =
            RetryableFuture.withExponentialBackoff[LndOutpoint](retryConfig.initialDelay, retryConfig.maxDelay)

          val shouldRetry: Try[LndOutpoint] => Boolean = {
            case Failure(_: Exceptions.OfflinePeer) => true
            case _ => false
          }

          retrying(shouldRetry) {
            def onChannelOpened(lndTxid: LndTxid): Unit = {
              val createdAt = Instant.now()
              val expiresAt = createdAt.plusSeconds(lifeTimeSeconds)
              val readableCapacity = capacity.toString(currency)
              channelsRepository.updateActiveChannel(channel.channelId, createdAt, expiresAt).onComplete {
                case Success(_) =>
                  val msj =
                    s"Channel opened: currency = $currency, public key =  ${channel.publicKey}, createdAt = $createdAt, expiresAt = $expiresAt, txid = $lndTxid, capacity = $readableCapacity"
                  logger.info(msj)
                  discordHelper.sendMessage(msj)

                case Failure(exception) =>
                  val msj =
                    s"Channel opened on lnd but failed to set it on the database, currency = $currency, public key = ${channel.publicKey}, createdAt = $createdAt, expiresAt = $expiresAt, , txid = $lndTxid, capacity = $readableCapacity"
                  logger.error(msj, exception)
                  discordHelper.sendMessage(msj)
              }
            }

            // the promise just returns the outpoint, as the channel isn't opened for a while
            val promise = Promise[LndOutpoint]()

            def onChannelPending(outPoint: LndOutpoint): Unit = {
              channelsRepository
                .updateChannelPoint(channel.channelId, outPoint)
                .onComplete {
                  case Success(_) =>
                    if (promise.isCompleted) {
                      logger.warn(
                        s"Promise already resolved, Channel pending with publicKey: ${channel.publicKey}, txid = ${outPoint.txid}, outputIndex = ${outPoint.index}"
                      )
                    } else {
                      logger.info(
                        s"Channel pending with publicKey: ${channel.publicKey}, txid = ${outPoint.txid}, outputIndex = ${outPoint.index}"
                      )
                      promise.success(outPoint)
                    }

                  case Failure(exception) =>
                    val newException = FailedSettingOutPointException(
                      channel = channel.channelId,
                      nodePublicKey = channel.publicKey,
                      fundingTransaction = outPoint.txid,
                      outputIndex = outPoint.index,
                      cause = Some(exception)
                    )
                    if (promise.isCompleted) {
                      logger.warn(
                        s"Promise already resolved, Failed to open channel, publicKey = ${channel.publicKey}, channelId = ${channel.publicKey}",
                        newException
                      )
                    } else {
                      logger.info(
                        s"Failed to open channel, publicKey = ${channel.publicKey}, channelId = ${channel.publicKey}",
                        newException
                      )
                      promise.failure(newException)
                    }
                }
            }

            def onError(t: Throwable): Unit = {
              if (!promise.isCompleted) {
                promise.failure(t)
              }
            }

            val openChannelObserver = new OpenChannelObserver(channel, currency, capacity, discordHelper)(
              _onChannelPending = onChannelPending,
              _onChannelOpened = onChannelOpened,
              _onError = onError
            )

            for {
              response <- explorerService.getEstimateFee(currency)
              estimatedFee = response match {
                case Left(_) => EstimatedFee(currency.networkFee).perByte
                case Right(estimatedFee) => estimatedFee.perByte
              }
              _ <- channelsRepository.createChannel(channel)
              _ <- lnd.openChannel(currency, channel.publicKey, capacity, openChannelObserver, estimatedFee)
              outpoint <- promise.future
            } yield outpoint
          }

        case channel: Channel.ConnextChannel =>
          for {
            // On connext we can deposit/withdraw from channels at any time so we can check if we already have a channel
            // with the client and deposit the rented funds there instead of opening a new channel with each rental.
            channelAddress <- connextHelper.getCounterPartyChannelAddress(currency, channel.publicIdentifier).flatMap {
              case Some(address) => Future.successful(address)
              case None => connextHelper.openChannel(channel.publicIdentifier, currency)
            }

            transactionHash <- connextHelper.channelDeposit(channelAddress, capacity, currency)
            _ <- channelsRepository.createChannel(channel.withChannelAddress(channelAddress), transactionHash)
            _ = channelDepositMonitor.monitor(channelAddress, transactionHash, currency)
          } yield channelAddress
      }
    }

    override def createChannelFeePayment(
        channelFeePayment: ChannelFeePayment,
        paymentRHash: PaymentRHash,
        fee: ChannelFees
    ): Future[Unit] = {
      val detail = ChannelRentalFeeDetail(
        paymentRHash,
        fee.currency,
        fee.rentingFee,
        fee.transactionFee,
        fee.forceClosingFee
      )

      reportsRepository.createChannelRentalFeeDetail(detail).recover { case error =>
        logger.error(s"Error reporting channel rental fee detail $paymentRHash.", error)
      }

      channelsRepository.createChannelFeePayment(channelFeePayment, paymentRHash, fee.totalFee)
    }

    override def findChannelFeePayment(
        paymentRHash: PaymentRHash,
        currency: Currency
    ): Future[Option[ChannelFeePayment]] = {

      channelsRepository.findChannelFeePayment(paymentRHash, currency)
    }

    override def findChannelFeePayment(channelId: ChannelId): Future[Option[ChannelFeePayment]] = {
      channelId match {
        case id: ChannelId.LndChannelId => channelsRepository.findChannelFeePayment(id)
        case id: ChannelId.ConnextChannelId => channelsRepository.findChannelFeePayment(id)
      }
    }

    override def findChannel(channelId: ChannelId): Future[Option[Channel]] = {
      channelId match {
        case id: ChannelId.LndChannelId => channelsRepository.findChannel(id)
        case id: ChannelId.ConnextChannelId => channelsRepository.findConnextChannel(id)
      }
    }

    override def updateChannelStatus(channelId: ChannelId.LndChannelId, channelStatus: ChannelStatus): Future[Unit] = {
      channelsRepository.updateChannelStatus(channelId, channelStatus)
    }

    override def getExpiredChannels(currency: Currency): Future[List[LndChannel]] =
      channelsRepository.getExpiredChannels(currency)

    override def getFeeAmount(channelFeePayment: ChannelFeePayment): Future[Either[String, ChannelFees]] = {
      val currency = channelFeePayment.currency
      val payingCurrency = channelFeePayment.payingCurrency

      getChannelFees(channelFeePayment).flatMap {
        case Right(fees) =>
          if (currency != payingCurrency) {
            val convert = currencyConverter.convert(currency, payingCurrency)

            for {
              rentingFees <- convert(fees.rentingFee)
              transactionFee <- convert(fees.transactionFee)
              forceClosingFee <- convert(fees.forceClosingFee)
            } yield Right(
              ChannelFees(
                currency = payingCurrency,
                rentingFee = rentingFees,
                transactionFee = transactionFee,
                forceClosingFee = forceClosingFee
              )
            )
          } else {
            Future.successful(Right(fees))
          }
        case Left(error) => Future.successful(Left(error))
      }
    }

    override def getExtensionFeeAmount(
        channelId: UUID,
        payingCurrency: Currency,
        lifetimeSeconds: Long
    ): Future[Either[String, Satoshis]] = {
      val channelFeesPayment = for {
        lnd <- channelsRepository.findChannelFeePayment(ChannelId.LndChannelId(channelId))
        connext <- channelsRepository.findChannelFeePayment(ChannelId.ConnextChannelId(channelId))
      } yield lnd.orElse(connext)

      val result = for {
        channelFeesPayment <- channelFeesPayment.map(_.toRight(s"Channel $channelId not found")).toFutureEither()

        // We change the payment currency and lifetime of the original fee payment to calculate the extension fee
        // with the requested paying currency and added lifetime
        extensionFeePayment = channelFeesPayment.copy(
          payingCurrency = payingCurrency,
          lifeTimeSeconds = lifetimeSeconds
        )

        extensionFees <- getChannelFees(extensionFeePayment).toFutureEither().flatMap {
          case extensionFees if extensionFeePayment.currency != extensionFeePayment.payingCurrency =>
            currencyConverter
              .convert(extensionFees.extensionFee, extensionFeePayment.currency, extensionFeePayment.payingCurrency)
              .map(Right(_))
              .toFutureEither()

          case extensionFees =>
            Future.successful(Right(extensionFees.extensionFee)).toFutureEither()
        }
      } yield extensionFees

      result.toFuture
    }

    private def getChannelFees(channelFeePayment: ChannelFeePayment): Future[Either[String, ChannelFees]] = {
      val maxDuration = channelRentalConfig.maxDuration
      val minDuration = channelRentalConfig.minDuration
      val currency = channelFeePayment.currency
      val payingCurrency = channelFeePayment.payingCurrency
      val capacity = channelFeePayment.capacity
      val duration = channelFeePayment.lifeTimeSeconds
      val maxCapacityUsd = channelRentalConfig.maxCapacityUsd
      val minCapacityUsd = channelRentalConfig.minCapacityUsd
      val maxOnChainFeesUsd = channelRentalConfig.maxOnChainFeesUsd

      val fees = for {
        _ <- Either.cond(duration <= maxDuration.toSeconds, (), s"Max duration is $maxDuration").toFutureEither()
        _ <- Either.cond(duration >= minDuration.toSeconds, (), s"Min duration is $minDuration").toFutureEither()
        maxCapacity <- usdConverter
          .convert(maxCapacityUsd, currency)
          .map(_.left.map(_ => "Could not verify max capacity, please try again later."))
          .toFutureEither()
        _ <- Either
          .cond(
            capacity <= maxCapacity,
            (),
            s"Max capacity is ${maxCapacity.toString(currency)}($maxCapacityUsd USD)"
          )
          .toFutureEither()
        minCapacity <- usdConverter
          .convert(minCapacityUsd, currency)
          .map(_.left.map(_ => "Could not verify min capacity, please try again later."))
          .toFutureEither()
        _ <- Either
          .cond(
            capacity >= minCapacity,
            (),
            s"Min capacity is ${minCapacity.toString(currency)}($minCapacityUsd USD)"
          )
          .toFutureEither()
        maxOnChainFees <- usdConverter
          .convert(maxOnChainFeesUsd, payingCurrency)
          .map(_.left.map(_ => "Could not verify max on chain fees, please try again later"))
          .toFutureEither()
        fees = channelFeePayment.fees
      } yield fees.copy(transactionFee = fees.transactionFee.min(maxOnChainFees))

      fees.toFuture
    }

    override def getProcessingChannels(currency: Currency): Future[List[LndChannel]] = {
      channelsRepository.getProcessingChannels(currency)
    }

    override def updateActiveChannel(
        channelId: ChannelId.LndChannelId,
        createdAt: Instant,
        expiresAt: Instant
    ): Future[Unit] = {
      channelsRepository.updateActiveChannel(channelId, createdAt, expiresAt)
    }

    override def updateActiveChannel(outpoint: LndOutpoint, createdAt: Instant, expiresAt: Instant): Future[Unit] = {
      channelsRepository.updateActiveChannel(outpoint, createdAt, expiresAt)
    }

    override def requestRentedChannelExtension(
        channelId: ChannelId,
        payingCurrency: Currency,
        seconds: Long,
        fee: Satoshis,
        paymentHash: PaymentRHash
    ): Future[Unit] = {
      channelId match {
        case channelId: ChannelId.LndChannelId =>
          channelsRepository.requestRentedChannelExtension(paymentHash, payingCurrency, channelId, fee, seconds)

        case channelId: ChannelId.ConnextChannelId =>
          channelsRepository.requestRentedChannelExtension(paymentHash, payingCurrency, channelId, fee, seconds)
      }
    }

    override def canExtendRentedTime(channelId: ChannelId): Future[Either[String, Unit]] = {
      val minRemainingTime = 10.minutes
      val extendableBefore = Instant.now.plusSeconds(minRemainingTime.toSeconds)

      findChannel(channelId).map {
        case Some(channel) if !channel.isActive =>
          Left(s"channel ${channel.channelId} is not active")
        case Some(channel) if channel.expiresAt.isEmpty =>
          Left("Invalid state, active channel without expiration date")
        case Some(channel) if channel.expiresAt.exists(_.isBefore(extendableBefore)) =>
          Left(s"Channel $channelId expires in less than $minRemainingTime")
        case None =>
          Left(s"Channel $channelId not found")
        case _ =>
          Right(())
      }
    }

    private def findChannelExtension(
        paymentHash: PaymentRHash,
        currency: Currency
    ): Future[Option[ChannelExtension[ChannelId]]] = {
      for {
        lnd <- channelsRepository.findChannelExtension(paymentHash, currency)
        connext <- channelsRepository.findConnextChannelExtension(paymentHash, currency)
      } yield lnd.orElse(connext)
    }

    override def extendRentedChannel(
        clientId: ClientId,
        paymentHash: PaymentRHash,
        payingCurrency: Currency
    ): Future[Either[String, (ChannelId, Instant)]] = {
      val extensionResponse = for {
        extension <- findChannelExtension(paymentHash, payingCurrency)
          .map(_.toRight(s"Channel extension for $paymentHash in ${payingCurrency.entryName} was not found"))
          .toFutureEither()

        channel <- findChannel(extension.channelId)
          .map(_.toRight(s"Channel ${extension.channelId} not found"))
          .toFutureEither()

        _ <- canExtendRentedTime(extension.channelId).toFutureEither()

        fee <- feeService.getPaymentData(clientId, payingCurrency, paymentHash).toFutureEither()

        _ <- Either
          .cond(
            extension.fee.equals(fee.amount, payingCurrency.digits),
            (),
            s"expected ${extension.fee.toString(payingCurrency)}, got ${fee.amount.toString(payingCurrency)}"
          )
          .toFutureEither()

        channelExpirationDate <- channel.expiresAt
          .toRight("Invalid state, active channel without expiration date")
          .toFutureEither()

        response <-
          if (extension.isApplied) {
            Right((channel.channelId, channelExpirationDate)).withLeft[String].toFutureEither()
          } else {
            reportChannelRentalExtensionFee(channel.channelId, paymentHash, payingCurrency, extension.fee)

            payRentedChannelExtensionFee(extension)
              .toFutureEither()
              .map(_ => (channel.channelId, channelExpirationDate.plusSeconds(extension.seconds)))
          }
      } yield response

      extensionResponse.toFuture
    }

    private def payRentedChannelExtensionFee(extension: ChannelExtension[ChannelId]): Future[Either[String, Unit]] = {
      extension.channelId match {
        case _: ChannelId.LndChannelId =>
          channelsRepository.payRentedChannelExtensionFee(
            extension.asInstanceOf[ChannelExtension[ChannelId.LndChannelId]]
          )

        case _: ChannelId.ConnextChannelId =>
          channelsRepository.payConnextRentedChannelExtensionFee(
            extension.asInstanceOf[ChannelExtension[ChannelId.ConnextChannelId]]
          )
      }
    }

    override def findChannel(outPoint: LndOutpoint): Future[Option[Channel.LndChannel]] = {
      channelsRepository.findChannel(outPoint)
    }

    override def updateClosedChannel(outPoint: LndOutpoint, closingType: String, closedBy: String): Future[Unit] = {
      channelsRepository.updateClosedChannel(outPoint, closingType, closedBy)
    }

    override def createCloseExpiredChannelRequest(channelId: ChannelId, active: Boolean): Future[Unit] = {
      channelsRepository.createCloseExpiredChannelRequest(channelId, active, Instant.now())
    }

    private def reportChannelRentalExtensionFee(
        channelId: ChannelId,
        paymentHash: PaymentRHash,
        payingCurrency: Currency,
        fee: Satoshis
    ): Future[Unit] = {

      findChannelFeePayment(channelId)
        .flatMap {
          case Some(feePayment) =>
            reportsRepository.createChannelRentalExtensionFee(
              ChannelRentalExtensionFee(paymentHash, payingCurrency, feePayment.currency, fee, Instant.now)
            )
          case None =>
            Future.successful(
              logger.warn(s"Error reporting channel rental extension fee $paymentHash, fee payment not found")
            )
        }
        .recover { case error =>
          logger.error(s"Error reporting channel rental extension fee $paymentHash.", error)
        }
    }

    override def findChannelFeePayment(outpoint: LndOutpoint): Future[Option[ChannelFeePayment]] = {
      channelsRepository.findChannelFeePayment(outpoint)
    }

    override def findChannel(paymentHash: PaymentRHash, currency: Currency): Future[Option[Channel]] = {
      if (Currency.forLnd.contains(currency)) {
        channelsRepository.findChannel(paymentHash, currency)
      } else {
        channelsRepository.findConnextChannel(paymentHash, currency)
      }
    }

    override def validateChannelRentalCapacity(capacity: Satoshis, currency: Currency): Future[Either[String, Unit]] = {
      if (Currency.forLnd.contains(currency)) {
        val neededFunds = capacity * 1.2

        lnd.getBalance(currency).map { balance =>
          if (balance >= neededFunds) {
            Right(())
          } else {
            Left("Not enough available funds on this HUB. Please try a smaller amount")
          }
        }
      } else {
        Future.successful(Right(()))
      }
    }

    override def getNodesInfo(tradingPair: TradingPair): Future[NodesInfo] = {
      for {
        (principalChannels, principalClients) <- getNodesInfo(tradingPair.principal)
        (secondaryChannels, secondaryClients) <- getNodesInfo(tradingPair.secondary)
        uniqueClients = (principalClients ++ secondaryClients).toSet.size
      } yield NodesInfo(principalChannels + secondaryChannels, uniqueClients)
    }

    private def getNodesInfo(currency: Currency): Future[(Int, List[ClientId])] = {
      if (Currency.forLnd.contains(currency)) {
        for {
          channels <- lnd.getOpenChannels(currency)
          clients <- clientsRepository.findAllLndClientPublicKeys(currency)
          clientIdsWithChannel = clients.collect {
            case client if channels.exists(_.publicKey == client.key) => client.clientId
          }
        } yield (channels.length, clientIdsWithChannel)
      } else {
        for {
          channels <- connextHelper.getAllChannels(currency)
          clients <- clientsRepository.findAllConnextClientPublicIdentifiers(currency)
          clientIdsWithChannel = clients.collect {
            case client if channels.exists(_.counterPartyIdentifier == client.identifier) => client.clientId
          }
        } yield (channels.length, clientIdsWithChannel)
      }
    }

    override def createConnextChannelContractDeploymentFee(
        transactionHash: String,
        clientId: ClientId
    ): Future[Either[String, Unit]] = {
      val hubAddress = channelRentalConfig.connextHubAddress
      val expectedFee = channelRentalConfig.connextChannelContractFee

      ethService.getTransaction(transactionHash).flatMap {
        case transaction if transaction.to != hubAddress =>
          Future.successful(Left(s"Transaction must be paid to $hubAddress"))

        case transaction if transaction.value != expectedFee =>
          Future.successful(Left(s"expected $expectedFee, got ${transaction.value}"))

        case _ =>
          channelsRepository
            .createConnextChannelContractDeploymentFee(transactionHash, clientId, expectedFee)
            .map(Right.apply)
      }
    }

    override def findConnextChannelContractDeploymentFee(
        clientId: ClientId
    ): Future[Option[ConnextChannelContractDeploymentFee]] = {
      channelsRepository.findConnextChannelContractDeploymentFee(clientId)
    }
  }
}
