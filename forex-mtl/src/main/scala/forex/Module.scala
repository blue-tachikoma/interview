package forex

import cats.effect.{ ConcurrentEffect, Resource, Timer }
import forex.services._
import forex.programs._
import org.typelevel.log4cats.slf4j._
import forex.config._
import forex.http.HttpModule
import scala.concurrent.ExecutionContext
import dev.profunktor.redis4cats.connection.RedisClient
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import dev.profunktor.redis4cats.connection.RedisURI
import cats.effect.ContextShift
import dev.profunktor.redis4cats.log4cats._

object Module {
  def wire[F[_]: ConcurrentEffect: ContextShift: Timer](executionContext: ExecutionContext): Resource[F, Unit] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[Module.type])

    for {
      config <- Config.load[F]("app")
      redisClient <- makeRedisClient[F](config.redis)
      ratesService <- RatesServices.live[F](executionContext, config.rates.oneFrame)
      ratesProgram <- RatesProgram[F](ratesService, redisClient, config.rates.program)
      _ <- HttpModule.serve[F](config.http, executionContext, ratesProgram)
    } yield ()
  }

  private def makeRedisClient[F[_]: ConcurrentEffect: ContextShift: Logger](
      config: RedisConfig
  ): Resource[F, RedisClient] =
    for {
      uri <- Resource.eval(RedisURI.make[F](config.uri))
      client <- RedisClient[F].fromUri(uri)
    } yield client
}
