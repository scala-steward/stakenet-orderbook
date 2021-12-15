package io.stakenet.orderbook.services

import java.time.Instant

import akka.actor.Scheduler
import io.stakenet.orderbook.config.RetryConfig
import io.stakenet.orderbook.lnd.LndHelper.SendPaymentError
import io.stakenet.orderbook.lnd.{LndHelper, MulticurrencyLndClient}
import io.stakenet.orderbook.models.clients.Identifier
import io.stakenet.orderbook.models.lnd.{PaymentData, PaymentRHash, PaymentRequestData}
import io.stakenet.orderbook.models.{Currency, Preimage, Satoshis}
import io.stakenet.orderbook.repositories.preimages.PreimagesRepository
import io.stakenet.orderbook.services.PaymentService.Error._
import io.stakenet.orderbook.services.PaymentService.Response
import io.stakenet.orderbook.utils.RetryableFuture
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

trait PaymentService {

  def createPaymentRequest(
      currency: Currency,
      amount: Satoshis,
      memo: String
  ): Future[Response[CreatePaymentRequestFailed, String]]
  def validatePayment(currency: Currency, paymentHash: PaymentRHash): Future[PaymentData]
  def isPaymentComplete(currency: Currency, paymentRHash: PaymentRHash): Future[Boolean]
  def getPaymentHash(currency: Currency, paymentRequest: String): Future[PaymentRHash]

  def sendPayment(paymentRequest: String, currency: Currency): Future[Response[PaymentFailed, Unit]]

  def keySend(
      publicKey: Identifier.LndPublicKey,
      amount: Satoshis,
      currency: Currency
  ): Future[Response[PaymentFailed, Unit]]

  def decodePaymentRequest(
      paymentRequest: String,
      currency: Currency
  ): Future[Response[PaymentRequestDecodingFailed, PaymentRequestData]]

  def generatePaymentHash(currency: Currency): Future[PaymentRHash]
}

object PaymentService {
  type Response[E <: Error, A] = Either[E, A]

  class LndImpl @Inject()(
      lndHelper: LndHelper,
      lnd: MulticurrencyLndClient,
      retryConfig: RetryConfig,
      preimagesRepository: PreimagesRepository.FutureImpl
  )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
  ) extends PaymentService {

    override def createPaymentRequest(
        currency: Currency,
        amount: Satoshis,
        memo: String
    ): Future[Response[CreatePaymentRequestFailed, String]] = {
      lndHelper.createPaymentRequest(currency, amount, memo).map { result =>
        result.left.map(_ => CreatePaymentRequestFailed("Error: fee payment was over a 100 USD"))
      }
    }

    override def validatePayment(currencyPayment: Currency, paymentHash: PaymentRHash): Future[PaymentData] = {
      lndHelper.validatePayment(paymentHash, currencyPayment)
    }

    override def sendPayment(paymentRequest: String, currency: Currency): Future[Response[PaymentFailed, Unit]] = {
      val retrying = RetryableFuture.withExponentialBackoff[Either[SendPaymentError, Unit]](
        retryConfig.initialDelay,
        retryConfig.maxDelay
      )

      val shouldRetry: Try[Either[SendPaymentError, Unit]] => Boolean = {
        case Success(Left(SendPaymentError.NoRoute())) => true
        case Success(Left(SendPaymentError.Timeout())) => true
        case _ => false
      }

      retrying(shouldRetry)(lndHelper.sendPayment(paymentRequest, currency))
        .map(_.left.map(e => PaymentFailed(e.getMessage)))
    }

    override def keySend(
        publicKey: Identifier.LndPublicKey,
        amount: Satoshis,
        currency: Currency
    ): Future[Response[PaymentFailed, Unit]] = {
      val retrying = RetryableFuture.withExponentialBackoff[Either[SendPaymentError, Unit]](
        retryConfig.initialDelay,
        retryConfig.maxDelay
      )

      val shouldRetry: Try[Either[SendPaymentError, Unit]] => Boolean = {
        case Success(Left(SendPaymentError.NoRoute())) => true
        case Success(Left(SendPaymentError.Timeout())) => true
        case _ => false
      }

      retrying(shouldRetry)(lndHelper.keySend(publicKey, amount, currency))
        .map(_.left.map(e => PaymentFailed(e.getMessage)))
    }

    override def decodePaymentRequest(
        paymentRequest: String,
        currency: Currency
    ): Future[Response[PaymentRequestDecodingFailed, PaymentRequestData]] = {
      lnd
        .decodePaymentRequest(paymentRequest, currency)
        .map(_.left.map(error => PaymentRequestDecodingFailed(error)))
    }

    override def isPaymentComplete(currency: Currency, paymentRHash: PaymentRHash): Future[Boolean] = {
      lndHelper.isPaymentSettled(currency, paymentRHash)
    }

    override def getPaymentHash(currency: Currency, paymentRequest: String): Future[PaymentRHash] = {
      lndHelper.getPaymentHash(currency, paymentRequest)
    }

    override def generatePaymentHash(currency: Currency): Future[PaymentRHash] = {
      val preimage = Preimage.random()
      val paymentHash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      preimagesRepository.createPreimage(preimage, paymentHash, currency, createdAt).map { _ =>
        paymentHash
      }
    }
  }

  sealed trait Error

  object Error {
    final case class PaymentFailed(reason: String) extends PaymentService.Error
    final case class PaymentRequestDecodingFailed(reason: String) extends PaymentService.Error
    final case class CreatePaymentRequestFailed(reason: String) extends PaymentService.Error
  }
}
