package io.stakenet.orderbook.models

case class AuthenticationInformation(
    botMakerSecret: Option[String],
    walletId: Option[String],
    websocketSubprotocol: Option[String]
) {
  override def toString: String = {
    val secret = botMakerSecret.map(_ => "******")

    s"AuthenticationInformation($secret, $walletId, $websocketSubprotocol)"
  }
}
