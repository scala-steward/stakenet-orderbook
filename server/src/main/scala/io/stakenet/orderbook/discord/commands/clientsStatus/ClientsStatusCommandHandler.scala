package io.stakenet.orderbook.discord.commands.clientsStatus

import ackcord.data.GuildChannel
import com.google.inject.Inject
import io.stakenet.orderbook.discord.DiscordAPI
import io.stakenet.orderbook.models.reports.ClientStatus
import io.stakenet.orderbook.repositories.reports.ReportsRepository
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ClientsStatusCommandHandler @Inject() (reportsRepository: ReportsRepository.FutureImpl, discordAPI: DiscordAPI)(
    implicit ec: ExecutionContext
) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def process(channel: GuildChannel, command: ClientsStatusCommand): Unit = {
    val report = command match {
      case ClientsStatusCommand.All() =>
        reportsRepository.getClientsStatusReport().map(renderReport)

      case ClientsStatusCommand.Green() =>
        reportsRepository.getClientsStatusReport().map(_.filter(_.isGreen)).map(renderReport)

      case ClientsStatusCommand.Red() =>
        reportsRepository.getClientsStatusReport().map(_.filter(_.isRed)).map(renderReport)
    }

    report.onComplete {
      case Success(report) =>
        discordAPI.sendMessage(channel, report)

      case Failure(exception) =>
        logger.error(s"Failed to generate clients status report", exception)
        discordAPI.sendMessage(channel, s"Failed to generate report: ${exception.getMessage}")
    }
  }

  private def renderReport(clientsStatus: List[ClientStatus]): String = {
    val header =
      """                                              Clients status report                                               
        |
        | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
        |               Client id                 Client type   Rented capacity   Hub local balance   Status   Status Time  
        | -------------------------------------- ------------- ----------------- ------------------- -------- -------------""".stripMargin

    val body = clientsStatus.foldLeft("") { (result, client) =>
      val id = s" ${client.clientId} "
      val clientType = s" ${client.clientType.padTo(12, ' ')}"
      val rentedCapacity = "%12.2f USD ".format(client.rentedCapacityUSD)
      val hubLocalBalance = "%14.2f USD ".format(client.hubLocalBalanceUSD)
      val status = if (client.isGreen) " Green  " else " Red    "
      val statusTime = s" ${Duration(client.time.toDays, DAYS)}"

      result + s" $id $clientType $rentedCapacity $hubLocalBalance $status $statusTime \n"
    }

    val totalRentedCapacity = "%.2f USD".format(clientsStatus.map(_.rentedCapacityUSD).sum)
    val totalHubLocalBalance = "%.2f USD".format(clientsStatus.map(_.hubLocalBalanceUSD).sum)

    val footer =
      s"""
        |- Total clients: ${clientsStatus.length}
        |- Green clients: ${clientsStatus.count(_.isGreen)}
        |- Red clients: ${clientsStatus.count(_.isRed)}
        |- Total rented capacity: $totalRentedCapacity
        |- Total hub local balance: $totalHubLocalBalance""".stripMargin

    s"""```
      |$header
      |$body
      |$footer
      |```""".stripMargin
  }
}
