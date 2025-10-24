package forex.services.rates.cache

import forex.domain.Rate

trait Algebra[F[_]] {
  def setAll(rates: List[Rate]): F[Unit]
  def get(pair: Rate.Pair): F[Option[Rate]]
  def tryAcquire: F[Boolean]
}
