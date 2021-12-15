package io.stakenet.orderbook.discord.commands.maintenance

import ackcord.data.GuildChannel
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.Inject
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.discord.DiscordAPI
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MaintenanceCommandHandler @Inject()(messageFilter: PeerMessageFilterActor.Ref, discordAPI: DiscordAPI)(
    implicit ec: ExecutionContext
) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def process(channel: GuildChannel, command: MaintenanceCommand): Unit = {
    implicit val timeout: Timeout = Timeout(3.seconds)

    command match {
      case MaintenanceCommand.Start() =>
        ask(messageFilter.ref, PeerMessageFilterActor.Command.StartMaintenance())
          .mapTo[PeerMessageFilterActor.StartMaintenanceResponse]
          .onComplete {
            case Success(PeerMessageFilterActor.StartMaintenanceResponse.MaintenanceStarted()) =>
              discordAPI.sendMessage(channel, "Maintenance started")
            case Success(PeerMessageFilterActor.StartMaintenanceResponse.MaintenanceAlreadyInProgress()) =>
              discordAPI.sendMessage(channel, "Maintenance already in progress")
            case Failure(exception) =>
              logger.error("Could not start maintenance", exception)
              discordAPI.sendMessage(channel, s"Could not start maintenance due to: ${exception.getMessage}")
          }

      case MaintenanceCommand.Complete() =>
        ask(messageFilter.ref, PeerMessageFilterActor.Command.CompleteMaintenance())
          .mapTo[PeerMessageFilterActor.CompleteMaintenanceResponse]
          .onComplete {
            case Success(PeerMessageFilterActor.CompleteMaintenanceResponse.MaintenanceCompleted()) =>
              discordAPI.sendMessage(channel, "Maintenance completed")
            case Success(PeerMessageFilterActor.CompleteMaintenanceResponse.NoMaintenanceInProgress()) =>
              discordAPI.sendMessage(channel, "There is no maintenance in progress")
            case Failure(exception) =>
              logger.error("Could not complete maintenance", exception)
              discordAPI.sendMessage(channel, s"Could not complete maintenance due to: ${exception.getMessage}")
          }
    }
  }
}
