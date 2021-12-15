package io.stakenet.orderbook.tasks

import akka.actor.ActorSystem
import io.stakenet.orderbook.lnd.MulticurrencyLndClient
import io.stakenet.orderbook.models.Currency
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.models.lnd.HubLocalBalances
import io.stakenet.orderbook.services.ClientService
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ClientStatusLoggerTask @Inject()(
    clientService: ClientService,
    lnd: MulticurrencyLndClient,
    actorSystem: ActorSystem
)(
    implicit ex: ExecutionContext
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start(): Unit = {
    logger.info("Running client status logger Task...")
    val initialDelay: FiniteDuration = 15.seconds
    val interval: FiniteDuration = 1.hour
    val _ = actorSystem.scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
      run()
    }
  }

  def run(): Unit = {
    for {
      clientIds <- getClientIdsList()
      hubChannelsLocalBalance <- getHubChannelsLocalBalance()
    } yield {
      clientIds.foreach { clientId =>
        val result = for {
          rentedCapacityUSD <- clientService.getClientRentedCapacityUSD(clientId)
          hubLocalBalanceUSD <- clientService.getClientHubLocalBalanceUSD(clientId, hubChannelsLocalBalance)
          _ <- clientService.logClientStatus(clientId, rentedCapacityUSD, hubLocalBalanceUSD)
        } yield ()

        result.recover {
          case error => logger.error(s"could log client $clientId status", error)
        }
      }
    }

    ()
  }

  private def getClientIdsList(): Future[List[ClientId]] = {
    val clients = clientService.getAllClientIds()

    clients.recover {
      case error => logger.error("Could not get clients list", error)
    }

    clients
  }

  private def getHubChannelsLocalBalance(): Future[Map[Currency, HubLocalBalances]] = {
    val emptyBalances = Map.empty[Currency, HubLocalBalances]
    val futures = Currency.forLnd.map(lnd.getClientsHubBalance)
    val clientsHubBalance = Future.foldLeft(futures)(emptyBalances) { (allBalances, currencyBalance) =>
      allBalances + (currencyBalance.currency -> currencyBalance)
    }

    clientsHubBalance.recover {
      case error => logger.error("Could not get hub's channels local balance", error)
    }

    clientsHubBalance
  }
}
