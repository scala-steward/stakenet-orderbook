package io.stakenet.orderbook.models.clients

import io.stakenet.orderbook.models.Currency

trait ClientIdentifier {
  def identifier: Identifier
}

object ClientIdentifier {

  case class ClientLndPublicKey(
      clientPublicKeyId: ClientPublicKeyId,
      key: Identifier.LndPublicKey,
      currency: Currency,
      clientId: ClientId
  ) extends ClientIdentifier {
    override def identifier: Identifier = key
  }

  case class ClientConnextPublicIdentifier(
      clientPublicIdentifierId: ClientPublicIdentifierId,
      override val identifier: Identifier.ConnextPublicIdentifier,
      currency: Currency,
      clientId: ClientId
  ) extends ClientIdentifier
}
