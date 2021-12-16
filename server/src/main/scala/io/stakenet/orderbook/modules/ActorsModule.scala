package io.stakenet.orderbook.modules

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Provider, Provides}
import io.stakenet.orderbook.actors.connection.ConnectionManagerActor
import io.stakenet.orderbook.actors.maintenance.PeerMessageFilterActor
import io.stakenet.orderbook.actors.orders.OrderManagerActor
import io.stakenet.orderbook.actors.persister.TradePersisterActor
import io.stakenet.orderbook.config.TradesConfig
import io.stakenet.orderbook.repositories.trades.TradesRepository
import io.stakenet.orderbook.services.OrderMatcherService
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

class ActorsModule extends AbstractModule {

  import ActorsModule._

  override def configure(): Unit = {
    bind(classOf[TradePersisterActor.Ref]).toProvider(classOf[TradePersisterActorProvider]).asEagerSingleton()
  }

  @Provides
  @Singleton
  def orderManager(
      orderMatcherService: OrderMatcherService,
      tradesConfig: TradesConfig
  )(implicit actorSystem: ActorSystem): OrderManagerActor.Ref = {
    OrderManagerActor.Ref.apply(orderMatcherService, tradesConfig)
  }

  @Provides
  @Singleton
  def peerMessageFilter()(implicit actorSystem: ActorSystem): PeerMessageFilterActor.Ref = {
    PeerMessageFilterActor.Ref.apply()
  }

  @Provides
  @Singleton
  def connectionManager()(implicit actorSystem: ActorSystem): ConnectionManagerActor.Ref = {
    ConnectionManagerActor.Ref.apply()
  }
}

object ActorsModule {

  class TradePersisterActorProvider @Inject() (
      orderManager: OrderManagerActor.Ref,
      tradesRepository: TradesRepository.FutureImpl
  )(implicit ec: ExecutionContext, actorSystem: ActorSystem)
      extends Provider[TradePersisterActor.Ref] {

    override val get: TradePersisterActor.Ref = {
      TradePersisterActor.Ref.apply(orderManager, tradesRepository)
    }
  }
}
