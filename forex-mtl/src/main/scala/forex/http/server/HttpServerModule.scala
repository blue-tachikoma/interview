package forex.http.server

import cats.effect.{ Concurrent, ConcurrentEffect, Resource, Timer }
import cats.syntax.all._
import forex.config.HttpConfig
import forex.http.server.rates.RatesHttpRoutes
import forex.programs.RatesProgram
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.middleware.{ AutoSlash, RequestLogger, ResponseLogger, Timeout }
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object HttpServerModule {
  type PartialMiddleware[F[_]] = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware[F[_]]   = HttpApp[F] => HttpApp[F]

  def makeAndServe[F[_]: ConcurrentEffect: Timer: LoggerFactory](
      config: HttpConfig,
      executionContect: ExecutionContext,
      ratesProgram: RatesProgram[F]
  ): Resource[F, Server] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[HttpServerModule.type])

    val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes
    val httpApp                        = makeHttpApp(ratesHttpRoutes, config.timeout)

    BlazeServerBuilder[F](executionContect)
      .bindHttp(config.port, config.host)
      .withHttpApp(httpApp)
      .resource
  }

  private def makeHttpApp[F[_]: Concurrent: Timer: Logger](
      routes: HttpRoutes[F],
      timeout: FiniteDuration
  ): HttpApp[F] = {
    val autoSlashMid = AutoSlash.httpRoutes[F](_)
    // TODO: Add to config
    val requestLoggerMid = RequestLogger
      .httpRoutes[F](
        logHeaders = true,
        logBody = true,
        logAction = Some(s => Logger[F].info(s))
      )(_)
    val responseLoggerMid = ResponseLogger
      .httpRoutes[F, Request[F]](
        logHeaders = true,
        logBody = true,
        logAction = Some(s => Logger[F].info(s))
      )(_)

    val routesMiddleware: PartialMiddleware[F] = autoSlashMid >>> requestLoggerMid >>> responseLoggerMid
    val appMiddleware: TotalMiddleware[F]      = Timeout[F, F, Request[F]](timeout)(_)

    appMiddleware(routesMiddleware(routes).orNotFound)
  }
}
