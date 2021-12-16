package io.stakenet.orderbook.repositories.ipCountryCodes

import javax.inject.Inject
import play.api.db.Database

class IPCountryCodesPostgresRepository @Inject() (database: Database) extends IPCountryCodesRepository.Blocking {

  override def createCountryCode(ip: String, countryCode: String): Unit = {
    database.withConnection { implicit connection =>
      IPCountryCodesDAO.createIpCountryCode(ip, countryCode)
    }
  }

  override def findCountryCode(ip: String): Option[String] = {
    database.withConnection { implicit connection =>
      IPCountryCodesDAO.findIpCountryCode(ip)
    }
  }
}
