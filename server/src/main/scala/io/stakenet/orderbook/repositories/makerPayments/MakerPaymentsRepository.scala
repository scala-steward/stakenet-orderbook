package io.stakenet.orderbook.repositories.makerPayments

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.Trade
import io.stakenet.orderbook.models.{Currency, MakerPayment, MakerPaymentId, MakerPaymentStatus, Satoshis}
import javax.inject.Inject

import scala.concurrent.Future

trait MakerPaymentsRepository[F[_]] {

  def createMakerPayment(
      makerPaymentId: MakerPaymentId,
      tradeId: Trade.Id,
      clientId: ClientId,
      amount: Satoshis,
      currency: Currency,
      status: MakerPaymentStatus
  ): F[Unit]

  def getFailedPayments(clientId: ClientId): F[List[MakerPayment]]
  def updateStatus(id: MakerPaymentId, status: MakerPaymentStatus): F[Unit]
}

object MakerPaymentsRepository {

  type Id[T] = T
  trait Blocking extends MakerPaymentsRepository[Id]

  class FutureImpl @Inject()(blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends MakerPaymentsRepository[Future] {
    override def createMakerPayment(
        makerPaymentId: MakerPaymentId,
        tradeId: Trade.Id,
        clientId: ClientId,
        amount: Satoshis,
        currency: Currency,
        status: MakerPaymentStatus
    ): Future[Unit] = Future {
      blocking.createMakerPayment(makerPaymentId, tradeId, clientId, amount, currency, status)
    }

    override def getFailedPayments(clientId: ClientId): Future[List[MakerPayment]] = Future {
      blocking.getFailedPayments(clientId)
    }

    override def updateStatus(id: MakerPaymentId, status: MakerPaymentStatus): Future[Unit] = Future {
      blocking.updateStatus(id, status)
    }
  }
}
