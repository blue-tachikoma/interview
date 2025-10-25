package forex.programs.rates

import cats.effect.testing.scalatest.{ AsyncIOSpec, CatsResourceIO }
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.rates.cache.FatalError
import org.scalatest.funsuite.FixtureAsyncFunSuite
import org.typelevel.log4cats.noop.NoOpLogger
import org.typelevel.log4cats.{ LoggerFactory, SelfAwareStructuredLogger }

import java.time.OffsetDateTime
import scala.concurrent.duration._

import ProgramSuite.Environment

class ProgramSuite extends FixtureAsyncFunSuite with AsyncIOSpec with CatsResourceIO[Environment] {
  import ProgramSuite._

  def resource: Resource[IO, Environment] = {
    implicit val logging: LoggerFactory[IO] = new LoggerFactory[IO] {
      def getLoggerFromName(name: String): SelfAwareStructuredLogger[IO] = NoOpLogger[IO]
      def fromName(name: String): IO[SelfAwareStructuredLogger[IO]]      = IO(NoOpLogger[IO])

    }
    val currencies   = Set("USD", "EUR", "JPY", "RUB")
    val config       = Program.Config(1.minute, currencies)
    val ratesCache   = new RatesCacheServiceMock[IO]
    val ratesService = new RatesServiceMock[IO]

    Program[IO](ratesService, ratesCache, config)
      .map(alg => new Environment(ratesCache, ratesService, alg))
  }

  test("Should return correct rates from cache") { resource =>
    for {
      _ <- resource.ratesCache.getMock.set { pair =>
             if (pair == rate.pair) IO.pure(rate.some)
             else IO.pure(none)
           }
      actual <- resource.program.get(getRequest)
    } yield assert(actual == rate.asRight[errors.Error])
  }

  test("Should fail when pair is not present in cache") { resource =>
    for {
      _ <- resource.ratesCache.getMock.set(_ => IO.pure(none))
      actual <- resource.program.get(getRequest)
    } yield {
      val expected = errors.Error
        .RateLookupFailed(
          show"Rate for ${rate.pair} is missing"
        )
        .asLeft[Rate]
      assert(actual == expected)
    }
  }

  test("Should fail when request pair is not valid") { resource =>
    val from = Currency("AAA")
    val to   = Currency("BBB")
    for {
      _ <- resource.ratesCache.getMock.set(_ => IO.pure(none))
      actual <- resource.program.get(Protocol.GetRatesRequest(from, to))
    } yield {
      val expected = errors.Error
        .ValidationError(
          s"Pair ${from.value}-${to.value} is not valid"
        )
        .asLeft[Rate]
      assert(actual == expected)
    }
  }

  test("Should fail on any cache error") { resource =>
    for {
      _ <- resource.ratesCache.getMock.set(_ => IO.raiseError(FatalError("Artificial error")))
      actual <- resource.program.get(getRequest)
    } yield {
      val expected = errors.Error
        .RateLookupFailed(show"Failed to get rate for ${rate.pair}")
        .asLeft[Rate]
      assert(actual == expected)
    }
  }
}

object ProgramSuite {
  class Environment(
      val ratesCache: RatesCacheServiceMock[IO],
      val ratesService: RatesServiceMock[IO],
      val program: Algebra[IO]
  )

  val rate = Rate(
    pair = Rate.Pair(Currency("USD"), Currency("JPY")),
    price = Price(BigDecimal(0.3450)),
    timestamp = Timestamp(OffsetDateTime.now())
  )
  val getRequest = Protocol.GetRatesRequest(rate.pair.from, rate.pair.to)
}
