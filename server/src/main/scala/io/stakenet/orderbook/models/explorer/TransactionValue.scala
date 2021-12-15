package io.stakenet.orderbook.models.explorer

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class TransactionValue(address: Option[String], value: BigDecimal) {}

object TransactionValue {
  implicit val InputsReads: Reads[TransactionValue] = (
    (JsPath \ "address").readNullable[String] and
      (JsPath \ "value").read[BigDecimal]
  )(TransactionValue.apply _)
}
