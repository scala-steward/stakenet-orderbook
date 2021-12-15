package io.stakenet.orderbook.config

import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

case class OrderFeesConfig(refundableAfter: FiniteDuration)

object OrderFeesConfig {

  def apply(config: Configuration): OrderFeesConfig = {
    val refundableAfter = config.get[FiniteDuration]("refundableAfter")
    OrderFeesConfig(refundableAfter)
  }
}
