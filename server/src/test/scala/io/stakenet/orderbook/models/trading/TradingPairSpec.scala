package io.stakenet.orderbook.models.trading

import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.Currency._
import io.stakenet.orderbook.models.trading.TradingPair._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class TradingPairSpec extends AnyWordSpec {
  "from" should {
    "construct a trading pair from two given currencies" in {
      TradingPair.from(XSN, BTC) must be(XSN_BTC)
      TradingPair.from(BTC, XSN) must be(XSN_BTC)
      TradingPair.from(XSN, LTC) must be(XSN_LTC)
      TradingPair.from(LTC, XSN) must be(XSN_LTC)
      TradingPair.from(LTC, BTC) must be(LTC_BTC)
      TradingPair.from(BTC, LTC) must be(LTC_BTC)
    }

    "fail for invalid currency pairs" in {
      Currency.values.foreach { x =>
        intercept[Exception] {
          TradingPair.from(x, x)
        }
      }

    }
  }
}
