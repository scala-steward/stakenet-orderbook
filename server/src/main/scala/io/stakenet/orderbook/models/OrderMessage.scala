package io.stakenet.orderbook.models

case class OrderMessage(message: Vector[Byte], orderId: OrderId)
