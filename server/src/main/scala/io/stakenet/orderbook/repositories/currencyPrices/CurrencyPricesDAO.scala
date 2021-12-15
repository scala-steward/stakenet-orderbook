package io.stakenet.orderbook.repositories.currencyPrices

import java.sql.Connection

import anorm._
import io.stakenet.orderbook.models.trading.CurrencyPrices

private[currencyPrices] object CurrencyPricesDAO {

  def create(currencyPrices: CurrencyPrices)(
      implicit conn: Connection
  ): Unit = {
    SQL"""
        INSERT INTO currency_prices(currency, btc_price, usd_price, created_at)
        VALUES (
          ${currencyPrices.currency.toString}::CURRENCY_TYPE,
          ${currencyPrices.btcPrice},
          ${currencyPrices.usdPrice},
          ${currencyPrices.createdAt}
        )  
        """
      .execute()

    ()
  }
}
