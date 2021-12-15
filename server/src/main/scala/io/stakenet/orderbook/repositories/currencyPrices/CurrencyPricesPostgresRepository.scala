package io.stakenet.orderbook.repositories.currencyPrices

import io.stakenet.orderbook.models.trading.CurrencyPrices
import javax.inject.Inject
import play.api.db.Database

class CurrencyPricesPostgresRepository @Inject()(database: Database) extends CurrencyPricesRepository.Blocking {

  override def create(currencyPrices: CurrencyPrices): Unit = {
    database.withConnection { implicit conn =>
      CurrencyPricesDAO.create(currencyPrices)
    }
  }
}
