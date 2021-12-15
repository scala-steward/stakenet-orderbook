package io.stakenet.orderbook.models

import io.stakenet.orderbook.models.ChannelIdentifier.LndOutpoint
import io.stakenet.orderbook.models.lnd.LndTxid
import org.scalatest.OptionValues._
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec

class LndOutpointSpec extends AnyWordSpec {
  "from " should {

    "construct an object with correct string values" in {
      List(
        "075a9a8bb0fdd99b830383e7912ba537e25e9ee8e7b48a62df8c80b86dd25475:0",
        "9f8a12a74dc482809beece6618cace9e441e4b333c4cd7a66b8c69dcc6d2b1dd:0",
        "7780b43b54556b1d7aebb22eed3c8b5888b9ed19a7a5a21530ad2a221ed0f656:1",
        "12183772d17569aa7c71225fcbc60385a51ab8a4a820612f738a7b3188981172:1",
        "db99440ebf7f858727b31e1fbfe829d40ce27b9745919c54b5b72ef569ffe5b0:1",
        "948ea80cf4077ad15fa57649b3e63c451ee90c0ae9b91f040f2fc93a7cfa58d7:1",
        "e563add95a539ace897ed67218d0206d0f378fe1d305ebfefc54331e775c43db:1",
        "ebab4227aa44f69c3553049bb7efb7b450e8ec07684194e354f049f9f96aa0d3:0",
        "b998d99033821ca3a5ed9cc4d11cdf728575fb0b1383622f21e4fb841e26dedb:0"
      ).foreach { x =>
        LndOutpoint.untrusted(x) mustNot be(empty)
      }
    }

    "construct with correct objects" in {
      val txid = LndTxid.untrusted("075a9a8bb0fdd99b830383e7912ba537e25e9ee8e7b48a62df8c80b86dd25475").value
      val expected = LndOutpoint(txid, 0)
      val result = LndOutpoint.untrusted("075a9a8bb0fdd99b830383e7912ba537e25e9ee8e7b48a62df8c80b86dd25475:0").value

      result mustBe expected
    }

    "fail for invalid strings" in {
      List(
        "075a9a8bb0fdd99b830383e7912ba537e25e9ee8e7b48a62df8c80b86dd25475:g",
        "9f8a12a74dc482809beece6618cace9e441e4b333c4cd7a66b8c69dcc6d2b1dd:0:4",
        "",
        "gt:1",
        "db99440ebf7f858727b31e1fbfe829d40ce27b9745919c54b5b72ef569ffe5b0",
        "1"
      ).foreach { x =>
        LndOutpoint.untrusted(x) must be(empty)
      }
    }
  }
}
