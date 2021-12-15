package io.stakenet.orderbook.actors.connection

import io.stakenet.orderbook.actors.peers.PeerUser

case class ConnectionManagerState(clients: Set[PeerUser]) {
  def +(client: PeerUser): ConnectionManagerState = copy(clients = clients + client)

  def -(client: PeerUser): ConnectionManagerState = copy(clients = clients - client)

  def exists(client: PeerUser): Boolean = client match {
    case bot: PeerUser.Bot =>
      clients.exists {
        case client: PeerUser.Bot if client.id == bot.id => true
        case _ => false
      }

    case wallet: PeerUser.Wallet =>
      clients.exists {
        case client: PeerUser.Wallet if client.id == wallet.id => true
        case _ => false
      }

    case _ =>
      false
  }
}

object ConnectionManagerState {
  def empty: ConnectionManagerState = ConnectionManagerState(Set.empty)
}
