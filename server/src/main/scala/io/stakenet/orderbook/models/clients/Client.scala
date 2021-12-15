package io.stakenet.orderbook.models.clients

import io.stakenet.orderbook.models.WalletId

sealed trait Client

object Client {
  case class BotMakerClient(name: String, clientId: ClientId, paysFees: Boolean) extends Client
  case class WalletClient(walletId: WalletId, clientId: ClientId) extends Client
}
