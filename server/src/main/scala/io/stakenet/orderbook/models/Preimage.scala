package io.stakenet.orderbook.models

import java.security.SecureRandom

import javax.xml.bind.DatatypeConverter

import scala.util.Try

case class Preimage(value: Vector[Byte]) {
  assert(value.length == 32, "a preimage should have 32 bytes")

  override def toString: String = DatatypeConverter.printHexBinary(value.toArray)
}

// TODO: add test for this
object Preimage {

  def untrusted(bytes: Vector[Byte]): Option[Preimage] = {
    Option.when(bytes.length == 32)(new Preimage(bytes))
  }

  def untrusted(hexString: String): Option[Preimage] = {
    Try(DatatypeConverter.parseHexBinary(hexString)).toOption
      .map(_.toVector)
      .flatMap(untrusted)
  }

  // see https://github.com/lightningnetwork/lightning-rfc/blob/master/11-payment-encoding.md#tagged-fields
  def random(): Preimage = {
    val random = new SecureRandom()
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)

    new Preimage(bytes.toVector)
  }
}
