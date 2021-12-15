package io.stakenet.orderbook.repositories.ipCountryCodes

import java.sql.Connection

import anorm._
import org.postgresql.util.{PSQLException, PSQLState}

private[ipCountryCodes] object IPCountryCodesDAO {

  private object Constraints {
    val ipCountryCodesPK = "ip_country_codes_pk"
  }

  def createIpCountryCode(ip: String, countryCode: String)(implicit conn: Connection): Unit = {
    try {
      SQL"""
         INSERT INTO ip_country_codes(
           ip,
           country_code
         ) VALUES (
           $ip::INET,
           $countryCode
         )
       """.execute()

      ()
    } catch {
      case error: PSQLException if isConstraintError(error, Constraints.ipCountryCodesPK) =>
        throw new PSQLException(s"IP $ip already exist", PSQLState.DATA_ERROR)
    }
  }

  def findIpCountryCode(ip: String)(implicit conn: Connection): Option[String] = {
    SQL"""
         SELECT country_code
         FROM ip_country_codes
         WHERE ip = $ip::INET
       """.as(SqlParser.scalar[String].singleOpt)
  }

  private def isConstraintError(error: PSQLException, constraint: String): Boolean = {
    error.getServerErrorMessage.getConstraint == constraint
  }
}
