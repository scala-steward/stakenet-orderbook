package io.stakenet.orderbook.utils

import scala.concurrent.Future

object Extensions {
  implicit class OptionExt[A](val value: Option[A]) extends AnyVal {
    def getOrThrow(msg: String): A = value.getOrElse(throw new RuntimeException(msg))
  }

  implicit class FutureEitherExt[Left, Right](val value: Future[Either[Left, Right]]) extends AnyVal {
    def toFutureEither(): FutureEither[Left, Right] = FutureEither(value)
  }

  implicit class EitherExt[Left, Right](val value: Either[Left, Right]) extends AnyVal {
    def toFutureEither(): FutureEither[Left, Right] = FutureEither(value)
  }
}
