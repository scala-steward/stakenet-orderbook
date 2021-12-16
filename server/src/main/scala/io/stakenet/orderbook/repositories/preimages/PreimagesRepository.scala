package io.stakenet.orderbook.repositories.preimages

import java.time.Instant

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.Preimage
import io.stakenet.orderbook.models.lnd.PaymentRHash
import javax.inject.Inject

import scala.concurrent.Future

trait PreimagesRepository[F[_]] {
  def createPreimage(preimage: Preimage, hash: PaymentRHash, currency: Currency, createdAt: Instant): F[Unit]
  def findPreimage(hash: PaymentRHash, currency: Currency): F[Option[Preimage]]
}

object PreimagesRepository {

  type Id[T] = T
  trait Blocking extends PreimagesRepository[Id]

  class FutureImpl @Inject() (blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends PreimagesRepository[Future] {

    override def createPreimage(
        preimage: Preimage,
        hash: PaymentRHash,
        currency: Currency,
        createdAt: Instant
    ): Future[Unit] = Future {
      blocking.createPreimage(preimage, hash, currency, createdAt)
    }

    override def findPreimage(hash: PaymentRHash, currency: Currency): Future[Option[Preimage]] = Future {
      blocking.findPreimage(hash, currency)
    }
  }
}
