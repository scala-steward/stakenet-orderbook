package io.stakenet.orderbook.discord.commands.clientsStatus

sealed trait ClientsStatusCommand

object ClientsStatusCommand {
  case class All() extends ClientsStatusCommand
  case class Green() extends ClientsStatusCommand
  case class Red() extends ClientsStatusCommand

  val help: String = """
                       |Valid commands:
                       |- !clients_status all
                       |- !clients_status green
                       |- !clients_status red
                       |""".stripMargin.trim

  def seemsClientsStatusCommand(string: String): Boolean = {
    string.toLowerCase.startsWith("!clients_status")
  }

  def apply(string: String): Option[ClientsStatusCommand] = {
    val parts = string.toLowerCase.split(" ").filter(_.nonEmpty)

    Option(parts.toSeq)
      .collect {
        case Seq(prefix, filter) if seemsClientsStatusCommand(prefix) => filter
      }
      .collect {
        case "all" => ClientsStatusCommand.All()
        case "green" => ClientsStatusCommand.Green()
        case "red" => ClientsStatusCommand.Red()
      }
  }
}
