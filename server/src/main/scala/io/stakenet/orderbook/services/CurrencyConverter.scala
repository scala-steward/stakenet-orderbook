package io.stakenet.orderbook.services

import com.google.inject.Inject
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.models.{Currency, Satoshis}
import io.stakenet.orderbook.services.apis.PriceApi

import scala.concurrent.{ExecutionContext, Future}

class CurrencyConverter @Inject() (priceApi: PriceApi)(implicit ec: ExecutionContext) {

  def convert(amount: Satoshis, currency: Currency, target: Currency): Future[Satoshis] = {
    val tradingPair = TradingPair.from(currency, target)

    priceApi.getCurrentPrice(tradingPair).map { price =>
      convert(amount, currency, target, price)
    }
  }

  def convert(currency: Currency, target: Currency): Satoshis => Future[Satoshis] = {
    val tradingPair = TradingPair.from(currency, target)
    val price = priceApi.getCurrentPrice(tradingPair)

    amount: Satoshis => price.map(p => convert(amount, currency, target, p))
  }

  def convert(amount: Satoshis, currency: Currency, target: Currency, price: Satoshis): Satoshis = {
    val tradingPair = TradingPair.from(currency, target)

    if (tradingPair.principal == currency) {
      amount / price
    } else {
      amount * price
    }
  }
}
