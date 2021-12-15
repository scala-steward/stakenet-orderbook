package io.stakenet.orderbook.discord

import java.time.Instant
import java.util.UUID

import ackcord.APIMessage
import ackcord.data.{GuildChannel, Message}
import io.stakenet.orderbook.discord.commands.ExternalCommandHandler
import io.stakenet.orderbook.discord.commands.clientsStatus.ClientsStatusCommandHandler
import io.stakenet.orderbook.discord.commands.maintenance.MaintenanceCommandHandler
import io.stakenet.orderbook.discord.commands.report.ReportCommandHandler
import io.stakenet.orderbook.helpers.Executors.databaseEC
import io.stakenet.orderbook.models.clients.ClientId
import io.stakenet.orderbook.repositories.clients.ClientsPostgresRepository
import io.stakenet.orderbook.repositories.common.PostgresRepositorySpec
import io.stakenet.orderbook.repositories.reports.{ReportsPostgresRepository, ReportsRepository}
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar._
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.matchers.must.Matchers

import scala.concurrent.duration._

class ClientsStatusCommandSpec extends PostgresRepositorySpec with Matchers {

  private lazy val reportsRepository = new ReportsPostgresRepository(database)

  "!clients_status" should {
    "get all clients" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!clients_status all")

      val clientId1 = ClientId(UUID.fromString("11b4e5a8-68a0-4bdf-b11e-e52d39804fbe"))
      val clientId2 = ClientId(UUID.fromString("a1f4c407-836e-4b8d-8d1d-6668287c5d32"))
      val clientId3 = ClientId(UUID.fromString("d361540d-b5bf-4ac3-93e8-693d0f8fe6bd"))

      val today = Instant.now

      logClientInfo(clientId1, 100, 100, today)
      logClientInfo(clientId1, 100, 100, today.minusSeconds(1.day.toSeconds))
      logClientInfo(clientId2, 50, 100, today)
      logClientInfo(clientId2, 50, 100, today.minusSeconds(3.days.toSeconds))
      logClientInfo(clientId3, 150, 0, today)
      logClientInfo(clientId3, 150, 0, today.minusSeconds(2.days.toSeconds))

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """```
          |                                              Clients status report                                               
          |
          | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
          |               Client id                 Client type   Rented capacity   Hub local balance   Status   Status Time  
          | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
          |  11b4e5a8-68a0-4bdf-b11e-e52d39804fbe   Bot                100.00 USD          100.00 USD   Green    1 day 
          |  a1f4c407-836e-4b8d-8d1d-6668287c5d32   Bot                 50.00 USD          100.00 USD   Red      3 days 
          |  d361540d-b5bf-4ac3-93e8-693d0f8fe6bd   Bot                150.00 USD            0.00 USD   Green    2 days 
          |
          |
          |- Total clients: 3
          |- Green clients: 2
          |- Red clients: 1
          |- Total rented capacity: 300.00 USD
          |- Total hub local balance: 200.00 USD
          |```""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }

    "get only green clients" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!clients_status green")

      val clientId1 = ClientId(UUID.fromString("11b4e5a8-68a0-4bdf-b11e-e52d39804fbe"))
      val clientId2 = ClientId(UUID.fromString("a1f4c407-836e-4b8d-8d1d-6668287c5d32"))
      val clientId3 = ClientId(UUID.fromString("d361540d-b5bf-4ac3-93e8-693d0f8fe6bd"))

      val today = Instant.now

      logClientInfo(clientId1, 100, 100, today)
      logClientInfo(clientId1, 100, 100, today.minusSeconds(1.day.toSeconds))
      logClientInfo(clientId2, 50, 100, today)
      logClientInfo(clientId2, 50, 100, today.minusSeconds(3.days.toSeconds))
      logClientInfo(clientId3, 150, 0, today)
      logClientInfo(clientId3, 150, 0, today.minusSeconds(2.days.toSeconds))

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """```
          |                                              Clients status report                                               
          |
          | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
          |               Client id                 Client type   Rented capacity   Hub local balance   Status   Status Time  
          | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
          |  11b4e5a8-68a0-4bdf-b11e-e52d39804fbe   Bot                100.00 USD          100.00 USD   Green    1 day 
          |  d361540d-b5bf-4ac3-93e8-693d0f8fe6bd   Bot                150.00 USD            0.00 USD   Green    2 days 
          |
          |
          |- Total clients: 2
          |- Green clients: 2
          |- Red clients: 0
          |- Total rented capacity: 250.00 USD
          |- Total hub local balance: 100.00 USD
          |```""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }

    "get red clients" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!clients_status red")

      val clientId1 = ClientId(UUID.fromString("11b4e5a8-68a0-4bdf-b11e-e52d39804fbe"))
      val clientId2 = ClientId(UUID.fromString("a1f4c407-836e-4b8d-8d1d-6668287c5d32"))
      val clientId3 = ClientId(UUID.fromString("d361540d-b5bf-4ac3-93e8-693d0f8fe6bd"))

      val today = Instant.now

      logClientInfo(clientId1, 100, 100, today)
      logClientInfo(clientId1, 100, 100, today.minusSeconds(1.day.toSeconds))
      logClientInfo(clientId2, 50, 100, today)
      logClientInfo(clientId2, 50, 100, today.minusSeconds(3.days.toSeconds))
      logClientInfo(clientId3, 150, 0, today)
      logClientInfo(clientId3, 150, 0, today.minusSeconds(2.days.toSeconds))

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """```
          |                                              Clients status report                                               
          |
          | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
          |               Client id                 Client type   Rented capacity   Hub local balance   Status   Status Time  
          | -------------------------------------- ------------- ----------------- ------------------- -------- -------------
          |  a1f4c407-836e-4b8d-8d1d-6668287c5d32   Bot                 50.00 USD          100.00 USD   Red      3 days 
          |
          |
          |- Total clients: 1
          |- Green clients: 0
          |- Red clients: 1
          |- Total rented capacity: 50.00 USD
          |- Total hub local balance: 100.00 USD
          |```""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }

    "fail with unknown option" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!clients_status nope")

      commandHandler.handlePossibleCommand(channel, command)

      val expected =
        """Got unknown command, Valid commands:
          |- !clients_status all
          |- !clients_status green
          |- !clients_status red
          |""".stripMargin

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(eqTo(channel), captor.capture())

      val expectedLines = expected.linesIterator.toList
      val actualLines = captor.getValue.linesIterator.toList

      expectedLines.zip(actualLines).foreach {
        case (expectedLine, actualLine) => actualLine mustBe expectedLine
      }
    }
  }

  private def getCommandHandler(discordAPI: DiscordAPI) = {
    val clientsStatusCommandHandler = new ClientsStatusCommandHandler(
      new ReportsRepository.FutureImpl(reportsRepository),
      discordAPI
    )

    new ExternalCommandHandler(
      mock[ReportCommandHandler],
      mock[MaintenanceCommandHandler],
      clientsStatusCommandHandler,
      discordAPI
    )
  }

  private def logClientInfo(
      clientId: ClientId,
      rentedCapacityUSD: BigDecimal,
      hubLocalBalanceUSD: BigDecimal,
      date: Instant
  ): Unit = {
    val clientsRepository = new ClientsPostgresRepository(database)

    clientsRepository.logClientInfo(clientId, rentedCapacityUSD, hubLocalBalanceUSD, date)
  }
}
