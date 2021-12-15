package io.stakenet.orderbook.models.lnd

import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class LndTxidSpec extends AnyWordSpec {

  "apply" should {
    "construct a txid keeping the bytes unchanged" in {
      val bytes = "example".getBytes().toVector
      val actual = LndTxid.apply(bytes).bigEndianBytes
      actual must be(bytes)
    }

    "construct a txid keeping the bytes unchanged from a byte array" in {
      val bytes = "example".getBytes()
      val expected = bytes.toVector
      val actual = LndTxid.apply(bytes).bigEndianBytes
      actual must be(expected)
    }
  }

  "fromLnd" should {
    "handle the incoming bytes as little-endian (reverse them)" in {
      val input = "example".getBytes()
      val expected = "example".getBytes().reverse.toVector
      val actual = LndTxid.fromLnd(input)
      actual.bigEndianBytes must be(expected)
    }
  }

  "untrusted " should {
    "don't alter the given input order" in {
      val txidStr = "d3f5712645c137c899ace9c8735a1030db5563d63365e1ee40730444eb537b8e"
      val actual = LndTxid.untrusted(txidStr)
      actual.value.toString must be(txidStr)
    }

    "fail if the input isn't hex (extra 1 prefix)" in {
      val txidStr = "1d3f5712645c137c899ace9c8735a1030db5563d63365e1ee40730444eb537b8e"
      val actual = LndTxid.untrusted(txidStr)
      actual.isEmpty must be(true)
    }
  }

  "lndBytes" should {
    "return the bytes as little-endian (reverse them)" in {
      val input = "example".getBytes()
      val expected = "example".getBytes().reverse.toVector
      val actual = LndTxid(input).lndBytes
      actual must be(expected)
    }
  }
}
