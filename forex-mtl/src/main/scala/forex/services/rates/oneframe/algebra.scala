package forex.services.rates.oneframe

import forex.domain.Rate

import errors._

trait Algebra[F[_]] {
  def get(pair: Rate.Pair): F[Either[Error, Rate]]
  def getBatch(pairs: List[Rate.Pair]): F[Either[Error, List[Rate]]]
}
