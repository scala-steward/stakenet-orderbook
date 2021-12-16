package io.stakenet.orderbook.repositories.preimages

import java.time.Instant

import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.Preimage
import io.stakenet.orderbook.models.lnd.PaymentRHash
import javax.inject.Inject
import play.api.db.Database

class PreimagesPostgresRepository @Inject() (database: Database) extends PreimagesRepository.Blocking {

  override def createPreimage(preimage: Preimage, hash: PaymentRHash, currency: Currency, createdAt: Instant): Unit = {
    database.withConnection { implicit conn =>
      PreimagesDAO.createPreimage(preimage, hash, currency, createdAt)
    }
  }

  override def findPreimage(hash: PaymentRHash, currency: Currency): Option[Preimage] = {
    database.withConnection { implicit conn =>
      PreimagesDAO.find(hash, currency)
    }
  }
}
