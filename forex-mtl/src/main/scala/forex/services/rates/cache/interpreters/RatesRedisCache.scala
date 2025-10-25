package forex.services.rates.cache.interpreters

import cats.MonadThrow
import cats.effect.{ Concurrent, ContextShift, Resource, Sync, Timer }
import cats.syntax.all._
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effects.{ SetArg, SetArgs }
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import forex.config.RetryConfig
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
import retry.RetryPolicies._
import retry.syntax.all._
import retry.{ RetryDetails, RetryPolicy }

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

class RatesRedisCache[F[_]: Timer: MonadThrow: Logger](
    config: RatesRedisCache.Config,
    instanceId: UUID,
    redisCmd: RedisCommands[F, String, String]
) extends Algebra[F] {

  val setAllRetryPolicy: RetryPolicy[F] =
    limitRetries[F](config.setAllRetry.maxRetries) join fullJitter[F](config.setAllRetry.baseDelay)

  val getRetryPolicy: RetryPolicy[F] =
    limitRetries[F](config.getRetry.maxRetries) join fullJitter[F](config.getRetry.baseDelay)

  val tryAcquireRetryPolicy: RetryPolicy[F] =
    limitRetries[F](config.tryAcquireRetry.maxRetries) join fullJitter[F](config.tryAcquireRetry.baseDelay)

  def setAll(rates: List[Rate]): F[Unit] =
    redisCmd
      .mSet(toKeyValue(rates))
      .handleErrorWith(convertToRedisErrorAndRaise)
      .retryingOnSomeErrors(
        isWorthRetrying = isWorthRetrying(_),
        policy = setAllRetryPolicy,
        onError = logOnRetry(_, _)
      )

  private def toKeyValue(rates: List[Rate]): Map[String, String] =
    rates.map(rate => makeKey(rate.pair) -> rate.asJson.noSpaces).toMap

  def get(pair: Rate.Pair): F[Option[Rate]] =
    redisCmd
      .get(makeKey(pair))
      .flatMap { rateOpt =>
        rateOpt.traverse(rawRate => MonadThrow[F].fromEither(decode[Rate](rawRate)))
      }
      .handleErrorWith(convertToRedisErrorAndRaise)
      .retryingOnSomeErrors(
        isWorthRetrying = isWorthRetrying(_),
        policy = getRetryPolicy,
        onError = logOnRetry(_, _)
      )

  private def makeKey(pair: Rate.Pair): String =
    s"forex:${config.ratesNamespace}:${pair.from.value}${pair.to.value}"

  def tryAcquire: F[Boolean] =
    redisCmd
      .set(
        key = s"forex:${config.ratesLockNamespace}:${config.lockKey}",
        value = instanceId.toString(),
        setArgs = SetArgs(SetArg.Existence.Nx, SetArg.Ttl.Px(config.lockTtl))
      )
      .handleErrorWith(convertToRedisErrorAndRaise)
      .retryingOnSomeErrors(
        isWorthRetrying = isWorthRetrying(_),
        policy = tryAcquireRetryPolicy,
        onError = logOnRetry(_, _)
      )

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

  private def isWorthRetrying(err: Throwable): Boolean =
    err match {
      case _: RetryableError => true
      case _                 => false
    }

  private def logOnRetry(err: Throwable, details: RetryDetails): F[Unit] =
    Logger[F].info(s"Retry attempt ${details.retriesSoFar + 1}: ${err.getMessage()}")

}

object RatesRedisCache {
  case class Config(
      ratesNamespace: String,
      ratesLockNamespace: String,
      lockKey: String,
      lockTtl: FiniteDuration,
      setAllRetry: RetryConfig,
      getRetry: RetryConfig,
      tryAcquireRetry: RetryConfig
  )
  object Config {
    implicit val ratesProgramConfigReader: ConfigReader[Config] = deriveReader
  }

  def apply[F[_]: Concurrent: ContextShift: Timer: LoggerFactory](
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
