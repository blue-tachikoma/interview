package forex.services.cache

import cats.effect.{ Concurrent, ContextShift, Resource }
import dev.profunktor.redis4cats.connection.RedisClient
import org.typelevel.log4cats.LoggerFactory

import interpreters._

object Interpreters {
  def redis[F[_]: Concurrent: ContextShift: LoggerFactory](
      config: RedisCache.Config,
      client: RedisClient
  ): Resource[F, Algebra[F]] =
    RedisCache(config, client)
}
