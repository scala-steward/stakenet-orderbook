package io.stakenet.orderbook.repositories.fees

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.lnd.{Fee, FeeInvoice, PaymentRHash}
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.fees.requests.{BurnFeeRequest, LinkFeeToOrderRequest}
import javax.inject.Inject

import scala.concurrent.Future

trait FeesRepository[F[_]] {
  def createInvoice(paymentHash: PaymentRHash, currency: Currency, amount: Satoshis): F[Unit]
  def findInvoice(paymentHash: PaymentRHash, currency: Currency): F[Option[FeeInvoice]]
  def linkOrder(request: LinkFeeToOrderRequest): F[Unit]
  def unlink(orderId: OrderId): F[Unit]
  def unlinkAll(): F[Unit]
  def burn(request: BurnFeeRequest): F[Unit]
  def find(hash: PaymentRHash, currency: Currency): F[Option[Fee]]
  def find(orderId: OrderId, currency: Currency): F[Option[Fee]]
}

object FeesRepository {

  trait Blocking extends FeesRepository[Id]

  class FutureImpl @Inject() (blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends FeesRepository[scala.concurrent.Future] {

    override def findInvoice(paymentHash: PaymentRHash, currency: Currency): Future[Option[FeeInvoice]] = Future {
      blocking.findInvoice(paymentHash, currency)
    }

    override def createInvoice(paymentHash: PaymentRHash, currency: Currency, amount: Satoshis): Future[Unit] = Future {
      blocking.createInvoice(paymentHash, currency, amount)
    }

    override def linkOrder(request: LinkFeeToOrderRequest): Future[Unit] = Future {
      blocking.linkOrder(request)
    }

    override def unlink(orderId: OrderId): Future[Unit] = Future {
      blocking.unlink(orderId)
    }

    override def unlinkAll(): Future[Unit] = Future {
      blocking.unlinkAll()
    }

    override def burn(request: BurnFeeRequest): Future[Unit] = Future {
      blocking.burn(request)
    }

    override def find(hash: PaymentRHash, currency: Currency): Future[Option[Fee]] = Future {
      blocking.find(hash, currency)
    }

    override def find(orderId: OrderId, currency: Currency): Future[Option[Fee]] = Future {
      blocking.find(orderId, currency)
    }
  }
}
