package io.stakenet.orderbook.repositories.common

import com.spotify.docker.client.DefaultDockerClient
import com.whisk.docker.DockerFactory
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.must.Matchers
import play.api.db.evolutions.Evolutions
import play.api.db.{Database, Databases}

/**
 * Allow us to write integration tests depending in a postgres database.
 *
 * The database is launched in a docker instance using docker-it-scala library.
 *
 * When the database is started, play evolutions are automatically applied, the
 * idea is to let you write tests like this:
 * {{{
 *   class UserPostgresDALSpec extends PostgresRepositorySpec {
 *     lazy val dal = new UserPostgresDAL(database)
 *     ...
 *   }
 * }}}
 */
trait PostgresRepositorySpec
    extends AnyWordSpecLike
    with Matchers
    with DockerTestKit
    with DockerPostgresService
    with BeforeAndAfter
    with BeforeAndAfterAll {

  import DockerPostgresService._

  private val tables = List(
    "fee_refund_fees",
    "fee_refunds",
    "fees",
    "order_fee_invoices",
    "channel_extension_fee_payments",
    "channel_extension_requests",
    "close_expired_channel_requests",
    "channels",
    "connext_channel_extension_fee_payments",
    "connext_channel_extension_requests",
    "connext_channels",
    "channel_fee_payments",
    "partial_orders",
    "order_fee_payments",
    "channel_rental_fees",
    "channel_rental_fee_details",
    "fee_refunds_reports",
    "channel_rental_extension_fees",
    "wallet_clients",
    "client_public_keys",
    "client_info_logs",
    "currency_prices",
    "ip_country_codes",
    "client_public_identifiers",
    "maker_payments",
    "historic_trades",
    "connext_preimages",
    "liquidity_provider_logs",
    "liquidity_providers",
    "connext_channel_contract_deployment_fees"
  )

  private implicit val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(DefaultDockerClient.fromEnv().build())

  override def beforeAll(): Unit = {
    super.beforeAll()
    val _ = isContainerReady(postgresContainer).futureValue mustEqual true
  }

  before {
    clearDatabase()
  }

  lazy val database: Database = {
    val database = Databases(
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql://localhost:$PostgresExposedPort/$DatabaseName",
      name = "default",
      config = Map(
        "username" -> PostgresUsername,
        "password" -> PostgresPassword
      )
    )

    Evolutions.applyEvolutions(database)

    database
  }

  protected def clearDatabase(): Unit = {
    database.withConnection { implicit conn =>
      tables.foreach { table =>
        _root_.anorm.SQL(s"""DELETE FROM $table""").execute()
      }

      // We want to keep the bot maker clients created on 25.sql
      _root_.anorm
        .SQL("DELETE FROM clients WHERE client_id not in(SELECT client_id FROM bot_maker_clients)")
        .execute()

      ()
    }
  }
}
