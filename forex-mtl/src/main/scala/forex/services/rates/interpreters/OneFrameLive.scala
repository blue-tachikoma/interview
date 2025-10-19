package forex.services.rates.interpreters

import cats.syntax.all._
import forex.services.rates.Algebra
import forex.domain.Rate
import forex.services.rates.errors._
import cats.effect.{ ConcurrentEffect, Resource }
import scala.concurrent.ExecutionContext
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.{ Headers, Method, Request, Uri }
import org.http4s.circe.CirceEntityCodec._
import cats.effect.Sync
import pureconfig._
import pureconfig.generic.semiauto._
import pureconfig.error.CannotConvert
import forex.services.rates.interpreters.OneFrameLive.Config

class OneFrameLive[F[_]: Sync](
    client: Client[F],
    config: Config
) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    val param   = pair.from.value + pair.to.value
    val uri     = config.baseUri.withPath(config.ratesPath).withQueryParam("pair", param)
    val request = Request[F](
      method = Method.GET,
      uri = uri,
      headers = Headers("token" -> config.token)
    )

    client
      .expect[List[Rate]](request)
      .attemptT
      .leftMap[Error](e => Error.OneFrameLookupFailed(e.getMessage))
      .subflatMap(_.headOption.toRight(Error.OneFrameLookupFailed("Empty rate")))
      .value
  }

  override def getBatch(pairs: List[Rate.Pair]): F[Error Either List[Rate]] = {
    val params  = pairs.map(p => p.from.value + p.to.value)
    val uri     = config.baseUri.withPath(config.ratesPath).withQueryParam("pair", params)
    val request = Request[F](
      method = Method.GET,
      uri = uri,
      headers = Headers("token" -> config.token)
    )

    client
      .expect[List[Rate]](request)
      .attemptT
      .leftMap[Error](e => Error.OneFrameLookupFailed(e.getMessage))
      .value
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

  def make[F[_]: ConcurrentEffect](executionContext: ExecutionContext, config: Config): Resource[F, Algebra[F]] =
    BlazeClientBuilder[F](executionContext).resource
      .map(client => new OneFrameLive[F](client, config))
}
