package io.stakenet.orderbook.lnd

import java.time.Instant

import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import io.stakenet.orderbook.lnd.LndHelper.CreatePaymentRequestError.{FeeUsdLimitExceeded, UsdRateUnavailable}
import io.stakenet.orderbook.lnd.LndHelper.{CreatePaymentRequestError, SendPaymentError}
import io.stakenet.orderbook.lnd.payments.{KeySendObserver, SendPaymentObserver}
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd._
import io.stakenet.orderbook.models.{Currency, Preimage, Satoshis}
import io.stakenet.orderbook.services.UsdConverter
import javax.inject.Inject
import lnrpc.rpc.ChannelPoint.FundingTxid.FundingTxidBytes
import lnrpc.rpc._
import org.slf4j.LoggerFactory
import routerrpc.router.SendPaymentRequest

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

case class InvalidPaymentHash(value: String)
    extends RuntimeException(s"LND Returned something that's not a payment hash: $value")

// TODO: Remove timers when the spans are used to compute the latency dashboard
class LndHelper @Inject()(
    clientBuilder: LightningClientBuilder,
    usdConverter: UsdConverter,
    lnd: MulticurrencyLndClient
)(
    implicit ec: ExecutionContext
) {
  import LndTraceHelper._

  private val logger = LoggerFactory.getLogger(this.getClass)

  def createPaymentRequest(
      currency: Currency,
      amount: Satoshis,
      memo: String
  ): Future[Either[CreatePaymentRequestError, String]] = {
    val invoiceUsdLimit = clientBuilder.getInvoiceUsdLimit(currency)

    usdConverter.convert(amount, currency).flatMap {
      case Right(usdValue) =>
        if (usdValue <= invoiceUsdLimit) {
          lnd
            .addInvoice(currency, amount, memo)
            .map(Right(_))
        } else {
          Future.successful(Left(FeeUsdLimitExceeded(amount, currency, usdValue, invoiceUsdLimit)))
        }
      case Left(_) =>
        Future.successful(Left(UsdRateUnavailable(currency)))
    }
  }

  // returns the amount paid on the invoice identified by the given r_hash
  // TODO: This method has business logic, take it out
  def validatePayment(rHash: PaymentRHash, currency: Currency): Future[PaymentData] = {
    lnd.lookupInvoice(currency, rHash).transform {
      case Success(value) if value.state.isSettled =>
        // TODO: Verify whether the amtPaidSat is the correct field to use
        // TODO: Verify if we need to run any other operation to actually pick the fee
        val amount = currency
          .satoshis(value.amtPaidSat)
          .getOrElse(
            throw new RuntimeException(
              s"LND Returned something that's not satoshis: ${value.amtPaidSat}, rhash = $rHash"
            )
          )
        Success(PaymentData(amount, Instant.ofEpochSecond(value.settleDate)))

      case Success(_) => Failure(new RuntimeException("Invalid paymentHash, the invoice hasn't been settled"))
      case Failure(ex) =>
        // TODO: Validate the exactly exception
        logger.trace(s"Invalid paymentHash for currency = $currency, r_hash = $rHash", ex)
        Failure(new Exception("Invalid paymentHash"))
    }
  }

  // TODO: This method has business logic, take it out
  def isPaymentSettled(currency: Currency, paymentRHash: PaymentRHash): Future[Boolean] = {
    lnd
      .lookupInvoice(currency, paymentRHash)
      .map(invoice => invoice.state.isSettled)
  }

  // TODO: Use decodePaymentRequest
  def getPaymentHash(currency: Currency, paymentRequest: String): Future[PaymentRHash] =
    trace("decodePayReq", currency) {
      clientBuilder
        .getLnd(currency)
        .decodePayReq(PayReqString(paymentRequest))
        .map { x =>
          PaymentRHash
            .untrusted(x.paymentHash)
            .getOrElse(throw InvalidPaymentHash(x.paymentHash))
        }
    }

  def sendPayment(paymentRequest: String, currency: Currency): Future[Either[SendPaymentError, Unit]] =
    trace("sendPaymentV2", currency) {
      val request = SendPaymentRequest()
        .withPaymentRequest(paymentRequest)
        .withTimeoutSeconds(60)
        .withMaxParts(100)

      val promise = Promise[Either[SendPaymentError, Unit]]()

      val onFailed: PaymentFailureReason => Unit = {
        case PaymentFailureReason.FAILURE_REASON_INCORRECT_PAYMENT_DETAILS =>
          promise.complete(Success(Left(SendPaymentError.IncorrectPaymentDetails())))
        case PaymentFailureReason.FAILURE_REASON_INSUFFICIENT_BALANCE =>
          promise.complete(Success(Left(SendPaymentError.NotEnoughBalance())))
        case PaymentFailureReason.FAILURE_REASON_NO_ROUTE =>
          promise.complete(Success(Left(SendPaymentError.NoRoute())))
        case PaymentFailureReason.FAILURE_REASON_TIMEOUT =>
          promise.complete(Success(Left(SendPaymentError.Timeout())))
        case PaymentFailureReason.Unrecognized(_) =>
          promise.complete(Success(Left(SendPaymentError.Unknown())))
        case PaymentFailureReason.FAILURE_REASON_ERROR =>
          // This should be handled by the onException handler so we do
          // nothing here to avoid completing the promise twice

          ()
        case PaymentFailureReason.FAILURE_REASON_NONE =>
          // This should be handled by the onSucceeded handler so we do
          // nothing here to avoid completing the promise twice

          ()
      }

      val observer = new SendPaymentObserver(
        paymentRequest = paymentRequest,
        onSucceeded = () => promise.complete(Success(Right(()))),
        onFailed = onFailed,
        onException = (exception: Throwable) => promise.failure(exception)
      )

      clientBuilder.getLndRouter(currency).sendPaymentV2(request, observer)
      promise.future
    }

  def keySend(
      publicKey: Identifier.LndPublicKey,
      amount: Satoshis,
      currency: Currency
  ): Future[Either[SendPaymentError, Unit]] =
    trace("keysend", currency) {
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)

      // see https://github.com/lightningnetwork/lnd/blob/6c8c99dae99e741d2817d444f8b11945ddd15e2e/record/experimental.go#L4
      val keySendPreimageCustomRecordId = 5482373484L
      val customRecords = Map(keySendPreimageCustomRecordId -> ByteString.copyFrom(preimage.value.toArray))

      val request = SendPaymentRequest()
        .withDest(ByteString.copyFrom(publicKey.value.toArray))
        .withAmt(amount.valueFor(currency).longValue)
        .withTimeoutSeconds(60)
        .withPaymentHash(ByteString.copyFrom(paymentHash.value.toArray))
        .withDestCustomRecords(customRecords)
        .withMaxParts(100)

      val promise = Promise[Either[SendPaymentError, Unit]]()

      val onFailed: PaymentFailureReason => Unit = {
        case PaymentFailureReason.FAILURE_REASON_INCORRECT_PAYMENT_DETAILS =>
          promise.complete(Success(Left(SendPaymentError.IncorrectPaymentDetails())))
        case PaymentFailureReason.FAILURE_REASON_INSUFFICIENT_BALANCE =>
          promise.complete(Success(Left(SendPaymentError.NotEnoughBalance())))
        case PaymentFailureReason.FAILURE_REASON_NO_ROUTE =>
          promise.complete(Success(Left(SendPaymentError.NoRoute())))
        case PaymentFailureReason.FAILURE_REASON_TIMEOUT =>
          promise.complete(Success(Left(SendPaymentError.Timeout())))
        case PaymentFailureReason.Unrecognized(_) =>
          promise.complete(Success(Left(SendPaymentError.Unknown())))
        case PaymentFailureReason.FAILURE_REASON_ERROR =>
          // This should be handled by the onException handler so we do
          // nothing here to avoid completing the promise twice

          ()
        case PaymentFailureReason.FAILURE_REASON_NONE =>
          // This should be handled by the onSucceeded handler so we do
          // nothing here to avoid completing the promise twice

          ()
      }

      val observer = new KeySendObserver(
        recipient = publicKey,
        onSucceeded = () => promise.complete(Success(Right(()))),
        onFailed = onFailed,
        onException = (exception: Throwable) => promise.failure(exception)
      )

      clientBuilder.getLndRouter(currency).sendPaymentV2(request, observer)
      promise.future
    }

  def closeChannel(
      lndChannel: LndChannel,
      closeChannelObserver: StreamObserver[CloseStatusUpdate],
      active: Boolean,
      estimatedSatPerByte: Satoshis
  ): Future[Unit] = Future {
    val fundingTxidBytes = FundingTxidBytes(ByteString.copyFrom(lndChannel.fundingTransaction.lndBytes))
    val channelPoint =
      ChannelPoint().withFundingTxid(fundingTxidBytes).withOutputIndex(lndChannel.outputIndex)
    val satPerByte = clientBuilder
      .getMaxSatPerByte(lndChannel.currency)
      .min(estimatedSatPerByte.valueFor(lndChannel.currency).toLong)
      .max(1)
    // if the channel is inactive, will try to close forcing
    // The initial fee covered the time takes to force closing channel.
    val request = if (active) {
      CloseChannelRequest().withChannelPoint(channelPoint).withSatPerByte(satPerByte)
    } else {
      CloseChannelRequest().withChannelPoint(channelPoint).withForce(true)
    }

    val _ = trace("closeChannel", lndChannel.currency) {
      clientBuilder
        .getLnd(lndChannel.currency)
        .closeChannel(request, closeChannelObserver)
      Future.unit
    }
  }

  def getTransactionFee(txHash: String, currency: Currency): Future[Option[Satoshis]] = {
    for {
      transactions <- lnd.getTransactions(currency)
      transaction = transactions.find(_.txHash == txHash)
      fee = transaction.map(_.totalFees)
    } yield fee.flatMap(f => currency.satoshis(f.toString))
  }

  def getPublicKey(currency: Currency): Identifier.LndPublicKey = {
    Identifier.LndPublicKey
      .untrusted(clientBuilder.getPublicKey(currency))
      .getOrElse(throw new RuntimeException("Invalid public key configured"))
  }
}

