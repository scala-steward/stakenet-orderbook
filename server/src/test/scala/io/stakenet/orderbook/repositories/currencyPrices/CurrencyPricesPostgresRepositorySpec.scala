package io.stakenet.orderbook.repositories.currencyPrices

import java.time.Instant

import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.trading.CurrencyPrices
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import org.scalatest.BeforeAndAfter

class CurrencyPricesPostgresRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new CurrencyPricesPostgresRepository(database)

  "create" should {
    "create currency price" in {
      val currencyPrice = CurrencyPrices(Currency.XSN, BigDecimal(0.123), BigDecimal(0.456), Instant.now)

      repository.create(currencyPrice)

      succeed
    }
  }
}
