package forex

import cats.Parallel
import cats.effect.{ ConcurrentEffect, ContextShift, Resource, Timer }
import dev.profunktor.redis4cats.connection.{ RedisClient, RedisURI }
import dev.profunktor.redis4cats.log4cats._
import forex.config._
import forex.http.server.HttpServerModule
import forex.programs._
import forex.services._
import org.typelevel.log4cats.slf4j._
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext

object Module {
  def wire[F[_]: ConcurrentEffect: ContextShift: Timer: Parallel](
      executionContext: ExecutionContext
  ): Resource[F, Unit] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[Module.type])

    for {
      config <- Config.load[F]("app")
      redisClient <- makeRedisClient[F](config.redis)
      ratesService <- RatesServices.live[F](executionContext, config.rates.oneframe)
      cacheService <- CacheServices.redis[F](config.rates.cache, redisClient)
      ratesProgram <- RatesProgram[F](ratesService, cacheService, config.rates.program)
      _ <- HttpServerModule.makeAndServe[F](config.http, executionContext, ratesProgram)
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
