package io.stakenet.orderbook.models

import play.api.libs.json.{JsError, JsSuccess, Reads}

import scala.util.Try

class Satoshis private (value: BigInt) extends Ordered[Satoshis] {

  def value(targetDigits: Int): BigInt = {
    if (Satoshis.Digits >= targetDigits) {
      this.value / BigInt(10).pow(Satoshis.Digits - targetDigits)
    } else {
      this.value * BigInt(10).pow(targetDigits - Satoshis.Digits)
    }
  }

  def valueFor(currency: Currency): BigInt = value(currency.digits)

  override def compare(that: Satoshis): Int = this.value.compare(that.value(Satoshis.Digits))
  def min(that: Satoshis): Satoshis = if (this < that) this else that

  override def equals(that: Any): Boolean = that match {
    case that: Satoshis => that.value(Satoshis.Digits) == this.value
    case _ => false
  }

  def equals(that: Satoshis, digits: Int): Boolean = {
    that.value(digits) == this.value(digits)
  }

  def lessThan(that: Satoshis, digits: Int): Boolean = {
    this.value(digits) < that.value(digits)
  }

  override def hashCode: Int = this.value.hashCode

  def toBigDecimal: BigDecimal = BigDecimal(value) / Satoshis.DecimalScale

  def *(that: Satoshis): Satoshis = {
    val result = new Satoshis(this.value * that.value(Satoshis.Digits) / Satoshis.Scale)
    result
  } ensuring (_ <= Satoshis.MaxValue)

  def *(that: Int): Satoshis = {
    val result = new Satoshis(this.value * that)
    result
  } ensuring (_ <= Satoshis.MaxValue)

  def *(that: Double): Satoshis = {
    val satoshis = (this.toBigDecimal * BigDecimal(that) * Satoshis.DecimalScale)

    new Satoshis(satoshis.toBigInt)
  } ensuring (_ <= Satoshis.MaxValue)

  def /(that: Satoshis): Satoshis = {
    val result = new Satoshis(this.value * Satoshis.Scale / that.value(Satoshis.Digits))
    result
  } ensuring (_ >= Satoshis.MinValue)

  def /(that: Long): Satoshis = {
    val result = new Satoshis(this.value / that)
    result
  } ensuring (_ >= Satoshis.MinValue)

  def -(that: Satoshis): Satoshis = {
    new Satoshis(this.value - that.value(Satoshis.Digits))
  } ensuring (_ >= Satoshis.MinValue)

  def +(that: Satoshis): Satoshis = {
    new Satoshis(this.value + that.value(Satoshis.Digits))
  } ensuring (_ <= Satoshis.MaxValue)

  def max(that: Satoshis): Satoshis = {
    new Satoshis(this.value.max(that.value(Satoshis.Digits)))
  }

  override def toString: String = "%.18f".format(toBigDecimal)

  def toString(currency: Currency): String = {
    s"%.${currency.digits}f %s".format(toBigDecimal, currency.toString)
  }

  def toReadableUSDValue(currency: Currency, price: BigDecimal): String = {
    "%s (%.2f USD)".format(toString(currency), toBigDecimal * price)
  }
}

object Satoshis {
  val Digits: Int = 18
  val Scale: BigInt = BigInt(10).pow(Digits)
  val DecimalScale = BigDecimal(Scale)
  val Zero = new Satoshis(0)
  val One = new Satoshis(1)
  val MaxValue: Satoshis = new Satoshis(BigInt(10).pow(40))
  val MaxValueString: String = MaxValue.toString
  val MaxValueDecimal: BigDecimal = MaxValue.toBigDecimal
  val MinValue: Satoshis = Zero

  case class InclusiveInterval(from: Satoshis, to: Satoshis) {

    def contains(amount: Satoshis): Boolean = {
      amount >= from && amount <= to
    }
  }

  def from(string: String, sourceDigits: Int): Option[Satoshis] = {
    if (string.length > MaxValueString.length) {
      None
    } else {
      val satoshis = if (Digits >= sourceDigits) {
        BigInt(string) * BigInt(10).pow(Digits - sourceDigits)
      } else {
        BigInt(string) / BigInt(10).pow(sourceDigits - Digits)
      }

      Try(from(satoshis, Digits)).toOption.flatten
    }
  }

  def from(value: BigInt, sourceDigits: Int): Option[Satoshis] = {
    if (value <= MaxValue.value(sourceDigits) && value >= MinValue.value(sourceDigits)) {
      val satoshis = if (Digits >= sourceDigits) {
        value * BigInt(10).pow(Digits - sourceDigits)
      } else {
        value / BigInt(10).pow(sourceDigits - Digits)
      }

      Some(new Satoshis(satoshis))
    } else {
      None
    }
  }

  def from(value: BigDecimal): Option[Satoshis] = {
    require(
      value.scale <= Digits,
      s"The given BigDecimal can not be converted to satoshis, it will be truncated, value = $value"
    )

    // It makes no sense to convert a huge big decimal to big int and it will just consume lots of CPU
    if (value <= MaxValueDecimal) {
      val satoshis = (value * DecimalScale).toBigInt
      from(satoshis, Digits)
    } else {
      None
    }
  }

  implicit val reads: Reads[Satoshis] = Reads { json =>
    json.validate[String].flatMap { string =>
      from(BigDecimal(string))
        .map(JsSuccess.apply(_))
        .getOrElse {
          JsError.apply("Invalid Satoshis")
        }
    }
  }
}
