package io.stakenet.orderbook.discord.commands

import ackcord.APIMessage
import ackcord.data.GuildChannel
import io.stakenet.orderbook.discord.DiscordAPI
import io.stakenet.orderbook.discord.commands.clientsStatus.{ClientsStatusCommand, ClientsStatusCommandHandler}
import io.stakenet.orderbook.discord.commands.maintenance.{MaintenanceCommand, MaintenanceCommandHandler}
import io.stakenet.orderbook.discord.commands.report.{ReportCommand, ReportCommandHandler}
import javax.inject.Inject

class ExternalCommandHandler @Inject() (
    reportCommandHandler: ReportCommandHandler,
    maintenanceCommandHandler: MaintenanceCommandHandler,
    clientsStatusCommandHandler: ClientsStatusCommandHandler,
    discordAPI: DiscordAPI
) {

  def handlePossibleCommand(channel: GuildChannel, msg: APIMessage.MessageCreate): Unit = {
    val command = msg.message.content

    if (ReportCommand.seemsCommand(command)) {
      ReportCommand(command)
        .orElse {
          val reply = s"Got unknown command, ${ReportCommand.help}"
          discordAPI.sendMessage(channel, reply)
          None
        }
        .foreach { cmd =>
          reportCommandHandler.process(channel, cmd)
        }
    } else if (MaintenanceCommand.seemsMaintenanceCommand(command)) {
      MaintenanceCommand
        .apply(command)
        .orElse {
          val reply = s"Got unknown command, ${MaintenanceCommand.help}"
          discordAPI.sendMessage(channel, reply)
          None
        }
        .foreach { cmd =>
          maintenanceCommandHandler.process(channel, cmd)
        }
    } else if (ClientsStatusCommand.seemsClientsStatusCommand(command)) {
      ClientsStatusCommand(command)
        .orElse {
          val reply = s"Got unknown command, ${ClientsStatusCommand.help}"
          discordAPI.sendMessage(channel, reply)
          None
        }
        .foreach { cmd =>
          clientsStatusCommandHandler.process(channel, cmd)
        }
    } else {
      ()
    }
  }
}
