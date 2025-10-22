package forex.services.rates.interpreters

import cats.Applicative
import cats.effect.{ ConcurrentEffect, Resource, Sync }
import cats.syntax.all._
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.http.client.{ FatalError, RetryableError }
import forex.services.rates.Algebra
import forex.services.rates.errors._
import forex.services.rates.interpreters.OneFrameLive.Config
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.Status.{ ClientError, ServerError }
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.{ middleware, Client }
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

class OneFrameLive[F[_]: Sync: Logger](
    client: Client[F],
    config: Config
) extends Algebra[F] {
  import OneFrameLive._

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] = {
    val param = pair.from.value + pair.to.value
    val uri   = config.baseUri.withPath(config.ratesPath).withQueryParam("pair", param)

    client
      .expectOr[List[RatesResponse]](makeRatesRequest(uri))(toHttpClientError)
      .map { response =>
        response.headOption
          .map(_.toRate)
          .toRight[Error](Error.OneFrameLookupFailed("Empty rate"))
      }
      .handleErrorWith(handleError(_).map(_.asLeft[Rate]))
  }

  override def getBatch(pairs: List[Rate.Pair]): F[Either[Error, List[Rate]]] = {
    val params = pairs.map(p => p.from.value + p.to.value)
    val uri    = config.baseUri.withPath(config.ratesPath).withQueryParam("pair", params)

    client
      .expectOr[List[RatesResponse]](makeRatesRequest(uri))(toHttpClientError)
      .map(response => response.map(_.toRate).asRight[Error])
      .handleErrorWith(handleError(_).map(_.asLeft[List[Rate]]))
  }

  private def makeRatesRequest(uri: Uri): Request[F] =
    Request[F](Method.GET, uri, headers = Headers("token" -> config.token))

  private def toHttpClientError(response: Response[F]): F[Throwable] =
    response.status.responseClass match {
      case ClientError =>
        parseErrorResponse(response).map(body => FatalError(response.status.code, body))
      case ServerError =>
        parseErrorResponse(response).map(body => RetryableError(response.status.code, body))
      case _ =>
        Applicative[F].pure(FatalError(response.status.code, "Unexpected response"))
    }

  private def parseErrorResponse(response: Response[F]): F[String] =
    response
      .attemptAs[String]
      .foldF(
        df => Logger[F].error(df)(s"Failed parsing error response body: ${df.getMessage()}").as("Empty body"),
        body => body.pure[F]
      )

  private def handleError(error: Throwable): F[Error.OneFrameLookupFailed] =
    error match {
      case e @ RetryableError(status, message) =>
        Logger[F]
          .error(e)(s"Received retryable error from OneFrame: $status - $message")
          .as(Error.OneFrameLookupFailed("Retryable error occured"))
      case e @ FatalError(status, message) =>
        Logger[F]
          .error(e)(s"Received fatal error from OneFrame: $status - $message")
          .as(Error.OneFrameLookupFailed("Fatal error occured"))
      case other =>
        Logger[F]
          .error(other)(s"Received unexpected error from OneFrame: ${other.getMessage()}")
          .as(Error.OneFrameLookupFailed("Unexpected error occured"))
    }
}

object OneFrameLive {
  case class Config(
      baseUri: Uri,
      token: String,
      ratesPath: Uri.Path
  )
  object Config {
    implicit val uriReader: ConfigReader[Uri] = ConfigReader[String].emap { str =>
      Uri.fromString(str).leftMap(pf => CannotConvert(str, "Uri", pf.getMessage))
    }
    implicit val pathReader: ConfigReader[Uri.Path] = ConfigReader[String].map { pathStr =>
      val normalized = pathStr.stripPrefix("/").stripSuffix("/")
      if (normalized.isEmpty) Uri.Path.Root
      else Uri.Path.unsafeFromString(normalized)
    }
    implicit val oneFrameLiveConfigReader: ConfigReader[Config] = deriveReader
  }

  case class RatesResponse(
      from: String,
      to: String,
      price: BigDecimal,
      time_stamp: OffsetDateTime
  ) {
    def toRate: Rate = Rate(
      Rate.Pair(Currency(from), Currency(to)),
      Price(price),
      Timestamp(time_stamp)
    )
  }
  object RatesResponse {
    implicit val ratesResponseDecoder: Decoder[RatesResponse] = deriveDecoder
  }

  def make[F[_]: ConcurrentEffect: LoggerFactory](
      executionContext: ExecutionContext,
      config: Config
  ): Resource[F, Algebra[F]] = {
    implicit val logger: Logger[F] = LoggerFactory[F].getLoggerFromClass(classOf[OneFrameLive[F]])
    BlazeClientBuilder[F](executionContext).resource
      .map { client =>
        val loggingClient = middleware.Logger[F](
          logHeaders = true,
          logBody = true,
          logAction = Some(s => logger.info(s))
        )(client)
        new OneFrameLive[F](loggingClient, config)
      }
  }
}
