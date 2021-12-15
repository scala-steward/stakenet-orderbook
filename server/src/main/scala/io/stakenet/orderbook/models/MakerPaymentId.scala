package io.stakenet.orderbook.models

import java.util.UUID

import scala.util.Try

case class MakerPaymentId(value: UUID) extends AnyVal with Ordered[MakerPaymentId] {
  override def compare(that: MakerPaymentId): Int = this.value.compareTo(that.value)
  override def toString: String = value.toString
}

object MakerPaymentId {

  def random(): MakerPaymentId = {
    MakerPaymentId(UUID.randomUUID())
  }

  def from(string: String): Option[MakerPaymentId] = {
    Try(UUID.fromString(string)).map(MakerPaymentId.apply).toOption
  }
}
