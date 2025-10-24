package forex.services.rates.cache.interpreters

import cats.MonadThrow
import cats.effect.{ Concurrent, ContextShift, Resource, Sync }
import cats.syntax.all._
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effects.{ SetArg, SetArgs }
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import forex.domain.Rate
import forex.services.rates.cache.{ Algebra, FatalError, RetryableError }
import io.circe.parser._
import io.circe.syntax._
import io.lettuce.core.protocol.RedisProtocolException
import io.lettuce.core.{
  RedisCommandExecutionException,
  RedisCommandInterruptedException,
  RedisCommandTimeoutException,
  RedisConnectionException
}
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import pureconfig._
import pureconfig.generic.semiauto._

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class RatesRedisCache[F[_]: MonadThrow: Logger](
    config: RatesRedisCache.Config,
    instanceId: UUID,
    redisCmd: RedisCommands[F, String, String]
) extends Algebra[F] {

  def setAll(rates: List[Rate]): F[Unit] =
    redisCmd
      .mSet(toKeyValue(rates))
      .handleErrorWith(convertToRedisErrorAndRaise)

  private def toKeyValue(rates: List[Rate]): Map[String, String] =
    rates.map(rate => makeKey(rate.pair) -> rate.asJson.noSpaces).toMap

  def get(pair: Rate.Pair): F[Option[Rate]] =
    redisCmd
      .get(makeKey(pair))
      .flatMap { rateOpt =>
        rateOpt.traverse(rawRate => MonadThrow[F].fromEither(decode[Rate](rawRate)))
      }
      .handleErrorWith(convertToRedisErrorAndRaise)

  private def makeKey(pair: Rate.Pair): String =
    s"forex:${config.ratesNamespace}:${pair.from.value}${pair.to.value}"

  def tryAcquire: F[Boolean] =
    redisCmd
      .set(
        key = s"forex:${config.ratesNamespace}:${config.lockKey}",
        value = instanceId.toString(),
        setArgs = SetArgs(SetArg.Existence.Nx, SetArg.Ttl.Px(config.lockTtl))
      )
      .handleErrorWith(convertToRedisErrorAndRaise)

  private def convertToRedisErrorAndRaise[A](ex: Throwable): F[A] =
    ex match {
      case e @ (_: RedisConnectionException | _: RedisCommandTimeoutException) =>
        Logger[F].warn(e)(s"Retryable error occured: ${e.getMessage()}") *>
          RetryableError(e.getMessage()).raiseError[F, A]
      case e @ (_: RedisCommandExecutionException | _: RedisCommandInterruptedException | _: RedisProtocolException) =>
        Logger[F].warn(e)(s"Fatal error occured: ${e.getMessage()}") *>
          FatalError(e.getMessage()).raiseError[F, A]
      case other =>
        Logger[F].warn(other)(s"Fatal error occured: ${other.getMessage()}") *>
          FatalError(other.getMessage()).raiseError[F, A]
    }

}

object RatesRedisCache {
  case class Config(
      ratesNamespace: String,
      ratesLockNamespace: String,
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
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[RatesRedisCache[F]])
    for {
      redisCmd <- Redis[F].fromClient(redisClient, RedisCodec.Utf8)
      instanceId <- Resource.eval(Sync[F].delay(UUID.randomUUID()))
    } yield new RatesRedisCache[F](config, instanceId, redisCmd)
  }
}
