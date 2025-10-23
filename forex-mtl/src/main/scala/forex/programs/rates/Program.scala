package forex.programs.rates

import cats.Parallel
import cats.effect.concurrent.Supervisor
import cats.effect.{ Concurrent, Resource, Sync, Timer }
import cats.syntax.all._
import forex.domain._
import forex.programs.rates.Program.Config
import forex.programs.rates.errors.Error.ValidationError
import forex.services.rates.oneframe.errors.Error.OneFrameLookupFailed
import forex.services.{ CacheService, RatesService }
import fs2.Stream
import io.circe.parser._
import io.circe.syntax._
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import pureconfig._
import pureconfig.generic.semiauto._

import scala.concurrent.duration._

import errors._

class Program[F[_]: Sync: Timer: Logger](
    ratesService: RatesService[F],
    cacheService: CacheService[F],
    config: Config,
    allPossiblePairs: Set[Rate.Pair]
) extends Algebra[F] {
  private val allPairsList: List[Rate.Pair] = allPossiblePairs.toList

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] = {
    val pair = Rate.Pair(request.from, request.to)
    if (!allPossiblePairs.contains(pair)) {
      val error = ValidationError(s"Pair ${pair.from.value}-${pair.to.value} is not valid")
      Logger[F].error(s"Validation failed").as(error.asLeft[Rate])
    } else {
      cacheService
        .get(makeKey(request.from, request.to))
        .map {
          case Some(value) =>
            decode[Rate](value)
              .leftMap[Error](e => Error.RateLookupFailed(e.getMessage))
          case None =>
            Error.RateLookupFailed("Cache is empty").asLeft[Rate]
        }
    }
  }

  private def polling: Stream[F, Unit] =
    Stream.eval(pollRates) >> Stream
      .awakeEvery[F](config.ratesPollingTimeout)
      .evalMap(_ => pollRates)

  private def pollRates: F[Unit] =
    for {
      _ <- Logger[F].info("Trying to acquire lock")
      isAcquired <- cacheService.tryAcquire
      _ <- Logger[F].info(s"Lock acquiring result: $isAcquired")
      _ <- updateRates.whenA(isAcquired)
      _ <- Logger[F].info("Skipping: Lock is already acquired").unlessA(isAcquired)
    } yield ()

  private def updateRates: F[Unit] =
    (for {
      _ <- Logger[F].info("Updating rates")
      rates <- getRates
      _ <- cacheService.setAll(toKeyValue(rates))
      _ <- Logger[F].info("Rates updated")
    } yield ()).handleErrorWith { ex =>
      Logger[F].error(ex)(s"Failed to update rates")
    }

  private def getRates: F[List[Rate]] =
    ratesService.getBatch(allPairsList).flatMap {
      case Right(rates)                    => rates.pure[F]
      case Left(OneFrameLookupFailed(msg)) =>
        (new RuntimeException(msg)).raiseError[F, List[Rate]]
    }

  private def toKeyValue(rates: List[Rate]): Map[String, String] =
    rates.map(rate => makeKey(rate.pair) -> rate.asJson.noSpaces).toMap

  private def makeKey(pair: Rate.Pair): String =
    makeKey(pair.from, pair.to)

  private def makeKey(from: Currency, to: Currency): String =
    s"${config.ratesCacheNamespace}:${from.value}${to.value}"
}

object Program {
  case class Config(
      ratesCacheNamespace: String,
      ratesPollingTimeout: FiniteDuration,
      currencies: Set[String]
  )
  object Config {
    implicit val ratesProgramConfigReader: ConfigReader[Config] = deriveReader
  }

  def apply[F[_]: Concurrent: Parallel: Timer: LoggerFactory](
      ratesService: RatesService[F],
      cacheService: CacheService[F],
      config: Config
  ): Resource[F, Algebra[F]] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[Program[F]])

    val service = new Program[F](
      ratesService = ratesService,
      cacheService = cacheService,
      config = config,
      allPossiblePairs = makePairs(config.currencies)
    )
    for {
      supervisor <- Supervisor[F]
      _ <- Resource.eval(supervisor.supervise(service.polling.compile.drain))
    } yield service
  }

  private def makePairs(currencies: Set[String]): Set[Rate.Pair] =
    for {
      left <- currencies
      right <- currencies if left != right
    } yield Rate.Pair(Currency(left), Currency(right))

}
