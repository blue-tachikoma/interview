package forex.http.server.rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.{ HttpRoutes, Response }

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap {
        case Right(rate) => Ok(rate.asGetApiResponse)
        case Left(e)     => toResponse(e)
      }
  }

  private def toResponse(err: Error): F[Response[F]] =
    err match {
      case Error.ValidationError(message)  => BadRequest(message)
      case Error.RateLookupFailed(message) => InternalServerError(message)
    }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
