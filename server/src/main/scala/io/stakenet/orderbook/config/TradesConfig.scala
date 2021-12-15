package io.stakenet.orderbook.config

import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

case class TradesConfig(swapTimeout: FiniteDuration)

object TradesConfig {

  def apply(config: Configuration): TradesConfig = {
    val swapTimeout = config.get[FiniteDuration]("swapTimeout")
    TradesConfig(swapTimeout)
  }
}
