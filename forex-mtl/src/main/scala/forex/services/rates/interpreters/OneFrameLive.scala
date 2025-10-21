package forex.services.rates.interpreters

import cats.effect.{ ConcurrentEffect, Resource, Sync }
import cats.syntax.all._
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.rates.Algebra
import forex.services.rates.errors._
import forex.services.rates.interpreters.OneFrameLive.Config
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.{ middleware, Client }
import org.http4s.{ Headers, InvalidMessageBodyFailure, Method, Request, Uri }
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto._

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

class OneFrameLive[F[_]: Sync](
    client: Client[F],
    config: Config
) extends Algebra[F] {
  import OneFrameLive._

  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    val param = pair.from.value + pair.to.value
    val uri   = config.baseUri.withPath(config.ratesPath).withQueryParam("pair", param)

    client
      .expect[List[RatesResponse]](makeRatesRequest(uri))
      .map { response =>
        response.headOption
          .map(_.toRate)
          .toRight[Error](Error.OneFrameLookupFailed("Empty rate"))
      }
      .handleError {
        case e: InvalidMessageBodyFailure => Error.OneFrameLookupFailed(e.message).asLeft[Rate]
        case other                        => Error.OneFrameLookupFailed(other.getMessage()).asLeft[Rate]
      }
  }

  override def getBatch(pairs: List[Rate.Pair]): F[Error Either List[Rate]] = {
    val params = pairs.map(p => p.from.value + p.to.value)
    val uri    = config.baseUri.withPath(config.ratesPath).withQueryParam("pair", params)

    client
      .expect[List[RatesResponse]](makeRatesRequest(uri))
      .map { response =>
        response.map(_.toRate).asRight[Error]
      }
      .handleError {
        case e: InvalidMessageBodyFailure => Error.OneFrameLookupFailed(e.message).asLeft[List[Rate]]
        case other                        => Error.OneFrameLookupFailed(other.getMessage()).asLeft[List[Rate]]
      }
  }

  private def makeRatesRequest(uri: Uri): Request[F] =
    Request[F](Method.GET, uri, headers = Headers("token" -> config.token))

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
