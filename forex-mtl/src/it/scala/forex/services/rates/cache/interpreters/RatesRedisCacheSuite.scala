package forex.services.rates.cache.interpreters

import cats.effect.testing.scalatest.{ AsyncIOSpec, CatsResourceIO }
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.dimafeng.testcontainers.GenericContainer
import dev.profunktor.redis4cats.connection.{ RedisClient, RedisURI }
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import forex.domain._
import forex.services.rates.cache.Algebra
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.funsuite.FixtureAsyncFunSuite
import org.testcontainers.containers.wait.strategy.Wait
import org.typelevel.log4cats.noop.NoOpLogger
import org.typelevel.log4cats.{ Logger, LoggerFactory, SelfAwareStructuredLogger }

import java.time.OffsetDateTime
import scala.concurrent.duration._

import RatesRedisCacheSuite.Environment

class RatesRedisCacheSuite extends FixtureAsyncFunSuite with AsyncIOSpec with CatsResourceIO[Environment] {
  import RatesRedisCacheSuite._

  def resource: Resource[IO, Environment] = {
    implicit val logging: LoggerFactory[IO] = new LoggerFactory[IO] {
      def getLoggerFromName(name: String): SelfAwareStructuredLogger[IO] = NoOpLogger[IO]
      def fromName(name: String): IO[SelfAwareStructuredLogger[IO]]      = IO(NoOpLogger[IO])

    }
    implicit val logger: Logger[IO] = NoOpLogger[IO]

    val redisContainer = Resource.make {
      IO {
        val container = GenericContainer(
          dockerImage = "redis:7-alpine",
          exposedPorts = Seq(6379),
          waitStrategy = Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
        )
        container.start()
        RedisContainer(container)
      }
    }(container => IO(container.stop()))

    val config = RatesRedisCache.Config(
      ratesNamespace = "rates",
      ratesLockNamespace = "rates-lock",
      lockKey = "lock",
      lockTtl = 1.minute
    )

    for {
      container <- redisContainer
      uri <- Resource.eval(RedisURI.make[IO](container.redisUri))
      client <- RedisClient[IO].fromUri(uri)
      redisCmd <- Redis[IO].fromClient(client, RedisCodec.Utf8)
      service <- RatesRedisCache[IO](config, client)
    } yield new Environment(service, redisCmd, config)
  }

  test("Should get existing rate by key") { env =>
    for {
      _ <- env.redisCmd.set(makeKey(rate.pair, env.config.ratesNamespace), rate.asJson.noSpaces)
      actual <- env.service.get(rate.pair)
    } yield assert(actual == rate.some)
  }

  test("Should set a list of rates") { env =>
    for {
      _ <- env.service.setAll(rates)
      storedRates <- rates.traverse(r => env.redisCmd.get(makeKey(r.pair, env.config.ratesNamespace)))
      actual <- storedRates.flatten.traverse(s => IO.fromEither(decode[Rate](s)))
    } yield assert(actual == rates)
  }

  test("Should acquire lock when it is possibe") { env =>
    for {
      isAcquired <- env.service.tryAcquire
      actual <- env.redisCmd.get(s"forex:${env.config.ratesLockNamespace}:${env.config.lockKey}")
    } yield assert(isAcquired && actual.isDefined)
  }

  test("Should not acquire lock when it was already acquired") { env =>
    for {
      isAcquired <- env.service.tryAcquire
      actual <- env.redisCmd.get(s"forex:${env.config.ratesLockNamespace}:${env.config.lockKey}")
    } yield assert(!isAcquired && actual.isDefined)
  }

  private def makeKey(pair: Rate.Pair, namespace: String): String =
    s"forex:$namespace:${pair.from.value}${pair.to.value}"
}

object RatesRedisCacheSuite {
  class Environment(
      val service: Algebra[IO],
      val redisCmd: RedisCommands[IO, String, String],
      val config: RatesRedisCache.Config
  )

  case class RedisContainer(container: GenericContainer) extends AnyVal {
    def host: String     = container.container.getHost
    def port: Int        = container.container.getMappedPort(6379)
    def redisUri: String = s"redis://$host:$port"
    def stop(): Unit     = container.stop()
  }

  val rate = Rate(
    pair = Rate.Pair(Currency("USD"), Currency("JPY")),
    price = Price(BigDecimal(0.3450)),
    timestamp = Timestamp(OffsetDateTime.now())
  )

  val rates = List(
    Rate(
      pair = Rate.Pair(Currency("USD"), Currency("JPY")),
      price = Price(BigDecimal(0.3450)),
      timestamp = Timestamp(OffsetDateTime.now())
    ),
    Rate(
      pair = Rate.Pair(Currency("EUR"), Currency("RUB")),
      price = Price(BigDecimal(0.8349574835)),
      timestamp = Timestamp(OffsetDateTime.now())
    ),
    Rate(
      pair = Rate.Pair(Currency("JPY"), Currency("USD")),
      price = Price(BigDecimal(0.5475735943)),
      timestamp = Timestamp(OffsetDateTime.now())
    )
  )
}
