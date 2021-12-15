package io.stakenet.orderbook.config

import play.api.Configuration

case class IpInfoConfig(urlApi: String, token: String)

object IpInfoConfig {

  def apply(config: Configuration): IpInfoConfig = {
    val token = config.get[String]("token")
    val urlApi = config.get[String]("urlApi")
    IpInfoConfig(urlApi = urlApi, token = token)
  }
}
