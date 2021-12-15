package io.stakenet.orderbook.discord.commands.report

import scala.concurrent.duration._

case class ReportCommand(period: FiniteDuration, summary: Boolean)

object ReportCommand {

  def seemsReportCommand(string: String): Boolean = {
    string.toLowerCase.startsWith("!report")
  }

  def seemsSummaryCommand(string: String): Boolean = {
    string.toLowerCase.startsWith("!summary")
  }

  def seemsCommand(string: String): Boolean = {
    seemsReportCommand(string) || seemsSummaryCommand(string)
  }

  val help: String = """
                       |Valid commands:
                       |- !report day
                       |- !report week
                       |- !report month
                       |- !summary day
                       |- !summary week
                       |- !summary month
                       |""".stripMargin.trim

  def apply(string: String): Option[ReportCommand] = {
    val parts = string.toLowerCase.split(" ").filter(_.nonEmpty)
    Option(parts.toSeq)
      .collect {
        case Seq(prefix, periodStr) if seemsCommand(prefix) =>
          periodStr -> seemsSummaryCommand(prefix)
      }
      .collect {
        case ("day", summary) => new ReportCommand(1.day, summary)
        case ("week", summary) => new ReportCommand(7.days, summary)
        case ("month", summary) => new ReportCommand(28.days, summary)
      }
  }
}
