package io.stakenet.orderbook.modules

import akka.actor.{ActorSystem, Scheduler}
import com.google.inject.{AbstractModule, Provides}

class SchedulersModule extends AbstractModule {

  override def configure(): Unit = {}

  @Provides
  def scheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}
