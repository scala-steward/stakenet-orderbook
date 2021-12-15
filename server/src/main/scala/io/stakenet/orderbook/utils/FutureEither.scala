package io.stakenet.orderbook.utils

import scala.concurrent.{ExecutionContext, Future}

class FutureEither[Left, Right](value: Future[Either[Left, Right]]) {

  def map[A](f: Right => A)(implicit ec: ExecutionContext): FutureEither[Left, A] = {
    FutureEither(value.map(_.map(f)))
  }

  def flatMap[A](f: Right => FutureEither[Left, A])(implicit ec: ExecutionContext): FutureEither[Left, A] = {
    FutureEither(
      value.flatMap {
        case Right(a) => f(a).toFuture
        case Left(e) => Future.successful(Left(e))
      }
    )
  }

  def toFuture: Future[Either[Left, Right]] = value
}

object FutureEither {

  def apply[Left, Right](value: Future[Either[Left, Right]]): FutureEither[Left, Right] = {
    new FutureEither(value)
  }

  def apply[Left, Right](value: Either[Left, Right]): FutureEither[Left, Right] = {
    new FutureEither(Future.successful(value))
  }
}
