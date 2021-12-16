package io.stakenet.orderbook.models.clients

import javax.xml.bind.DatatypeConverter

import scala.util.Try

sealed trait Identifier extends Product with Serializable

object Identifier {

  case class LndPublicKey(value: Vector[Byte]) extends Identifier {
    override def toString: String = DatatypeConverter.printHexBinary(value.toArray)
  }

  object LndPublicKey {

    def untrusted(value: Array[Byte]): Option[LndPublicKey] = {
      if (value.length == 33) {
        Some(LndPublicKey(value.toVector))
      } else {
        None
      }
    }

    def untrusted(hexString: String): Option[LndPublicKey] = {
      Try { DatatypeConverter.parseHexBinary(hexString) }.toOption
        .flatMap(untrusted)
    }
  }

  case class ConnextPublicIdentifier(value: String) extends Identifier {
    override def toString: String = value
  }

  object ConnextPublicIdentifier {

    def untrusted(value: String): Option[ConnextPublicIdentifier] = {
      if (value.length == 56 && value.startsWith("vector")) {
        Some(ConnextPublicIdentifier(value))
      } else {
        None
      }
    }
  }
}
