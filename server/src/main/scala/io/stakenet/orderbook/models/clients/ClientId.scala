package io.stakenet.orderbook.models.clients

import java.util.UUID

case class ClientId(uuid: UUID) {
  override def toString: String = uuid.toString
}

object ClientId {
  def random(): ClientId = ClientId(UUID.randomUUID())
}
