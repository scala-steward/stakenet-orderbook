package io.stakenet.orderbook.models.lnd

import java.security.MessageDigest

import io.stakenet.orderbook.models.Preimage
import javax.xml.bind.DatatypeConverter

import scala.util.Try

// A valid r_hash has 32 bytes
case class PaymentRHash(value: Vector[Byte]) {
  override def toString: String = DatatypeConverter.printHexBinary(value.toArray)
}

object PaymentRHash {

  def untrusted(bytes: Array[Byte]): Option[PaymentRHash] = {
    if (bytes.length == 32) {
      Some(new PaymentRHash(bytes.toVector))
    } else {
      None
    }
  }

  def untrusted(hexString: String): Option[PaymentRHash] = {
    Try { DatatypeConverter.parseHexBinary(hexString) }.toOption
      .flatMap(untrusted)
  }

  def from(preimage: Preimage): PaymentRHash = {
    val sha256 = MessageDigest.getInstance("SHA-256")
    PaymentRHash(sha256.digest(preimage.value.toArray).toVector)
  }
}
