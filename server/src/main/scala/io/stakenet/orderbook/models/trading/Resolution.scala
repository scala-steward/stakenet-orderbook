package io.stakenet.orderbook.models.trading

/*
  Trading view works with the following resolutions:
  number-> minutes
  D  -> Days
  W  -> Weeks
  M  -> Months

  Trading view does not uses Hours, you have to send hours in minutes
 */

case class Resolution(months: Int = 0, weeks: Int = 0, days: Int = 0, minutes: Int = 0) {
  require(List(months > 0, weeks > 0, days > 0, minutes > 0).count(identity) == 1)
  override def toString: String = {
    if (months > 0) s"${months}M"
    else if (weeks > 0) s"${weeks}W"
    else if (days > 0) s"${days}D"
    else minutes.toString

  }
}

object Resolution {

  def from(value: String): Option[Resolution] = {
    require(value.trim.length < 4 && value.trim.length > 0)
    try {
      val num =
        if (isNumber(value)) value.trim.toInt
        else if (isLetter(value)) 1
        else value.trim.init.toInt

      val char = if (isNumber(value)) 'N' else value.trim.toUpperCase.last

      char match {
        case 'M' => Some(Resolution(months = num))
        case 'W' => Some(Resolution(weeks = num))
        case 'D' => Some(Resolution(days = num))
        case 'N' => Some(Resolution(minutes = num))
        case _ => None
      }
    } catch {
      case _: NumberFormatException => None
    }

  }

  private def isNumber(value: String): Boolean = {
    !value.isEmpty && value.forall(Character.isDigit)
  }

  private def isLetter(value: String): Boolean = {
    !value.isEmpty && value.forall(Character.isLetter) && value.length == 1
  }
}
