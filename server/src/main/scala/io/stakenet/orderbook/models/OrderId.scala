package io.stakenet.orderbook.models

import java.util.UUID

import scala.util.Try

case class OrderId(value: UUID) extends AnyVal with Ordered[OrderId] {
  override def compare(that: OrderId): Int = this.value.compareTo(that.value)
  override def toString: String = value.toString
}

object OrderId {

  def random(): OrderId = {
    OrderId(UUID.randomUUID())
  }

  def from(string: String): Option[OrderId] = {
    Try(UUID.fromString(string)).map(OrderId.apply).toOption
  }
}
