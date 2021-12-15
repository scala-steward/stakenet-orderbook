package io.stakenet.orderbook

import akka.actor.ActorRef
import io.stakenet.orderbook.models.Currency

import scala.concurrent.duration.FiniteDuration

package object actors {
  sealed trait ActorMessage
  final case class InstantMessage(destination: ActorRef, message: AnyRef) extends ActorMessage
  final case class ScheduledMessage(destination: ActorRef, message: AnyRef, delay: FiniteDuration) extends ActorMessage

  final case class UpdateStateResult[S](newState: S, messages: List[ActorMessage])
  final case class Subscriptors(currency: Currency, peers: List[ActorRef])

}
