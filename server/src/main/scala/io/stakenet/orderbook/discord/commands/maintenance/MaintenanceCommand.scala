package io.stakenet.orderbook.discord.commands.maintenance

sealed trait MaintenanceCommand

object MaintenanceCommand {
  case class Start() extends MaintenanceCommand
  case class Complete() extends MaintenanceCommand

  val help: String = """
                       |Valid commands:
                       |- !maintenance on
                       |- !maintenance off
                       |""".stripMargin.trim

  def seemsMaintenanceCommand(string: String): Boolean = {
    string.toLowerCase.startsWith("!maintenance")
  }

  def apply(string: String): Option[MaintenanceCommand] = {
    val parts = string.toLowerCase.split(" ").filter(_.nonEmpty)

    Option(parts.toSeq)
      .collect {
        case Seq(prefix, action) if seemsMaintenanceCommand(prefix) => action
      }
      .collect {
        case "on" => MaintenanceCommand.Start()
        case "off" => MaintenanceCommand.Complete()
      }
  }
}
