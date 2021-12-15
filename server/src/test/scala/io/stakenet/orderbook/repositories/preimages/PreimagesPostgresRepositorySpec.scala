package io.stakenet.orderbook.repositories.preimages

import java.time.Instant

import helpers.Helpers
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.Preimage
import io.stakenet.orderbook.models.lnd.PaymentRHash
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

class PreimagesPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new PreimagesPostgresRepository(database)

  "createPreimage" should {
    "create a preimage" in {
      val preimage = Preimage.random()
      val hash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      repository.createPreimage(preimage, hash, Currency.WETH, createdAt)

      succeed
    }

    "create the same preimage for a different currency" in {
      val preimage = Preimage.random()
      val hash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      repository.createPreimage(preimage, hash, Currency.WETH, createdAt)
      repository.createPreimage(preimage, hash, Currency.XSN, createdAt)

      succeed
    }

    "fail on repeated preimage " in {
      val preimage = Preimage.random()
      val hash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      repository.createPreimage(preimage, hash, Currency.WETH, createdAt)

      val error = intercept[PSQLException] {
        repository.createPreimage(preimage, Helpers.randomPaymentHash(), Currency.WETH, createdAt)
      }

      error.getMessage mustBe s"$preimage already exist"
    }

    "fail on repeated payment hash " in {
      val preimage = Preimage.random()
      val hash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      repository.createPreimage(preimage, hash, Currency.WETH, createdAt)

      val error = intercept[PSQLException] {
        repository.createPreimage(Preimage.random(), hash, Currency.WETH, createdAt)
      }

      error.getMessage mustBe s"preimage with hash $hash already exist"
    }
  }

  "findPreimage" should {
    "find a preimage" in {
      val preimage = Preimage.random()
      val hash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      repository.createPreimage(preimage, hash, Currency.WETH, createdAt)
      repository.createPreimage(preimage, hash, Currency.BTC, createdAt)

      val result = repository.findPreimage(hash, Currency.WETH).value

      result.toString mustBe preimage.toString
    }

    "return None when preimage does not exist" in {
      val preimage = Preimage.random()
      val hash = PaymentRHash.from(preimage)
      val createdAt = Instant.now

      repository.createPreimage(preimage, hash, Currency.WETH, createdAt)
      repository.createPreimage(preimage, hash, Currency.BTC, createdAt)

      val result = repository.findPreimage(hash, Currency.LTC)

      result mustBe empty
    }
  }
}
