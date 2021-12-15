package io.stakenet.orderbook.repositories.feeRefunds

import java.time.Instant

import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.models.lnd.{FeeRefund, PaymentRHash, RefundStatus}
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository.Errors._
import io.stakenet.orderbook.repositories.feeRefunds.FeeRefundsRepository.Id
import io.stakenet.orderbook.repositories.fees.{FeeException, FeesDAO}
import javax.inject.Inject
import play.api.db.Database

class FeeRefundsPostgresRepository @Inject()(database: Database) extends FeeRefundsRepository.Blocking {
  override def createRefund(
      refundedHashes: List[PaymentRHash],
      currency: Currency,
      amount: Satoshis
  ): Id[FeeRefund.Id] = {
    val id = FeeRefund.Id.random()

    val feeRefund = FeeRefund(
      id,
      currency,
      amount,
      RefundStatus.Processing,
      Instant.now(),
      None
    )

    database.withTransaction { implicit connection =>
      FeeRefundsDAO.create(feeRefund, refundedHashes)

      refundedHashes.foreach { hash =>
        FeesDAO.findForUpdate(hash, currency) match {
          case Some(_) => FeesDAO.refund(hash, currency)
          case None => throw FeeException.RefundedFeeNotFound(hash, currency)
        }
      }
    }

    id
  }

  override def find(paymentHash: PaymentRHash, currency: Currency): Id[Option[FeeRefund]] = {
    database.withConnection { implicit connection =>
      FeeRefundsDAO.find(paymentHash, currency)
    }
  }

  override def completeRefund(id: FeeRefund.Id): Id[Either[CantCompleteRefund, Unit]] = {
    database.withTransaction { implicit connection =>
      for {
        refund <- FeeRefundsDAO
          .findForUpdate(id)
          .toRight(CantCompleteRefund(s"Refund $id does not exists."))

        _ <- Right(refund).filterOrElse(
          _.status != RefundStatus.Refunded,
          CantCompleteRefund(s"Refund $id was already in ${refund.status.entryName} status.")
        )

        completedRefund = refund.copy(
          status = RefundStatus.Refunded,
          refundedOn = Some(Instant.now())
        )

      } yield FeeRefundsDAO.update(completedRefund)
    }
  }

  override def failRefund(id: FeeRefund.Id): Id[Either[CantFailRefund, Unit]] = {
    database.withTransaction { implicit connection =>
      for {
        refund <- FeeRefundsDAO
          .findForUpdate(id)
          .toRight(CantFailRefund(s"Refund $id does not exists."))

        _ <- Right(refund).filterOrElse(
          _.status == RefundStatus.Processing,
          CantFailRefund(s"Refund $id was already in ${refund.status.entryName} status.")
        )

        failedRefund = refund.copy(
          status = RefundStatus.Failed
        )

      } yield FeeRefundsDAO.update(failedRefund)
    }
  }
}
