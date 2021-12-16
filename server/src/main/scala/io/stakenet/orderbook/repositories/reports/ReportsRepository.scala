package io.stakenet.orderbook.repositories.reports

import java.time.Instant

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.reports.{
  ChannelRentalExtensionFee,
  ChannelRentalFee,
  ChannelRentalFeeDetail,
  ChannelRentalReport,
  ClientStatus,
  FeeRefundsReport,
  OrderFeePayment,
  PartialOrder,
  TradesFeeReport
}
import javax.inject.Inject

import scala.concurrent.Future

trait ReportsRepository[F[_]] {
  def createOrderFeePayment(orderFeePayment: OrderFeePayment): F[Unit]
  def createPartialOrder(partialOrder: PartialOrder): F[Unit]
  def createChannelRentalFee(channelRentalFee: ChannelRentalFee): F[Unit]
  def createFeeRefundedReport(feeRefundsReport: FeeRefundsReport): F[Unit]
  def getChannelRentReport(currency: Currency, from: Instant, to: Instant): F[ChannelRentalReport]
  def getTradesFeeReport(currency: Currency, from: Instant, to: Instant): F[TradesFeeReport]
  def createChannelRentalExtensionFee(channelRentalExtensionFee: ChannelRentalExtensionFee): F[Unit]
  def createChannelRentalFeeDetail(channelRentalFeeDetail: ChannelRentalFeeDetail): F[Unit]
  def getClientsStatusReport(): F[List[ClientStatus]]
}

object ReportsRepository {

  type Id[T] = T
  trait Blocking extends ReportsRepository[Id]

  class FutureImpl @Inject() (blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends ReportsRepository[scala.concurrent.Future] {

    override def createOrderFeePayment(orderFeePayment: OrderFeePayment): Future[Unit] = Future {
      blocking.createOrderFeePayment(orderFeePayment)
    }

    override def createPartialOrder(partialOrder: PartialOrder): Future[Unit] = Future {
      blocking.createPartialOrder(partialOrder)
    }

    override def createChannelRentalFee(channelRentalFee: ChannelRentalFee): Future[Unit] = Future {
      blocking.createChannelRentalFee(channelRentalFee)
    }

    override def createFeeRefundedReport(feeRefundsReport: FeeRefundsReport): Future[Unit] = Future {
      blocking.createFeeRefundedReport(feeRefundsReport)
    }

    override def getChannelRentReport(
        currency: Currency,
        from: Instant,
        to: Instant
    ): Future[ChannelRentalReport] = Future {
      blocking.getChannelRentReport(currency, from, to)
    }

    override def getTradesFeeReport(currency: Currency, from: Instant, to: Instant): Future[TradesFeeReport] =
      Future {
        blocking.getTradesFeeReport(currency, from, to)
      }

    override def createChannelRentalExtensionFee(channelRentalExtensionFee: ChannelRentalExtensionFee): Future[Unit] =
      Future {
        blocking.createChannelRentalExtensionFee(channelRentalExtensionFee)
      }

    override def createChannelRentalFeeDetail(channelRentalFeeDetail: ChannelRentalFeeDetail): Future[Unit] = Future {
      blocking.createChannelRentalFeeDetail(channelRentalFeeDetail)
    }

    override def getClientsStatusReport(): Future[List[ClientStatus]] = Future {
      blocking.getClientsStatusReport()
    }
  }
}
