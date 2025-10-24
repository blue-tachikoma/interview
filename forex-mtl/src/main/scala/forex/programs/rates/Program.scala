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
      val error = ValidationError(show"Pair $pair is not valid")
      Logger[F].error(s"Validation failed").as(error.asLeft[Rate])
    } else {
      cacheService
        .get(pair)
        .flatMap {
          case Some(rate) => rate.asRight[Error].pure[F]
          case None       =>
            val error: Error = Error.RateLookupFailed(show"Rate for $pair is missing")
            Logger[F].error(error.message).as(error.asLeft[Rate])
        }
        .handleErrorWith { err =>
          Logger[F]
            .error(err)(err.getMessage())
            .as(Error.RateLookupFailed(show"Failed to get rate for $pair").asLeft[Rate])
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
      _ <- cacheService.setAll(rates)
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
}

object Program {
  case class Config(
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
