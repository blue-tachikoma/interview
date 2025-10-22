package forex.services.rates.oneframe

import cats.Applicative
import cats.effect.{ ConcurrentEffect, Resource }
import org.typelevel.log4cats.LoggerFactory

import scala.concurrent.ExecutionContext

import interpreters._

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = new OneFrameDummy[F]()
  def live[F[_]: ConcurrentEffect: LoggerFactory](
      executionContext: ExecutionContext,
      config: OneFrameLive.Config
  ): Resource[F, Algebra[F]] =
    OneFrameLive.make(executionContext, config)
}
