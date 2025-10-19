package forex.config

import scala.concurrent.duration.FiniteDuration
import forex.services.rates.interpreters.OneFrameLive
import forex.programs.rates.Program

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
    oneFrame: OneFrameLive.Config
)
