package io.stakenet.orderbook.models.clients

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues._

class ClientVersionSpec extends AnyWordSpec with Matchers {
  "apply" should {
    "return a valid ClientVersion" in {
      val version = "0.4.0.4"
      val result = ClientVersion(version).value

      result.toString mustBe version
    }

    "fail when version has more than four components" in {
      val version = "0.4.0.4.0"
      val result = ClientVersion(version)

      result mustBe empty
    }

    "fail when version has less than four components" in {
      val version = "0.4.0"
      val result = ClientVersion(version)

      result mustBe empty
    }

    "fail when version has non numeric characters" in {
      val version = "0.4.0.0a"
      val result = ClientVersion(version)

      result mustBe empty
    }

    "fail when version has empty components" in {
      val version = "0..0.0"
      val result = ClientVersion(version)

      result mustBe empty
    }
  }

  "compare" should {
    "return 0 when versions are equal" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.2.3.4").value
      val result = version2.compare(version1)

      result mustBe 0
    }

    "return a negative number when first component is smaller" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("0.2.3.4").value
      val result = version2.compare(version1)

      result < 0 mustBe true
    }

    "return a negative number when second component is smaller" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.1.3.4").value
      val result = version2.compare(version1)

      result < 0 mustBe true
    }

    "return a negative number when third component is smaller" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.2.2.4").value
      val result = version2.compare(version1)

      result < 0 mustBe true
    }

    "return a negative number when fourth component is smaller" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.2.3.3").value
      val result = version2.compare(version1)

      result < 0 mustBe true
    }

    "return a positive number when first component is bigger" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("2.2.3.4").value
      val result = version2.compare(version1)

      result > 0 mustBe true
    }

    "return a positive number when second component is bigger" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.3.3.4").value
      val result = version2.compare(version1)

      result > 0 mustBe true
    }

    "return a positive number when third component is bigger" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.2.4.4").value
      val result = version2.compare(version1)

      result > 0 mustBe true
    }

    "return a positive number when fourth component is bigger" in {
      val version1 = ClientVersion("1.2.3.4").value
      val version2 = ClientVersion("1.2.3.5").value
      val result = version2.compare(version1)

      result > 0 mustBe true
    }
  }
}
