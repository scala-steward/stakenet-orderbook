package io.stakenet.orderbook.services.impl

import java.time.Instant

import io.stakenet.orderbook.config.OrderFeesConfig
import io.stakenet.orderbook.connext.ConnextHelper
import io.stakenet.orderbook.connext.ConnextHelper.ResolveTransferError.{
  CouldNotResolveTransfer,
  NoChannelWithCounterParty,
  TransferNotFound
}
import io.stakenet.orderbook.discord.DiscordHelper
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.reports.{FeeRefundsReport, OrderFeePayment, PartialOrder}
import io.stakenet.orderbook.models.trading.TradingOrder
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.repositories.fees.requests.{BurnFeeRequest, LinkFeeToOrderRequest}
import io.stakenet.orderbook.repositories.preimages.PreimagesRepository
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import io.stakenet.orderbook.services.FeeService.Errors.{
  CouldNotCalculateRefundableAmount,
  CouldNotRefundFee,
  CouldNotTakeFee
}
import io.stakenet.orderbook.services.validators.Fees
import io.stakenet.orderbook.services.{ClientService, FeeService, PaymentService}
import io.stakenet.orderbook.utils.Extensions._
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class LndFeeService @Inject() (
    feesRepository: FeesRepository.FutureImpl,
    feeRefundsRepository: FeeRefundsRepository.FutureImpl,
    orderFeesConfig: OrderFeesConfig,
    paymentService: PaymentService,
    discordHelper: DiscordHelper,
    reportsRepository: ReportsRepository.FutureImpl,
    connextHelper: ConnextHelper,
    clientService: ClientService,
    preimagesRepository: PreimagesRepository.FutureImpl
)(implicit
    ec: ExecutionContext
) extends FeeService {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def createInvoice(currency: Currency, amount: Satoshis): Future[Either[String, String]] = {
    paymentService.createPaymentRequest(currency, amount, "Fee for placing order").flatMap {
      case Right(invoice) =>
        for {
          paymentHash <- paymentService.getPaymentHash(currency, invoice)
          _ <- feesRepository.createInvoice(paymentHash, currency, amount)
        } yield Right(invoice)
      case Left(error) =>
        Future.successful(Left(error.reason))
    }
  }

  override def takeFee(
      clientId: ClientId,
      tradingOrder: TradingOrder,
      hash: PaymentRHash
  ): Future[Either[CouldNotTakeFee, Unit]] = {
    val feeCurrency = Fees.getCurrencyPayment(tradingOrder)
    val order = tradingOrder.value

    def linkToExistingFeeRequest(fee: Fee): Future[Either[String, LinkFeeToOrderRequest]] = {
      if (fee.paidAmount < order.funds) {
        val error = "The max funds you can place for this payment hash is: %s, but %s received.".format(
          fee.paidAmount.toString(feeCurrency),
          order.funds.toString(feeCurrency)
        )

        Future.successful(Left(error))
      } else if (fee.lockedForOrderId.isDefined && !fee.lockedForOrderId.contains(order.id)) {
        val error = fee.lockedForOrderId
          .map(orderId => s"The fee $hash is already locked on the order $orderId")
          .getOrElse("Invalid state, fee should be locked for an order")

        Future.successful(Left(error))
      } else {
        val request = LinkFeeToOrderRequest(hash, feeCurrency, order.funds, order.id, fee.paidAt, order.feePercent)

        Future.successful(Right(request))
      }
    }

    def linkToNewFeeRequest(): Future[Either[String, LinkFeeToOrderRequest]] = {
      val paymentCurrency = Fees.getCurrencyPayment(tradingOrder)
      getPaymentData(clientId, paymentCurrency, hash).map {
        case Right(paymentData) =>
          val fundsPaidFor = Fees.getPaidAmount(paymentData.amount, order.feePercent)
          val paidAt = paymentData.settledAt

          val expectedFee = Fees.getFeeValue(order.funds, order.feePercent)

          if (paymentData.amount.lessThan(expectedFee, paymentCurrency.digits)) {
            val error = s"The expected fee to place the order is %s, but %s received.".format(
              expectedFee.toString(feeCurrency),
              paymentData.amount.toString(feeCurrency)
            )

            Left(error)
          } else {
            // if the payment is bigger than the placed value, we store that amount
            val paidAmount = fundsPaidFor.max(order.funds)
            val request = LinkFeeToOrderRequest(hash, feeCurrency, paidAmount, order.id, paidAt, order.feePercent)
            discordHelper
              .sendMessage(s"New order fee paid, amount= ${paymentData.amount.toString(order.feeCurrency)}")
            val orderFeePayment =
              OrderFeePayment(hash, feeCurrency, order.funds, paymentData.amount, order.feePercent, Instant.now())
            reportsRepository.createOrderFeePayment(orderFeePayment).onComplete {
              case Failure(e) =>
                logger.error(
                  s"Couldn't create the order fee payment register. ($orderFeePayment)",
                  e
                )
              case Success(_) => ()
            }
            Right(request)
          }

        case Left(error) => Left(error)
      }
    }

    def linkFee(): Future[Either[CouldNotTakeFee, Unit]] = {
      val linkRequest = feesRepository.find(hash, feeCurrency).flatMap {
        case Some(fee) => linkToExistingFeeRequest(fee)
        case None => linkToNewFeeRequest()

      }

      linkRequest.flatMap {
        case Right(request) => feesRepository.linkOrder(request).map(_ => Right(()))
        case Left(error) => Future.successful(Left(CouldNotTakeFee(error)))
      }
    }

    if (Currency.forLnd.contains(feeCurrency)) {
      feesRepository.findInvoice(hash, feeCurrency).flatMap {
        case Some(_) =>
          linkFee()

        case None =>
          val error = s"fee for $hash in $feeCurrency was not found"
          Future.successful(Left(CouldNotTakeFee(error)))
      }
    } else {
      linkFee()
    }
  }

  override def getPaymentData(
      clientId: ClientId,
      paymentCurrency: Currency,
      hash: PaymentRHash
  ): Future[Either[String, PaymentData]] = {
    if (Currency.forLnd.contains(paymentCurrency)) {
      paymentService
        .validatePayment(paymentCurrency, hash)
        .map(Right.apply)
    } else {
      val result = for {
        clientPublicIdentifier <- clientService
          .findPublicIdentifier(clientId, paymentCurrency)
          .map(_.toRight(s"Client $clientId has not public identifier for $paymentCurrency"))
          .toFutureEither()

        preimage <- preimagesRepository
          .findPreimage(hash, paymentCurrency)
          .map(_.toRight(s"preimage for $hash in $paymentCurrency not found"))
          .toFutureEither()

        paymentData <- connextHelper
          .resolveTransfer(
            paymentCurrency,
            clientPublicIdentifier.identifier,
            hash,
            preimage
          )
          .map { result =>
            result.left.map {
              case _: NoChannelWithCounterParty => s"no channel with client $clientId"
              case _: TransferNotFound => s"transfer not found for payment hash $hash"
              case _: CouldNotResolveTransfer => s"An error ocurred resolving the fee transfer"
            }
          }
          .toFutureEither()
      } yield paymentData

      result.toFuture
    }
  }

  override def unlink(orderId: OrderId): Future[Unit] = {
    feesRepository.unlink(orderId)
  }

  override def burn(orderId: OrderId, currency: Currency, amount: Satoshis): Future[Unit] = {
    val burnRequest = BurnFeeRequest(orderId, currency, amount)
    feesRepository.burn(burnRequest)
  }

  override def find(orderId: OrderId, currency: Currency): Future[Option[Fee]] = {
    feesRepository.find(orderId, currency)
  }

  override def refund(
      clientId: ClientId,
      currency: Currency,
      refundedFees: List[RefundablePayment]
  ): Future[Either[CouldNotRefundFee, (Satoshis, Instant)]] = {
    def sendPayment(refundId: FeeRefund.Id, amount: Satoshis) = {
      val result = for {
        publicKey <- clientService
          .findPublicKey(clientId, currency)
          .map {
            case Some(clientPublicKey) => Right(clientPublicKey.key)
            case None => Left(s"$clientId has no $currency public key registered")
          }
          .toFutureEither()

        _ <- paymentService
          .keySend(publicKey, amount, currency)
          .transformWith {
            case Success(Right(_)) =>
              feeRefundsRepository.completeRefund(refundId).map(_ => Right(()))
            case Success(Left(PaymentService.Error.PaymentFailed(reason))) =>
              feeRefundsRepository.failRefund(refundId).map(_ => Left(reason))
            case Failure(exception) =>
              feeRefundsRepository.failRefund(refundId).map(_ => Left(exception.getMessage))
          }
          .toFutureEither()
      } yield ()

      result.toFuture
    }

    Future.sequence(refundedFees.map(f => feeRefundsRepository.find(f.paymentRHash, currency))).flatMap { result =>
      val refunds = result.collect { case Some(r) => r }

      if (refunds.isEmpty) {
        val result = for {
          refundAmount <- getRefundedAmount(refundedFees, currency).toFutureEither()

          refundedHashes = refundedFees.map(_.paymentRHash)
          refundId <- feeRefundsRepository
            .createRefund(refundedHashes, currency, refundAmount)
            .map(Right.apply)
            .toFutureEither()

          _ <- sendPayment(refundId, refundAmount).toFutureEither()
        } yield (refundId, refundAmount)

        result.toFuture.transform {
          case Success(Right((refundId, amount))) =>
            discordHelper.sendMessage(s"Refunded fee: ${amount.toString(currency)}")
            val feeRefundsReport = FeeRefundsReport(refundId, currency, amount, Instant.now())
            reportsRepository.createFeeRefundedReport(feeRefundsReport).onComplete {
              case Failure(e) =>
                logger.error(s"Couldn't create the fee refund report register. ($feeRefundsReport)", e)
              case Success(_) =>
                ()
            }
            Success(Right((amount, Instant.now())))
          case Success(Left(error)) => Success(Left(CouldNotRefundFee(error)))
          case Failure(exception) => Success(Left(CouldNotRefundFee(exception.getMessage)))
        }
      } else if (refunds.size == result.size && refunds.forall(_.id == refunds.head.id)) {
        val refund = refunds.head

        refund.status match {
          case RefundStatus.Processing =>
            Future.successful(Left(CouldNotRefundFee(s"Refund is already in process")))
          case RefundStatus.Refunded =>
            Future.successful(
              refund.refundedOn
                .map(refundedOn => Right((refund.amount, refundedOn)))
                .getOrElse(Left(CouldNotRefundFee("Invalid state, completed refund without refunded date")))
            )
          case RefundStatus.Failed =>
            sendPayment(refund.id, refund.amount).map {
              case Right(_) => Right((refund.amount, Instant.now()))
              case Left(error) => Left(CouldNotRefundFee(error))
            }
        }
      } else {
        Future.successful(Left(CouldNotRefundFee("Invalid request")))
      }
    }
  }

  private def validateFeesAreUnique(refundedFees: List[RefundablePayment]): Either[String, Unit] = {
    val errors = refundedFees
      .groupBy(_.paymentRHash)
      .filter { case (_, fees) => fees.size > 1 }
      .map { case (hash, fees) => s"$hash was sent ${fees.size} times" }

    Either.cond(errors.isEmpty, (), errors.mkString("[", ", ", "]"))
  }

  private def getRefundableAmount(
      paymentHash: PaymentRHash,
      currency: Currency
  ): Future[Either[String, Satoshis]] = {
    val waitTime = orderFeesConfig.refundableAfter
    val refundableBefore = Instant.now().minusSeconds(waitTime.toSeconds)

    feesRepository
      .find(paymentHash, currency)
      .flatMap {
        case Some(Fee(_, _, _, Some(orderId), _, _)) =>
          Future.successful(Left(s"Fee $paymentHash is locked for order $orderId"))

        case Some(fee) if fee.paidAt.isAfter(refundableBefore) =>
          Future.successful(Left(s"Fee $paymentHash needs to wait $waitTime from the fee payment for a refund"))

        case Some(fee) =>
          Future.successful(Right(fee.refundableFeeAmount))

        // if the fee was never used we need the invoice to get the amount to refund
        case None =>
          for {
            invoice <- feesRepository.findInvoice(paymentHash, currency)
            isPaymentCompleted <- paymentService.isPaymentComplete(currency, paymentHash)
          } yield (invoice, isPaymentCompleted) match {
            case (Some(invoice), true) => Right(invoice.amount)
            case (None, _) => Left(s"Fee with payment hash $paymentHash not found")
            case (_, false) => Left(s"Fee with payment hash $paymentHash is not paid")
          }
      }
  }

  private def getRefundedAmount(
      refundedFees: List[RefundablePayment],
      currency: Currency
  ): Future[Either[String, Satoshis]] = {
    validateFeesAreUnique(refundedFees) match {
      case Right(_) =>
        Future.sequence(refundedFees.map(r => getRefundableAmount(r.paymentRHash, currency))).map { refundedAmounts =>
          val invalidAmounts = refundedAmounts.collect { case Left(error) =>
            error
          }

          if (invalidAmounts.isEmpty) {
            Right(
              refundedAmounts
                .collect { case Right(amount) => amount }
                .foldLeft(Satoshis.Zero)(_ + _)
            )
          } else {
            Left(invalidAmounts.mkString("[", ", ", "]"))
          }
        }
      case Left(error) =>
        Future.successful(Left(error))
    }
  }

  override def getRefundableAmount(
      currency: Currency,
      refundablePayments: List[RefundablePayment]
  ): Future[Either[CouldNotCalculateRefundableAmount, Satoshis]] = {

    if (refundablePayments.isEmpty) {
      Future.successful(
        Left(CouldNotCalculateRefundableAmount("A list of payment hash was expected, but an empty list was provided"))
      )
    } else {
      getRefundedAmount(refundablePayments, currency).map {
        case Left(value) => Left(CouldNotCalculateRefundableAmount(value))
        case Right(value) => Right(value)
      }
    }
  }

  override def savePartialOrder(partialOrder: PartialOrder): Future[Unit] = {
    reportsRepository.createPartialOrder(partialOrder)
  }
}
