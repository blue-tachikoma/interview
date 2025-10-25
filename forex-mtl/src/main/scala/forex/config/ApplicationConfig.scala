package forex.config

import forex.programs.rates.Program
import forex.services.rates.cache.interpreters.RatesRedisCache
import forex.services.rates.oneframe.interpreters.OneFrameLive
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    redis: RedisConfig,
    rates: RatesConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class RedisConfig(uri: String)

case class RatesConfig(
    program: Program.Config,
    oneframe: OneFrameLive.Config,
    cache: RatesRedisCache.Config
)

case class RetryConfig(
    isEnabled: Boolean,
    maxRetries: Int,
    baseDelay: FiniteDuration
)

object RetryConfig {
  implicit val retryConfigReader: ConfigReader[RetryConfig] = deriveReader
}
