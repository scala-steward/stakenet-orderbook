package io.stakenet.orderbook.models.explorer

import io.stakenet.orderbook.models.Satoshis

case class EstimatedFee(perKiloByte: Satoshis) {
  val perByte: Satoshis = perKiloByte / 1000L
}
