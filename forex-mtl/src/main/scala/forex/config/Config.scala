package forex.config

import cats.effect.Sync

import pureconfig.ConfigSource
import pureconfig.generic.auto._
import cats.effect.Resource

object Config {

  /** @param path
    *   the property path inside the default configuration
    */
  def load[F[_]: Sync](path: String): Resource[F, ApplicationConfig] =
    Resource.eval {
      Sync[F].delay(ConfigSource.default.at(path).loadOrThrow[ApplicationConfig])
    }

}
