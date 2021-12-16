package io.stakenet.orderbook.repositories.ipCountryCodes

import io.stakenet.orderbook.executors.DatabaseExecutionContext
import javax.inject.Inject

import scala.concurrent.Future

trait IPCountryCodesRepository[F[_]] {
  def createCountryCode(ip: String, countryCode: String): F[Unit]
  def findCountryCode(ip: String): F[Option[String]]
}

object IPCountryCodesRepository {

  type Id[T] = T
  trait Blocking extends IPCountryCodesRepository[Id]

  class FutureImpl @Inject() (blocking: Blocking)(implicit ec: DatabaseExecutionContext)
      extends IPCountryCodesRepository[scala.concurrent.Future] {

    override def createCountryCode(ip: String, countryCode: String): Future[Unit] = Future {
      blocking.createCountryCode(ip, countryCode)
    }

    override def findCountryCode(ip: String): Future[Option[String]] = Future {
      blocking.findCountryCode(ip)
    }
  }
}
