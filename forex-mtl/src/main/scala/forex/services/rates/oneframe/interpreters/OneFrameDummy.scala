package forex.services.rates.oneframe.interpreters

import cats.Applicative
import cats.syntax.applicative._
import cats.syntax.either._
import forex.domain.{ Price, Rate, Timestamp }
import forex.services.rates.oneframe.Algebra
import forex.services.rates.oneframe.errors._

class OneFrameDummy[F[_]: Applicative] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    Rate(pair, Price(BigDecimal(100)), Timestamp.now).asRight[Error].pure[F]

  override def getBatch(pairs: List[Rate.Pair]): F[Error Either List[Rate]] = ???
}
