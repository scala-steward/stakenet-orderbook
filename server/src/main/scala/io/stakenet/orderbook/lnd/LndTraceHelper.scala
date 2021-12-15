package io.stakenet.orderbook.lnd

import io.stakenet.orderbook.models.Currency
import kamon.Kamon

import scala.concurrent.{ExecutionContext, Future}

private[lnd] object LndTraceHelper {

  def trace[A](operation: String, currency: Currency)(
      block: => Future[A]
  )(implicit ec: ExecutionContext): Future[A] = {
    val timer = Kamon
      .timer(name = "lnd-latency", description = "The time taken to process lnd requests")
      .withTag("currency", currency.entryName)
      .withTag("method", operation)
      .start()

    val result = block

    result.onComplete(_ => timer.stop())

    result
  }
}
