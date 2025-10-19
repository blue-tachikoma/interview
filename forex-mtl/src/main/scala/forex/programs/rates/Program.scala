package forex.programs.rates

import cats.data.EitherT
import cats.syntax.all._
import errors._
import forex.domain._
import forex.services.RatesService
import cats.effect.Resource
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import dev.profunktor.redis4cats.connection.RedisClient
import cats.effect.Concurrent
import cats.effect.ContextShift
import dev.profunktor.redis4cats.log4cats._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import dev.profunktor.redis4cats.data.RedisCodec
import cats.Monad
import io.circe.parser._
import io.circe.syntax._
import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.semiauto._
import forex.programs.rates.Program.Config

class Program[F[_]: Monad](
    ratesService: RatesService[F],
    redisCmd: RedisCommands[F, String, String],
    config: Config
) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] = {
    val key = request.from.value + request.to.value
    redisCmd.get(key).flatMap {
      case Some(value) =>
        decode[Rate](value)
          .leftMap[Error](e => Error.RateLookupFailed(e.getMessage))
          .pure[F]
      case None =>
        EitherT(ratesService.get(Rate.Pair(request.from, request.to)))
          .leftMap(toProgramError)
          .semiflatTap(rate => redisCmd.setEx(key, rate.asJson.noSpaces, config.cacheTtl))
          .value
    }
  }

}

object Program {
  case class Config(
      cacheTtl: FiniteDuration
  )
  object Config {
    implicit val ratesProgramConfigReader: ConfigReader[Config] = deriveReader
  }

  def apply[F[_]: Concurrent: ContextShift: LoggerFactory](
      ratesService: RatesService[F],
      redisClient: RedisClient,
      config: Config
  ): Resource[F, Algebra[F]] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[Program[F]])
    Redis[F]
      .fromClient(redisClient, RedisCodec.Utf8)
      .map(redisCmd => new Program[F](ratesService, redisCmd, config))
  }

}
