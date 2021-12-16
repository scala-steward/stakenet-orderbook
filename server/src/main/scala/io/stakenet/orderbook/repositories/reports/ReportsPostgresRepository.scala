package io.stakenet.orderbook.repositories.reports

import java.time.Instant

import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.reports._
import io.stakenet.orderbook.repositories.reports.ReportsRepository.Id
import javax.inject.Inject
import play.api.db.Database

class ReportsPostgresRepository @Inject() (database: Database) extends ReportsRepository.Blocking {

  override def createOrderFeePayment(orderFeePayment: OrderFeePayment): Unit = {
    database.withConnection { implicit conn =>
      ReportsDAO.createOrderFeePayment(orderFeePayment)
    }
  }

  override def createPartialOrder(partialOrder: PartialOrder): Unit = {
    database.withConnection { implicit conn =>
      ReportsDAO.createPartialOrder(partialOrder)
    }
  }

  override def createChannelRentalFee(channelRentalFee: ChannelRentalFee): Unit = {
    database.withConnection { implicit conn =>
      ReportsDAO.createChannelRentalFee(channelRentalFee)
    }
  }

  override def createFeeRefundedReport(feeRefundsReport: FeeRefundsReport): Unit = {
    database.withConnection { implicit conn =>
      ReportsDAO.createFeeRefundedReport(feeRefundsReport)
    }
  }

  override def getChannelRentReport(currency: Currency, from: Instant, to: Instant): ChannelRentalReport = {
    database.withConnection { implicit conn =>
      val revenue = ReportsDAO.getChannelRentRevenue(currency, from, to)
      val transactionFees = ReportsDAO.getChannelTransactionFees(currency, from, to)
      val extensionsRevenue = ReportsDAO.getChannelRentExtensionsRevenue(currency, from, to)
      val extensionsCount = ReportsDAO.getChannelRentExtensionsCount(currency, from, to)

      ChannelRentalReport(
        currency,
        revenue.rentingFee,
        revenue.transactionFee,
        revenue.forceClosingFee,
        extensionsRevenue.amount,
        transactionFees.fundingTransactionFee,
        transactionFees.closingTransactionFee,
        transactionFees.numRentals,
        extensionsCount,
        transactionFees.lifeTimeSeconds,
        transactionFees.totalCapacity
      )
    }
  }

  override def getTradesFeeReport(currency: Currency, from: Instant, to: Instant): TradesFeeReport = {
    database.withConnection { implicit conn =>
      ReportsDAO.getTradesFeeReport(currency, from, to)
    }
  }

  override def createChannelRentalExtensionFee(channelRentalExtensionFee: ChannelRentalExtensionFee): Id[Unit] = {
    database.withConnection { implicit conn =>
      ReportsDAO.createChannelRentalExtensionFee(channelRentalExtensionFee)
    }
  }

  override def createChannelRentalFeeDetail(channelRentalFeeDetail: ChannelRentalFeeDetail): Id[Unit] = {
    database.withConnection { implicit conn =>
      ReportsDAO.createChannelRentalFeeDetail(channelRentalFeeDetail)
    }
  }

  override def getClientsStatusReport(): Id[List[ClientStatus]] = {
    database.withConnection { implicit conn =>
      ReportsDAO.getClientsStatusReport()
    }
  }
}
