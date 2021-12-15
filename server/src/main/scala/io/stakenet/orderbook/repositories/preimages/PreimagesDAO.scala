package io.stakenet.orderbook.repositories.preimages

import java.sql.Connection
import java.time.Instant

import anorm._
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.Preimage
import io.stakenet.orderbook.models.lnd.PaymentRHash
import org.postgresql.util.{PSQLException, PSQLState}
import io.stakenet.orderbook.repositories.preimages.PreimagesParsers.preimageColumn

private[preimages] object PreimagesDAO {

  private object Constraints {
    val preimagesPk = "preimages_pk"
    val preimagesHashUnique = "preimages_hash_currency_unique"
  }

  def createPreimage(preimage: Preimage, hash: PaymentRHash, currency: Currency, createdAt: Instant)(
      implicit conn: Connection
  ): Unit = {
    val preimageBytes = preimage.value.toArray
    val hashBytes = hash.value.toArray

    try {
      SQL"""
         INSERT INTO connext_preimages(
           preimage,
           hash,
           currency,
           created_at
         ) VALUES (
           $preimageBytes,
           $hashBytes,
           ${currency.entryName}::CURRENCY_TYPE,
           $createdAt
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.preimagesPk) =>
        throw new PSQLException(s"$preimage already exist", PSQLState.DATA_ERROR)
      case error: PSQLException if isConstraintError(error, Constraints.preimagesHashUnique) =>
        throw new PSQLException(s"preimage with hash $hash already exist", PSQLState.DATA_ERROR)
    }
  }

  def find(hash: PaymentRHash, currency: Currency)(implicit conn: Connection): Option[Preimage] = {
    val hashBytes = hash.value.toArray

    SQL"""
         SELECT preimage
         FROM connext_preimages
         WHERE hash = $hashBytes AND
           currency = ${currency.entryName}::CURRENCY_TYPE
       """.as(SqlParser.scalar[Preimage].singleOpt)
  }

  private def isConstraintError(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
