package io.stakenet.orderbook.lnd

import io.stakenet.orderbook.models.Currency
import javax.inject.Inject
import play.api.Configuration

class LightningConfigBuilder @Inject()(config: Configuration) {

  def getAll: Map[Currency, LndConfig] = {
    Currency.forLnd.map { c =>
      c -> getConfig(c)
    }.toMap
  }

  def getConfig(currency: Currency): LndConfig = {
    val c = config.get[Configuration](s"lnd.${currency.entryName}")
    LndConfig(
      host = c.get[String]("host"),
      port = c.get[Int]("port"),
      tlsCertificateFile = c.get[String]("tlsCertificateFile"),
      macaroonFile = c.get[String]("macaroonFile"),
      publicKey = c.get[String]("publicKey"),
      channelIpAddress = c.get[String]("channelIpAddress"),
      channelPort = c.get[Int]("channelPort"),
      channelMinSize = c.get[Long]("channelMinSize"),
      maxSatPerByte = c.get[Long]("maxSatPerByte"),
      invoiceUsdLimit = BigDecimal(c.get[String]("invoiceUsdLimit"))
    )
  }
}
