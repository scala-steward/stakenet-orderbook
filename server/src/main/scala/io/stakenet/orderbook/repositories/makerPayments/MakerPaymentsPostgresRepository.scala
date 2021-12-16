package io.stakenet.orderbook.repositories.makerPayments

import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.trading.Trade
import io.stakenet.orderbook.models.{Currency, MakerPayment, MakerPaymentId, MakerPaymentStatus, Satoshis}
import io.stakenet.orderbook.repositories.makerPayments.MakerPaymentsRepository.Id
import javax.inject.Inject
import play.api.db.Database

class MakerPaymentsPostgresRepository @Inject() (database: Database) extends MakerPaymentsRepository.Blocking {

  override def createMakerPayment(
      makerPaymentId: MakerPaymentId,
      tradeId: Trade.Id,
      clientId: ClientId,
      amount: Satoshis,
      currency: Currency,
      status: MakerPaymentStatus
  ): Id[Unit] = {
    database.withConnection { implicit conn =>
      MakerPaymentsDAO.createMakerPayment(makerPaymentId, tradeId, clientId, amount, currency, status)
    }
  }

  override def getFailedPayments(clientId: ClientId): Id[List[MakerPayment]] = {
    database.withConnection { implicit conn =>
      MakerPaymentsDAO.getFailedPayments(clientId)
    }
  }

  override def updateStatus(id: MakerPaymentId, status: MakerPaymentStatus): Id[Unit] = {
    database.withConnection { implicit conn =>
      MakerPaymentsDAO.updateStatus(id, status)
    }
  }
}
