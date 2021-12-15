package controllers.validators

import io.stakenet.orderbook.config.FeatureFlags
import io.stakenet.orderbook.helpers.Executors.databaseEC
import io.stakenet.orderbook.services.{IpInfoErrors, IpInfoService}
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future

class WebSocketValidatorSpec extends AnyWordSpec with Matchers {
  "acceptClientCountry" should {

    "return true if the flag is disabled" in {
      val ipInfoService = mock[IpInfoService]
      val featureFlags = FeatureFlags(feesEnabled = true, rejectBlacklistedCountries = false)
      val validator = getValidator(ipInfoService, featureFlags)
      val ip = "80.80.80.80"
      when(ipInfoService.getCountry(ip)).thenReturn(Future.successful(Right("US")))
      val response = validator.acceptClientCountry(ip)
      response.map { result =>
        result mustBe true
      }
    }

    "return true if the country is not in the blackList" in {
      val ipInfoService = mock[IpInfoService]
      val featureFlags = FeatureFlags(feesEnabled = true, rejectBlacklistedCountries = true)
      val validator = getValidator(ipInfoService, featureFlags)
      val ip = "80.80.80.80"
      when(ipInfoService.getCountry(ip)).thenReturn(Future.successful(Right("MX")))
      val response = validator.acceptClientCountry(ip)

      response.map { result =>
        result mustBe true
      }
    }

    "return false if the country is not found" in {
      val ipInfoService = mock[IpInfoService]
      val featureFlags = FeatureFlags(feesEnabled = true, rejectBlacklistedCountries = true)
      val validator = getValidator(ipInfoService, featureFlags)
      val ip = "80.80.80.80"
      val ipInfoError = IpInfoErrors.GetCountryError(ip, 404, "not found")
      when(ipInfoService.getCountry(ip)).thenReturn(Future.successful(Left(ipInfoError)))
      val response = validator.acceptClientCountry(ip)

      response.map { result =>
        result mustBe false
      }
    }

    "return false if the country is in the black list" in {
      val ipInfoService = mock[IpInfoService]
      val featureFlags = FeatureFlags(feesEnabled = true, rejectBlacklistedCountries = false)
      val validator = getValidator(ipInfoService, featureFlags)
      val ip = "80.80.80.80"
      when(ipInfoService.getCountry(ip)).thenReturn(Future.successful(Right("US")))
      val response = validator.acceptClientCountry(ip)

      response.map { result =>
        result mustBe false
      }
    }
  }

  private def getValidator(ipInfoService: IpInfoService, featureFlags: FeatureFlags) = {
    new WebSocketValidator(ipInfoService, featureFlags)
  }
}
