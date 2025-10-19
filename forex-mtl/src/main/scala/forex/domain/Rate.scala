package forex.domain

import io.circe.generic.semiauto._
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec

case class Rate(
    pair: Rate.Pair,
    price: Price,
    timestamp: Timestamp
)

object Rate {
  implicit val configuration: Configuration    = Configuration.default.withSnakeCaseMemberNames
  implicit val rateCodec: Codec.AsObject[Rate] = deriveConfiguredCodec

  final case class Pair(
      from: Currency,
      to: Currency
  )
  object Pair {
    implicit val pairCodec: Codec.AsObject[Pair] = deriveCodec
  }

}
