package controllers

import io.stakenet.orderbook.services.HealthService
import javax.inject.Inject
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class HealthController @Inject()(healthService: HealthService, cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def check(service: Option[String], currency: Option[String]) = Action.async { _ =>
    (service, currency) match {
      case (None, None) => Future.successful(Ok("")) // just the orderbook
      case (Some(s), Some(c)) => healthService.check(s, c).map(_ => Ok(""))
      case (Some(s), None) => healthService.check(s).map(_ => Ok(""))
      case (None, Some(_)) => Future.successful(BadRequest("Missing service"))
    }
  }
}
