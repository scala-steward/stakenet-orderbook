package io.stakenet.orderbook.repositories.ipCountryCodes.clients

import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.ipCountryCodes.IPCountryCodesPostgresRepository
import org.postgresql.util.PSQLException
import org.scalatest.BeforeAndAfter
import org.scalatest.OptionValues._

class IPCountryCodesRepositorySpec extends PostgresRepositorySpec with BeforeAndAfter {

  private lazy val repository = new IPCountryCodesPostgresRepository(database)

  "createCountryCode" should {
    "create a country code for an IPv4 address" in {
      val ip = "192.168.1.0"
      val countryCode = "MX"

      repository.createCountryCode(ip, countryCode)

      succeed
    }

    "create a country code for an IPv6 address" in {
      val ip = "2001:db8:3333:4444:5555:6666:7777:8888"
      val countryCode = "MX"

      repository.createCountryCode(ip, countryCode)

      succeed
    }

    "fail on duplicated ip address" in {
      val ip = "192.168.1.0"
      val countryCode = "MX"

      repository.createCountryCode(ip, countryCode)
      val error = intercept[PSQLException] {
        repository.createCountryCode(ip, countryCode)
      }

      error.getMessage mustBe s"IP $ip already exist"
    }

    "fail for an invalid ip address" in {
      val ip = "nope"
      val countryCode = "MX"

      val error = intercept[PSQLException] {
        repository.createCountryCode(ip, countryCode)
      }

      error.getMessage mustBe "ERROR: invalid input syntax for type inet: \"nope\""
    }
  }

  "findCountryCode " should {
    "find country code" in {
      val ip = "192.168.1.0"
      val countryCode = "MX"

      repository.createCountryCode(ip, countryCode)

      val result = repository.findCountryCode(ip).value
      result mustBe countryCode
    }

    "return None when ip does not exist" in {
      val ip = "192.168.1.0"

      val result = repository.findCountryCode(ip)
      result mustBe empty
    }
  }
}
