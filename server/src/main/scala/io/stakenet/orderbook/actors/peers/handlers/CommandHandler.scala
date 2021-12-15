package io.stakenet.orderbook.actors.peers.handlers

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import io.stakenet.orderbook.actors.peers.protocol.{Command, Event, TaggedCommandResponse}
import io.stakenet.orderbook.actors.peers.{PeerState, PeerUser}
import kamon.Kamon

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class CommandContext(requestId: String, cmd: Command, self: ActorRef, peerUser: PeerUser, state: PeerState)

trait CommandHandler[C <: Command] {

  import CommandHandler._

  protected implicit val defaultTimeout: Timeout = Timeout(30.seconds)

  def handle(cmd: C)(implicit ctx: CommandContext, log: LoggingAdapter): Result

  protected def processResponse(
      response: => Event.CommandResponse
  )(implicit ctx: CommandContext, log: LoggingAdapter): Unit = {
    val timer = createTimer.start()
    val start = System.currentTimeMillis()

    try {
      val tagged = TaggedCommandResponse(ctx.requestId, response)
      timer.stop()
      val took = System.currentTimeMillis() - start

      ctx.self ! tagged
      logResult(timeTaken = took, response = response)
    } catch {
      case NonFatal(_) =>
        val took = System.currentTimeMillis() - start
        timer.stop()

        logResult(timeTaken = took, response = response)
        val tagged = TaggedCommandResponse(ctx.requestId, Event.CommandResponse.CommandFailed.Reason("Unknown error"))
        ctx.self ! tagged
    }
  }

  protected def processResponseF(
      f: => Future[Event.CommandResponse]
  )(implicit ec: ExecutionContext, ctx: CommandContext, log: LoggingAdapter): Result = {
    val timer = createTimer.start()
    val start = System.currentTimeMillis()

    f.recover {
        case timeoutError: AskTimeoutException =>
          log.warning(s"An error occurred on the command response", timeoutError)
          Event.CommandResponse.CommandFailed.Reason(
            s"A timeout error occurred, please try again later"
          )
        case NonFatal(ex) => Event.CommandResponse.CommandFailed.Reason(ex.getMessage)
      }
      .foreach { response =>
        timer.stop()
        val took = System.currentTimeMillis() - start
        val tagged = TaggedCommandResponse(ctx.requestId, response)
        ctx.self ! tagged

        logResult(timeTaken = took, response = response)
      }

    Result.Async
  }

  private def logResult(timeTaken: Long, response: Event)(implicit ctx: CommandContext, log: LoggingAdapter): Unit = {
    val result = response.toString.take(200)
    val msg =
      s"${ctx.peerUser.name}: Request ${ctx.cmd.toString.take(200)} with id = ${ctx.requestId}, took $timeTaken ms, result = $result"
    if (msg.contains("Request Ping")) {
      log.debug(msg)
    } else {
      log.info(msg)
    }
  }

  private def createTimer(implicit ctx: CommandContext) = {
    Kamon
      .timer(name = "command-latency", description = "The time taken to process commands")
      .withTag("command", ctx.cmd.getClass.getSimpleName)
      .withTag("client-type", ctx.peerUser.getClass.getSimpleName)
//      .withTag("client-name", ctx.peerUser.name)
//      .withTag("request-id", ctx.requestId)
  }
}

object CommandHandler {
  sealed trait Result

  object Result {
    final case object Async extends Result
    final case class StateUpdated(newState: PeerState) extends Result
  }
}
