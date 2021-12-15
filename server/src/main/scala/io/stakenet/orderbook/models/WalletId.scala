package io.stakenet.orderbook.models

class WalletId(val id: String) extends AnyVal {
  override def toString: String = id
}

object WalletId {

  // The wallet id is a hash derived from the user's seed, that hash is an alphanumeric string of length 40
  private val VALID_FORMAT = "[a-z0-9]{40}"

  def apply(id: String): Option[WalletId] = Option.when(id.matches(VALID_FORMAT))(new WalletId(id))
}
