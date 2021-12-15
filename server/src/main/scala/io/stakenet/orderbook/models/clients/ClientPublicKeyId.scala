package io.stakenet.orderbook.models.clients

import java.util.UUID

case class ClientPublicKeyId(uuid: UUID) {
  override def toString: String = uuid.toString
}

object ClientPublicKeyId {
  def random(): ClientPublicKeyId = ClientPublicKeyId(UUID.randomUUID())
}
