package io.stakenet.orderbook.models.lnd

import io.stakenet.orderbook.models.{Currency, Satoshis}

class HubLocalBalances private (val currency: Currency, balances: Map[String, Satoshis]) {

  def add(remotePublicKey: String, localBalance: Satoshis): HubLocalBalances = {
    val lowercaseKey = remotePublicKey.toLowerCase
    val newBalance = balances.getOrElse(lowercaseKey, Satoshis.Zero) + localBalance

    new HubLocalBalances(currency, balances.updated(lowercaseKey, newBalance))
  }

  def get(remotePublicKey: String): Satoshis = {
    balances.getOrElse(remotePublicKey.toLowerCase, Satoshis.Zero)
  }
}

object HubLocalBalances {
  def empty(currency: Currency): HubLocalBalances = new HubLocalBalances(currency, Map.empty[String, Satoshis])
}
