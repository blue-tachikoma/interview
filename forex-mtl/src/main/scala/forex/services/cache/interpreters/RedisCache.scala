package forex.services.cache.interpreters

import cats.effect.{ Concurrent, ContextShift, Resource, Sync }
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effects.{ SetArg, SetArgs }
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import forex.services.cache.Algebra
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import pureconfig._
import pureconfig.generic.semiauto._

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class RedisCache[F[_]](
    config: RedisCache.Config,
    instanceId: UUID,
    redisCmd: RedisCommands[F, String, String]
) extends Algebra[F] {

  def setAll(data: Map[String, String]): F[Unit] =
    redisCmd.mSet(data)

  def get(key: String): F[Option[String]] =
    redisCmd.get(key)

  def tryAcquire: F[Boolean] =
    redisCmd.set(
      config.lockKey,
      instanceId.toString(),
      SetArgs(SetArg.Existence.Nx, SetArg.Ttl.Px(config.lockTtl))
    )

}

object RedisCache {
  case class Config(
      lockKey: String,
      lockTtl: FiniteDuration
  )
  object Config {
    implicit val ratesProgramConfigReader: ConfigReader[Config] = deriveReader
  }

  def apply[F[_]: Concurrent: ContextShift: LoggerFactory](
      config: Config,
      redisClient: RedisClient
  ): Resource[F, Algebra[F]] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[RedisCache[F]])
    for {
      redisCmd <- Redis[F].fromClient(redisClient, RedisCodec.Utf8)
      instanceId <- Resource.eval(Sync[F].delay(UUID.randomUUID()))
    } yield new RedisCache[F](config, instanceId, redisCmd)
  }
}
