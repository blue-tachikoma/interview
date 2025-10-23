package forex.programs.rates

import cats.Applicative
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import forex.services.CacheService

class RatesCacheServiceMock[F[_]: Sync] extends CacheService[F] {

  def setAll(data: Map[String, String]): F[Unit] =
    Applicative[F].unit

  val getMock: Ref[F, String => F[Option[String]]] =
    Ref.unsafe(_ => (new IllegalStateException("default get")).raiseError[F, Option[String]])

  def get(key: String): F[Option[String]] =
    getMock.get.flatMap(_(key))

  def tryAcquire: F[Boolean] =
    false.pure[F]

}
