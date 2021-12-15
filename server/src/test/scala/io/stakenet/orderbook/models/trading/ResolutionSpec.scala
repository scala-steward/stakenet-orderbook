package io.stakenet.orderbook.models.trading

import org.scalatest.matchers.must.Matchers._
import org.scalatest.OptionValues._
import org.scalatest.wordspec.AnyWordSpec

class ResolutionSpec extends AnyWordSpec {
  "from" should {
    "create resolutions" in {
      val tests = List(
        (Resolution.from("4M"), new Resolution(months = 4)),
        (Resolution.from("2M"), new Resolution(months = 2)),
        (Resolution.from("3M"), new Resolution(months = 3)),
        (Resolution.from("W"), new Resolution(weeks = 1)),
        (Resolution.from("2W "), new Resolution(weeks = 2)),
        (Resolution.from("3W  "), new Resolution(weeks = 3)),
        (Resolution.from("D"), new Resolution(days = 1)),
        (Resolution.from("2D"), new Resolution(days = 2)),
        (Resolution.from("3D"), new Resolution(days = 3)),
        (Resolution.from("1"), new Resolution(minutes = 1)),
        (Resolution.from("2"), new Resolution(minutes = 2)),
        (Resolution.from("60"), new Resolution(minutes = 60)),
        (Resolution.from("15"), new Resolution(minutes = 15))
      )

      tests.foreach(x => x._1.value must be(x._2))
    }

    "tostring returns sent value" in {
      val tests = List(
        (Resolution.from("4M"), "4M"),
        (Resolution.from("2M"), "2M"),
        (Resolution.from("3M"), "3M"),
        (Resolution.from("1W"), "1W"),
        (Resolution.from("2W "), "2W"),
        (Resolution.from("3W  "), "3W"),
        (Resolution.from("1D"), "1D"),
        (Resolution.from("2D"), "2D"),
        (Resolution.from("3D"), "3D"),
        (Resolution.from("1"), "1"),
        (Resolution.from("2"), "2"),
        (Resolution.from("60"), "60"),
        (Resolution.from("15"), "15")
      )

      tests.foreach(x => x._1.value.toString must be(x._2))
    }

    "fail when value is incorrect" in {
      val tests = List(
        Resolution.from("D1"),
        Resolution.from("yea"),
        Resolution.from("YY"),
        Resolution.from("DD")
      )

      tests.foreach(x => x must be(empty))

      val tests2 = List("123456", "yearsdfsd", "YYYYY", "")

      for (value <- tests2) {
        intercept[Exception] {
          Resolution.from(value)
        }
      }
    }
  }
}
