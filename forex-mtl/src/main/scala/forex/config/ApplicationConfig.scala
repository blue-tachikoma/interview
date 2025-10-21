package forex.config

import forex.programs.rates.Program
import forex.services.cache.interpreters.RedisCache
import forex.services.rates.interpreters.OneFrameLive

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    redis: RedisConfig,
    rates: RatesConfig,
    cache: RedisCache.Config
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class RedisConfig(uri: String)

case class RatesConfig(
    program: Program.Config,
    oneframe: OneFrameLive.Config
)
