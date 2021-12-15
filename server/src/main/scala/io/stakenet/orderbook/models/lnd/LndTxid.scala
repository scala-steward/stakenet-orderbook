package io.stakenet.orderbook.models.lnd

import javax.xml.bind.DatatypeConverter

import scala.util.Try

/**
 * Represents a txid from lnd
 *
 * Such txid has some problems, as returns the bytes in little-endian, while, the string
 * txid that you see on the explorers use big-endian.
 *
 * To avoid confusion, it's simpler to just normalize everything to big-endian, and,
 * use little-endian when dealing directly with lnd.
 *
 * @param bigEndianBytes the txid bytes in big-endian
 */
case class LndTxid(bigEndianBytes: Vector[Byte]) extends AnyVal {
  // lnd requires little-endian txids
  def lndBytes: Array[Byte] = bigEndianBytes.reverse.toArray

  override def toString: String = DatatypeConverter.printHexBinary(bigEndianBytes.toArray).toLowerCase
}

object LndTxid {

  def apply(bigEndianBytes: Array[Byte]): LndTxid = {
    new LndTxid(bigEndianBytes.toVector)
  }

  def fromLnd(littleEndianBytes: Array[Byte]): LndTxid = {
    val bigEndianBytes = littleEndianBytes.reverse
    LndTxid(bigEndianBytes.toVector)
  }

  def untrusted(hexString: String): Option[LndTxid] = {
    Try { DatatypeConverter.parseHexBinary(hexString).toVector }.toOption
      .map(LndTxid.apply)
  }
}
