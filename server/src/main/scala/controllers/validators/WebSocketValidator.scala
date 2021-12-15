package controllers.validators

import io.stakenet.orderbook.config.{Country, FeatureFlags}
import io.stakenet.orderbook.services.IpInfoService
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class WebSocketValidator @Inject()(ipInfoService: IpInfoService, featureFlags: FeatureFlags)(
    implicit ec: ExecutionContext
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def acceptClientCountry(
      ip: String
  ): Future[Boolean] = {

    if (featureFlags.rejectBlacklistedCountries) {
      ipInfoService.getCountry(ip).map {
        case Left(error) =>
          logger.info(s"couldn't get the country for the ip :$ip,  $error")
          false
        case Right(countryCode) =>
          Country.blackList.find(_.alpha2Code == countryCode) match {
            case Some(country) =>
              logger.info(s"Rejecting client from ${country.name}")
              false
            case None =>
              logger.info(s"Accepted client from $countryCode")
              true
          }
      }
    } else {
      Future.successful(true)
    }
  }
}
