package io.stakenet.orderbook.actors.persister

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.peers
import io.stakenet.orderbook.models.trading.TradingPair
import io.stakenet.orderbook.repositories.trades.TradesRepository

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class TradePersisterActor(orderManager: OrderManagerActor.Ref, tradesRepository: TradesRepository.FutureImpl)(implicit
    ec: ExecutionContext
) extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    log.info("Starting")
    TradingPair.values.foreach { pair =>
      orderManager.ref ! OrderManagerActor.Command.Subscribe(pair, self, retrieveOrdersSummary = false)
    }
  }

  override def receive: Receive = { case peers.protocol.Event.ServerEvent.SwapSuccess(trade) =>
    log.info(s"New swap success on ${trade.pair}, persisting...")
    tradesRepository.create(trade).onComplete {
      case Success(_) =>
      case Failure(ex) => log.error(s"Failed to persist trade = $trade", ex)
    }
  }
}

object TradePersisterActor {

  def props(orderManager: OrderManagerActor.Ref, tradesRepository: TradesRepository.FutureImpl)(implicit
      ec: ExecutionContext
  ): Props = Props(new TradePersisterActor(orderManager, tradesRepository))

  final class Ref private (val ref: ActorRef) extends AnyVal

  object Ref {

    def apply(
        orderManager: OrderManagerActor.Ref,
        tradesRepository: TradesRepository.FutureImpl
    )(implicit ec: ExecutionContext, system: ActorSystem): Ref = {
      val actor = system.actorOf(props(orderManager, tradesRepository), "trade-persister")
      new Ref(actor)
    }
  }
}
