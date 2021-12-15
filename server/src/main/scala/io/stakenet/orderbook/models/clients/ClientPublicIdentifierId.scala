package io.stakenet.orderbook.models.clients

import java.util.UUID

case class ClientPublicIdentifierId(uuid: UUID) {
  override def toString: String = uuid.toString
}

object ClientPublicIdentifierId {
  def random(): ClientPublicIdentifierId = ClientPublicIdentifierId(UUID.randomUUID())
}
