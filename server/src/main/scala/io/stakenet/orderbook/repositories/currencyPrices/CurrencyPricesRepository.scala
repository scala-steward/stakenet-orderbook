package io.stakenet.orderbook.repositories.currencyPrices

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.models.trading.CurrencyPrices
import javax.inject.Inject

import scala.concurrent.Future

trait CurrencyPricesRepository[F[_]] {
  def create(currencyPrices: CurrencyPrices): F[Unit]
}

object CurrencyPricesRepository {

  type Id[T] = T

  trait Blocking extends CurrencyPricesRepository[Id]

  class FutureImpl @Inject()(blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends CurrencyPricesRepository[Future] {

    override def create(currencyPrices: CurrencyPrices): Future[Unit] = Future {
      blocking.create(currencyPrices)
    }
  }
}
