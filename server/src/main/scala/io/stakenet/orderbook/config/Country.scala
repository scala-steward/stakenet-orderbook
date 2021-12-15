package io.stakenet.orderbook.config

case class Country(name: String, alpha2Code: String, alpha3Code: String)

object Country {

  val blackList = List(
    Country(name = "United States of America", alpha2Code = "US", alpha3Code = "USA"),
    Country(name = "Canada", alpha2Code = "CA", alpha3Code = "CAN"),
    Country(name = "Cuba", alpha2Code = "CU", alpha3Code = "CUB"),
    Country(name = "Iran", alpha2Code = "IR", alpha3Code = "IRN"),
    Country(name = "Syria", alpha2Code = "SY", alpha3Code = "SYR"),
    Country(name = "North Korea", alpha2Code = "KP", alpha3Code = "PRK"),
    Country(name = "Sudan", alpha2Code = "SD", alpha3Code = "SDN")
  )
}
