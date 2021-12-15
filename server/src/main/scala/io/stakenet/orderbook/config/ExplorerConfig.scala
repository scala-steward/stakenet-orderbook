package io.stakenet.orderbook.config

import play.api.Configuration

case class ExplorerConfig(urlApi: String)

object ExplorerConfig {

  def apply(config: Configuration): ExplorerConfig = {

    val urlApi = config.get[String]("urlApi")
    ExplorerConfig(urlApi = urlApi)
  }
}
