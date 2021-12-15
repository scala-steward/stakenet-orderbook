package io.stakenet.orderbook.helpers

import io.stakenet.orderbook.executors.DatabaseExecutionContext

import scala.concurrent.ExecutionContext

object Executors {

  implicit val globalEC: ExecutionContext = scala.concurrent.ExecutionContext.global

  implicit val databaseEC: DatabaseExecutionContext = new DatabaseExecutionContext {
    override def execute(runnable: Runnable): Unit = globalEC.execute(runnable)

    override def reportFailure(cause: Throwable): Unit = globalEC.reportFailure(cause)
  }
}
