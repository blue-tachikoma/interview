package forex.programs.rates

import cats.Applicative
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import forex.domain.Rate
import forex.services.CacheService

class RatesCacheServiceMock[F[_]: Sync] extends CacheService[F] {

  def setAll(rates: List[Rate]): F[Unit] =
    Applicative[F].unit

  val getMock: Ref[F, Rate.Pair => F[Option[Rate]]] =
    Ref.unsafe(_ => (new IllegalStateException("default get")).raiseError[F, Option[Rate]])

  def get(pair: Rate.Pair): F[Option[Rate]] =
    getMock.get.flatMap(_(pair))

  def tryAcquire: F[Boolean] =
    false.pure[F]

}
