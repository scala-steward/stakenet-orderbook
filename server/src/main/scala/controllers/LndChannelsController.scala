package controllers

import io.stakenet.orderbook.lnd.{LightningConfigBuilder, LndConfig}
import io.stakenet.orderbook.models.Currency
import javax.inject.Inject
import play.api.libs.json.{JsString, Json, Writes}
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class LndChannelsController @Inject()(cc: ControllerComponents, configBuilder: LightningConfigBuilder)(
    implicit ec: ExecutionContext
) extends AbstractController(cc) {

  import LndChannelsController._

  def get() = Action.async { _ =>
    Future {
      val values = configBuilder.getAll
      val json = Json.obj("data" -> Json.toJson(values))
      Ok(json)
    }
  }
}

object LndChannelsController {
  implicit val writes: Writes[LndConfig] = (obj: LndConfig) => {
    Json.obj("publicKey" -> obj.publicKey, "ipAddress" -> obj.channelIpAddress, "port" -> obj.port)
  }

  implicit val currencyWrites: Writes[Currency] = obj => JsString(obj.entryName)
}
