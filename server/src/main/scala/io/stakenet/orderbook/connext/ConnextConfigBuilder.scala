package io.stakenet.orderbook.connext

import io.stakenet.orderbook.models.Currency
import javax.inject.Inject
import play.api.Configuration

class ConnextConfigBuilder @Inject()(config: Configuration) {

  def getConfig(currency: Currency): ConnextConfig = {
    val currencyConfig = config.get[Configuration](s"connext.${currency.entryName}")

    ConnextConfig(
      host = currencyConfig.get[String]("host"),
      port = currencyConfig.get[String]("port"),
      publicIdentifier = currencyConfig.get[String]("publicIdentifier"),
      chainId = currencyConfig.get[String]("chainId"),
      assetId = currencyConfig.get[String]("assetId"),
      signerAddress = currencyConfig.get[String]("signerAddress"),
      withdrawAddress = currencyConfig.get[String]("withdrawAddress")
    )
  }
}
