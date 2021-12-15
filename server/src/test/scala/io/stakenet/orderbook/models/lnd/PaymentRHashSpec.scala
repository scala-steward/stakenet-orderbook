package io.stakenet.orderbook.models.lnd

import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class PaymentRHashSpec extends AnyWordSpec {
  "building from hex-string" should {
    "work" in {
      val string = "a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6"
      val result = PaymentRHash.untrusted(string)
      result mustNot be(empty)
    }

    val invalidInputs = List(
      "missing character" -> "a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd",
      "extra character" -> "a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6a",
      "empty string" -> "",
      "extra byte" -> "a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991cd6aa",
      "missing byte" -> "a13a667d36fa6e492823e882281b287114dc70c41609555fc64aa4ec7f991c"
    )

    invalidInputs.foreach {
      case (kind, input) =>
        s"fail on invalid input: $kind" in {
          val result = PaymentRHash.untrusted(input)
          result must be(empty)
        }
    }
  }

  "building from bytes" should {
    "work" in {
      val bytes = Vector(-95, 58, 102, 125, 54, -6, 110, 73, 40, 35, -24, -126, 40, 27, 40, 113, 20, -36, 112, -60, 22,
        9, 85, 95, -58, 74, -92, -20, 127, -103, 28, -42).map(_.asInstanceOf[Byte]).toArray
      val result = PaymentRHash.untrusted(bytes)
      result mustNot be(empty)
    }
  }
}
