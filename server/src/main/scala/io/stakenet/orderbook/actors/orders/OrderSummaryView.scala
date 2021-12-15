package io.stakenet.orderbook.actors.orders

import io.stakenet.orderbook.models.Satoshis

import scala.collection.immutable.TreeMap

sealed trait OrderSummaryView[T <: OrderSummaryView[T]] {

  import OrderSummaryView._

  val values: TreeMap[Price, Amount]

  def copy(values: TreeMap[Price, Amount]): T

  def add(price: Price, amount: Amount): T = {
    val newAmount = values.getOrElse(price, Satoshis.Zero) + amount
    val newValues = values + (price -> newAmount)
    copy(values = newValues)
  }

  def remove(price: Price, amount: Amount): T = {
    val newAmount = values.getOrElse(price, Satoshis.Zero) - amount
    val newValues = if (newAmount == Satoshis.Zero) {
      values - price
    } else {
      values + (price -> newAmount)
    }
    copy(values = newValues)
  }
}

object OrderSummaryView {

  type Price = Satoshis
  type Amount = Satoshis

  final class SellSide(override val values: TreeMap[OrderSummaryView.Price, OrderSummaryView.Amount])
      extends OrderSummaryView[SellSide] {

    override def copy(newValues: TreeMap[Price, Amount]): SellSide = {
      new SellSide(newValues)
    }
  }

  object SellSide {

    val empty: SellSide = new SellSide(TreeMap.empty)
  }

  final class BuySide(override val values: TreeMap[OrderSummaryView.Price, OrderSummaryView.Amount])
      extends OrderSummaryView[BuySide] {

    override def copy(newValues: TreeMap[Price, Amount]): BuySide = {
      new BuySide(newValues)
    }
  }

  object BuySide {

    val empty: BuySide = new BuySide(TreeMap.empty)
  }
}
