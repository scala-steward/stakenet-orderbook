package io.stakenet.orderbook.models.lnd

import helpers.Helpers
import io.stakenet.orderbook.models.{Currency, Satoshis}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._

class HubLocalBalancesSpec extends AnyWordSpec {
  "HubLocalBalances" should {
    "sum the local balances locked by the same remote peer" in {
      val key1 = Helpers.randomPublicKey().toString
      val key2 = Helpers.randomPublicKey().toString
      val key3 = Helpers.randomPublicKey().toString

      val localBalances = HubLocalBalances
        .empty(Currency.XSN)
        .add(key1, Satoshis.from(BigDecimal(1)).value)
        .add(key1, Satoshis.from(BigDecimal(2)).value)
        .add(key1, Satoshis.from(BigDecimal(3)).value)
        .add(key2, Satoshis.from(BigDecimal(7)).value)
        .add(key2, Satoshis.from(BigDecimal(15)).value)
        .add(key3, Satoshis.from(BigDecimal(50)).value)

      localBalances.get(key1) mustBe Satoshis.from(BigDecimal(6)).value
      localBalances.get(key2) mustBe Satoshis.from(BigDecimal(22)).value
      localBalances.get(key3) mustBe Satoshis.from(BigDecimal(50)).value
    }

    "return zero when key does not have active channels opened with the hub" in {
      val localBalances = HubLocalBalances.empty(Currency.XSN)

      localBalances.get(Helpers.randomPublicKey().toString) mustBe Satoshis.Zero
    }
  }
}
