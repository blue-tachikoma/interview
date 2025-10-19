package forex.services.rates

import cats.Applicative
import interpreters._
import cats.effect.{ ConcurrentEffect, Resource }
import scala.concurrent.ExecutionContext

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = new OneFrameDummy[F]()
  def live[F[_]: ConcurrentEffect](
      executionContext: ExecutionContext,
      config: OneFrameLive.Config
  ): Resource[F, Algebra[F]] =
    OneFrameLive.make(executionContext, config)
}
