package forex.programs.rates

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import forex.domain.Rate
import forex.services.RatesService
import forex.services.rates.oneframe.errors.Error

class RatesServiceMock[F[_]: Sync] extends RatesService[F] {

  val getMock: Ref[F, Rate.Pair => F[Either[Error, Rate]]] =
    Ref.unsafe(_ =>
      (new IllegalStateException("default get"))
        .raiseError[F, Either[Error, Rate]]
    )

  def get(pair: Rate.Pair): F[Either[Error, Rate]] =
    getMock.get.flatMap(_(pair))

  def getBatch(pairs: List[Rate.Pair]): F[Either[Error, List[Rate]]] = ???

}
