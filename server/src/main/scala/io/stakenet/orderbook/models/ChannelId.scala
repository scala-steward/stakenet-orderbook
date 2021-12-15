package io.stakenet.orderbook.models

import java.util.UUID

import scala.util.Try

sealed trait ChannelId

object ChannelId {

  case class LndChannelId(value: UUID) extends ChannelId {
    override def toString: String = value.toString
  }

  object LndChannelId {

    def random(): LndChannelId = {
      LndChannelId(UUID.randomUUID())
    }

    def from(string: String): Option[LndChannelId] = {
      Try(UUID.fromString(string)).map(LndChannelId.apply).toOption
    }
  }

  case class ConnextChannelId(value: UUID) extends ChannelId {
    override def toString: String = value.toString
  }

  object ConnextChannelId {

    def random(): ConnextChannelId = {
      ConnextChannelId(UUID.randomUUID())
    }

    def from(string: String): Option[ConnextChannelId] = {
      Try(UUID.fromString(string)).map(ConnextChannelId.apply).toOption
    }
  }
}
