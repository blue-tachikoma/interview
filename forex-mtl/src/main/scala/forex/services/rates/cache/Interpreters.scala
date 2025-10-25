package forex.services.rates.cache

import cats.effect.{ Concurrent, ContextShift, Resource, Timer }
import dev.profunktor.redis4cats.connection.RedisClient
import org.typelevel.log4cats.LoggerFactory

import interpreters._

object Interpreters {
  def redis[F[_]: Concurrent: ContextShift: Timer: LoggerFactory](
      config: RatesRedisCache.Config,
      client: RedisClient
  ): Resource[F, Algebra[F]] =
    RatesRedisCache(config, client)
}
