package io.stakenet.orderbook.repositories.fees

import java.time.Instant

import io.stakenet.orderbook.models.lnd.{Fee, FeeInvoice, PaymentRHash}
import io.stakenet.orderbook.models.{Currency, OrderId, Satoshis}
import io.stakenet.orderbook.repositories.fees.requests.{BurnFeeRequest, LinkFeeToOrderRequest}
import javax.inject.Inject
import play.api.db.Database

class FeesPostgresRepository @Inject() (database: Database) extends FeesRepository.Blocking {

  override def createInvoice(paymentHash: PaymentRHash, currency: Currency, amount: Satoshis): Id[Unit] = {
    val feeInvoice = FeeInvoice(paymentHash, currency, amount, Instant.now)

    database.withConnection { implicit conn =>
      FeesDAO.createInvoice(feeInvoice)
    }
  }

  override def findInvoice(paymentHash: PaymentRHash, currency: Currency): Id[Option[FeeInvoice]] = {
    database.withConnection { implicit conn =>
      FeesDAO.findInvoice(paymentHash, currency)
    }
  }

  override def linkOrder(request: LinkFeeToOrderRequest): Id[Unit] = {
    database.withTransaction { implicit conn =>
      val maybe = FeesDAO.findForUpdate(request.hash, request.currency)
      maybe match {
        case Some(Fee(_, _, _, Some(lockedForOrderId), _, _)) =>
          throw FeeException.FeeLocked(request.hash, lockedForOrderId)

        case Some(fee) if fee.paidAmount < request.amount =>
          throw FeeException.MissingFundsToLinkOrder(request, fee.paidAmount)

        case Some(_) => FeesDAO.lock(request.hash, request.currency, request.orderId)
        case None => FeesDAO.create(request)
      }
    }
  }

  override def unlink(orderId: OrderId): Id[Unit] = {
    database.withConnection { implicit conn =>
      FeesDAO.free(orderId)
    }
  }

  override def unlinkAll(): Id[Unit] = {
    database.withConnection { implicit conn =>
      FeesDAO.freeAll
    }
  }

  override def burn(request: BurnFeeRequest): Id[Unit] = {
    database.withTransaction { implicit conn =>
      FeesDAO.findForUpdate(request.orderId, request.currency) match {
        case None =>
          throw FeeException.OrderNotLinkedToFeeWhileBurning(request.orderId)

        case Some(fee) if fee.paidAmount < request.amount =>
          throw FeeException.MissingFundsToBurnFee(request, fee.paidAmount)

        case Some(fee) =>
          // NOTE: Keeping the row with 0 value allows to avoid validations when the same r_hash is used again
          FeesDAO.burn(request.orderId, request.currency, fee.paidAmount - request.amount)
      }
    }
  }

  override def find(hash: PaymentRHash, currency: Currency): Id[Option[Fee]] = {
    database.withConnection { implicit conn =>
      FeesDAO.find(hash, currency)
    }
  }

  override def find(orderId: OrderId, currency: Currency): Id[Option[Fee]] = {
    database.withConnection { implicit conn =>
      FeesDAO.find(orderId, currency)
    }
  }
}