object LndHelper {
  sealed trait SendPaymentError { def getMessage: String }

  object SendPaymentError {
    case class IncorrectPaymentDetails() extends SendPaymentError {
      override def getMessage: String = "Invalid payment data"
    }

    case class NotEnoughBalance() extends SendPaymentError {
      override def getMessage: String = "Not enough balance to make payment"
    }

    case class Timeout() extends SendPaymentError {
      override def getMessage: String = "Payment timeout"
    }

    case class NoRoute() extends SendPaymentError {
      override def getMessage: String = "No route was found to payment recipient"
    }

    case class Unknown() extends SendPaymentError {
      override def getMessage: String = "Unknown error"
    }
  }

  sealed trait CreatePaymentRequestError { def getMessage: String }

  object CreatePaymentRequestError {
    case class FeeUsdLimitExceeded(
        amount: Satoshis,
        currency: Currency,
        usdAmount: BigDecimal,
        limit: BigDecimal
    ) extends CreatePaymentRequestError {
      override def getMessage: String = s"${amount.toString(currency)}($usdAmount) exceeds the $limit fee limit"
    }

    case class UsdRateUnavailable(currency: Currency) extends CreatePaymentRequestError {
      override def getMessage: String = s"Could not get $currency USD rate"
    }
  }

  object GetBalanceError {
    class InvalidBalance(balance: Long) extends RuntimeException(s"Got an invalid balance from lnd: $balance")
  }
}
