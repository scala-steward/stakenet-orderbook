package io.stakenet.orderbook.discord

import java.util.UUID

import ackcord.APIMessage
import ackcord.data.{GuildChannel, Message}
import akka.actor.ActorSystem
import akka.testkit.TestKit
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.discord.commands.ExternalCommandHandler
import io.stakenet.orderbook.discord.commands.clientsStatus.ClientsStatusCommandHandler
import io.stakenet.orderbook.discord.commands.maintenance.{MaintenanceCommand, MaintenanceCommandHandler}
import io.stakenet.orderbook.discord.commands.report.ReportCommandHandler
import io.stakenet.orderbook.helpers.Executors.databaseEC
import org.mockito.Mockito
import org.mockito.MockitoSugar._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MaintenanceCommandSpec
    extends TestKit(ActorSystem("MaintenanceCommandSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "!maintenance" should {
    "start maintenance" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!maintenance on")

      commandHandler.handlePossibleCommand(channel, command)

      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, "Maintenance started")
    }

    "fail to start maintenance when one is already in progress" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!maintenance on")

      commandHandler.handlePossibleCommand(channel, command)
      commandHandler.handlePossibleCommand(channel, command)

      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, "Maintenance started")
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, "Maintenance already in progress")
    }

    "complete maintenance" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!maintenance on", "!maintenance off")

      commandHandler.handlePossibleCommand(channel, command)
      commandHandler.handlePossibleCommand(channel, command)

      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, "Maintenance started")
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, "Maintenance completed")
    }

    "fail to complete maintenance when there is no maintenance in progress" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!maintenance off")

      commandHandler.handlePossibleCommand(channel, command)

      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, "There is no maintenance in progress")
    }

    "return help when an invalid command is sent" in {
      val discordAPI = mock[DiscordAPI]
      val channel = mock[GuildChannel]
      val message = mock[Message]
      val command = mock[APIMessage.MessageCreate]
      val commandHandler = getCommandHandler(discordAPI)

      when(command.message).thenReturn(message)
      when(message.content).thenReturn("!maintenance day")

      commandHandler.handlePossibleCommand(channel, command)

      val error = s"Got unknown command, ${MaintenanceCommand.help}"
      verify(discordAPI, Mockito.timeout(1000)).sendMessage(channel, error)
    }
  }

  private def getCommandHandler(discordAPI: DiscordAPI) = {
    val maintenanceCommandHandler = new MaintenanceCommandHandler(
      PeerMessageFilterActor.Ref(s"message-filter${UUID.randomUUID()}"),
      discordAPI
    )

    new ExternalCommandHandler(
      mock[ReportCommandHandler],
      maintenanceCommandHandler,
      mock[ClientsStatusCommandHandler],
      discordAPI
    )
  }
}
