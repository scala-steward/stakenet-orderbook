package io.stakenet.orderbook.tasks

import com.google.inject.Inject
import io.stakenet.orderbook.repositories.fees.FeesRepository
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class UnlinkAllFeesTask @Inject() (feesRepository: FeesRepository.FutureImpl)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start() = {
    logger.info("Freeing all fees...")

    feesRepository.unlinkAll().onComplete {
      case Success(_) => logger.info("All fees successfully freed")
      case Failure(exception) => logger.error(s"Failed to free fees due to: ${exception.getMessage}", exception)
    }
  }
}
