package io.stakenet.orderbook.services

import com.google.inject.Inject
import io.stakenet.orderbook.actors.peers.PeerTrade
import io.stakenet.orderbook.lnd.LndHelper
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.{MakerPayment, MakerPaymentId, MakerPaymentStatus, Satoshis}
import io.stakenet.orderbook.repositories.clients.ClientsRepository
import io.stakenet.orderbook.repositories.fees.FeesRepository
import io.stakenet.orderbook.repositories.makerPayments.MakerPaymentsRepository
import io.stakenet.orderbook.utils.Extensions._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait MakerPaymentService {

  def payMaker(clientId: ClientId, peerTrade: PeerTrade): Future[Either[String, Unit]]
  def retryFailedPayments(clientId: ClientId): Future[Unit]
}

object MakerPaymentService {

  class Impl @Inject()(
      makerPaymentsRepository: MakerPaymentsRepository.FutureImpl,
      feesRepository: FeesRepository.FutureImpl,
      clientsRepository: ClientsRepository.FutureImpl,
      paymentService: PaymentService,
      lndHelper: LndHelper
  )(implicit ec: ExecutionContext)
      extends MakerPaymentService {
    val logger = LoggerFactory.getLogger(this.getClass)

    // TODO: Return proper errors instead of strings?
    // TODO: test this method
    override def payMaker(clientId: ClientId, peerTrade: PeerTrade): Future[Either[String, Unit]] = {
      val trade = peerTrade.trade
      val secondOrder = peerTrade.secondOrder

      val makerId = if (trade.existingOrder == secondOrder.orderId) {
        secondOrder.clientId
      } else {
        clientId
      }

      val (takerOrderId, takerFee, takerCurrency) = if (trade.executingOrder == trade.buyingOrderId) {
        (trade.buyingOrderId, trade.buyOrderFee, trade.buyingCurrency)
      } else {
        (trade.sellingOrderId, trade.sellOrderFee, trade.sellingCurrency)
      }

      val paymentAmount = takerFee * 0.45
      val makerPaymentId = MakerPaymentId.random()

      val result = for {
        _ <- Either
          .cond(
            paymentAmount != Satoshis.Zero,
            (),
            "commission is zero, skipping maker payment"
          )
          .toFutureEither()

        _ <- feesRepository
          .find(takerOrderId, takerCurrency)
          .map(_.toRight(s"taker order $takerOrderId did not pay a fee, skipping maker payment"))
          .toFutureEither()

        publicKey <- clientsRepository
          .findPublicKey(makerId, takerCurrency)
          .map(_.toRight(s"client $makerId has not public key for $takerCurrency, skipping maker payment"))
          .toFutureEither()

        _ <- Either
          .cond(
            publicKey.key != lndHelper.getPublicKey(takerCurrency),
            (),
            "skipping self payment"
          )
          .toFutureEither()

        _ <- makerPaymentsRepository
          .createMakerPayment(
            makerPaymentId,
            trade.id,
            makerId,
            paymentAmount,
            takerCurrency,
            MakerPaymentStatus.Pending
          )
          .map(Right.apply)
          .map(_.withLeft[String])
          .toFutureEither()

        _ <- paymentService
          .keySend(publicKey.key, paymentAmount, takerCurrency)
          .flatMap {
            case Right(_) =>
              setPaymentSent(makerPaymentId).map(Right.apply)

            case Left(error) =>
              setPaymentFailed(makerPaymentId).map(_ => Left(error.reason))
          }
          .recoverWith {
            case error =>
              logger.error(s"An error occurred paying maker $makerId, takerOrder = $takerOrderId", error)
              setPaymentFailed(makerPaymentId).map(_ => Left(error.getMessage))
          }
          .toFutureEither()
      } yield ()

      result.toFuture
    }

    // TODO: test this method
    def retryFailedPayments(clientId: ClientId): Future[Unit] = {
      makerPaymentsRepository.getFailedPayments(clientId).map { failedPayments =>
        failedPayments.foreach(retryFailedMakerPayment)
      }
    }

    private def retryFailedMakerPayment(payment: MakerPayment): Future[Unit] = {
      val currency = payment.currency
      val paymentAmount = payment.amount
      val clientId = payment.clientId

      val result = for {
        publicKey <- clientsRepository
          .findPublicKey(clientId, currency)
          .map(_.toRight(s"client $clientId has not public key for $currency, skipping maker payment"))
          .toFutureEither()

        _ <- setPaymentPending(payment.id)
          .map(Right.apply)
          .map(_.withLeft[String])
          .toFutureEither()

        _ <- paymentService
          .keySend(publicKey.key, paymentAmount, currency)
          .map(_.left.map(_.reason))
          .toFutureEither()
      } yield ()

      result.toFuture.transformWith {
        case Success(Right(_)) =>
          setPaymentSent(payment.id)

        case Success(Left(error)) =>
          logger.info(error)
          setPaymentFailed(payment.id)

        case Failure(error) =>
          logger.error(s"An error occurred paying maker $clientId, payment ${payment.id}", error)
          setPaymentFailed(payment.id)
      }
    }

    private def setPaymentFailed(makerPaymentId: MakerPaymentId): Future[Unit] = {
      makerPaymentsRepository.updateStatus(makerPaymentId, MakerPaymentStatus.Failed)
    }

    private def setPaymentPending(makerPaymentId: MakerPaymentId): Future[Unit] = {
      makerPaymentsRepository.updateStatus(makerPaymentId, MakerPaymentStatus.Pending)
    }

    private def setPaymentSent(makerPaymentId: MakerPaymentId): Future[Unit] = {
      makerPaymentsRepository.updateStatus(makerPaymentId, MakerPaymentStatus.Sent)
    }
  }
}
