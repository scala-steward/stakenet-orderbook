package io.stakenet.orderbook.services

import io.stakenet.orderbook.config.IpInfoConfig
import io.stakenet.orderbook.repositories.ipCountryCodes.IPCountryCodesRepository
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

trait IpInfoService {
  def getCountry(ip: String): Future[Either[IpInfoErrors, String]]
}

object IpInfoService {

  class IpInfoImpl @Inject() (
      ws: WSClient,
      ipInfoConfig: IpInfoConfig,
      ipCountryCodesRepository: IPCountryCodesRepository.FutureImpl
  )(implicit
      ec: ExecutionContext
  ) extends IpInfoService {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def getCountry(ip: String): Future[Either[IpInfoErrors, String]] = {
      val localIps = List("0:0:0:0:0:0:0:1", "127.0.0.1")
      if (localIps.contains(ip)) {
        Future.successful(Right("LOCAL"))
      } else {
        ipCountryCodesRepository.findCountryCode(ip).flatMap {
          case Some(countryCode) =>
            Future.successful(Right(countryCode))

          case None =>
            val request = ws.url(s"${ipInfoConfig.urlApi}/$ip/country?token=${ipInfoConfig.token}")

            request.get().map { response =>
              if (response.status == 200) {
                val countryCode = response.body.trim

                ipCountryCodesRepository.createCountryCode(ip, countryCode).recover { error =>
                  logger.error(s"Could not store country code $countryCode for $ip", error)
                }

                Right(countryCode)
              } else {
                Left(IpInfoErrors.GetCountryError(ip, response.status, response.body))
              }
            }
        }
      }
    }
  }
}

sealed trait IpInfoErrors { def getMessage: String }

object IpInfoErrors {

  case class GetCountryError(ip: String, code: Int, body: String) extends IpInfoErrors {

    override def getMessage: String =
      s"Failed to retrieve the ip info, Ip: $ip, code: $code, $body"
  }
}
