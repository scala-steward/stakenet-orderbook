package io.stakenet.orderbook.services

import enumeratum._
import io.stakenet.orderbook.executors.DatabaseExecutionContext
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.models.Currency
import javax.inject.Inject
import play.api.db.Database

import scala.concurrent.Future

class HealthService @Inject()(databaseHealthChecker: HealthService.DatabaseHealthChecker, lnd: MulticurrencyLndClient) {

  import HealthService._

  def check(serviceString: String): Future[Unit] = {
    Service.withNameInsensitiveOption(serviceString) match {
      case Some(Service.Database) => databaseHealthChecker.check()
      case Some(Service.Lnd) => Future.failed(new RuntimeException("Missing currency"))
      case None => Future.failed(new RuntimeException("Unknown service"))
    }
  }

  def check(serviceString: String, currencyString: String): Future[Unit] = {
    val result = for {
      service <- Service.withNameInsensitiveOption(serviceString)
      currency <- Currency.withNameInsensitiveOption(currencyString)
    } yield service match {
      case Service.Database => databaseHealthChecker.check()
      case Service.Lnd => lnd.healthCheck(currency)
    }

    result.getOrElse(Future.failed(new RuntimeException("Unknown service or currency")))
  }
}

object HealthService {
  sealed trait Service extends EnumEntry

  object Service extends Enum[Service] {
    val values = findValues

    final case object Database extends Service
    final case object Lnd extends Service
  }

  class DatabaseHealthChecker @Inject()(database: Database)(implicit ec: DatabaseExecutionContext) {
    import anorm._

    def check(): Future[Unit] = Future {
      database.withConnection { implicit connection =>
        val _ = SQL("""SELECT 1""").as(SqlParser.scalar[Int].single)
      }
    }
  }
}
