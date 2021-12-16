package io.stakenet.orderbook.repositories.feeRefunds

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.lnd.{FeeRefund, PaymentRHash}
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository.Errors._
import javax.inject.Inject

import scala.concurrent.Future

trait FeeRefundsRepository[F[_]] {

  def createRefund(
      refundedHashes: List[PaymentRHash],
      currency: Currency,
      amount: Satoshis
  ): F[FeeRefund.Id]
  def find(paymentHash: PaymentRHash, currency: Currency): F[Option[FeeRefund]]
  def completeRefund(id: FeeRefund.Id): F[Either[CantCompleteRefund, Unit]]
  def failRefund(id: FeeRefund.Id): F[Either[CantFailRefund, Unit]]
}

object FeeRefundsRepository {

  type Id[T] = T
  trait Blocking extends FeeRefundsRepository[Id]

  class FutureImpl @Inject() (blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends FeeRefundsRepository[scala.concurrent.Future] {

    override def createRefund(
        refundedHashes: List[PaymentRHash],
        currency: Currency,
        amount: Satoshis
    ): Future[FeeRefund.Id] = Future {
      blocking.createRefund(refundedHashes, currency, amount)
    }

    override def find(paymentHash: PaymentRHash, currency: Currency): Future[Option[FeeRefund]] = Future {
      blocking.find(paymentHash, currency)
    }

    override def completeRefund(id: FeeRefund.Id): Future[Either[CantCompleteRefund, Unit]] = Future {
      blocking.completeRefund(id)
    }

    override def failRefund(id: FeeRefund.Id): Future[Either[CantFailRefund, Unit]] = Future {
      blocking.failRefund(id)
    }
  }

  sealed trait Error

  object Errors {
    case class CantCompleteRefund(reason: String) extends Error
    case class CantFailRefund(reason: String) extends Error
  }
}
