package forex.domain

import io.circe.Codec
import io.circe.generic.semiauto._

case class Currency(value: String) extends AnyVal

object Currency {
  implicit val currencyCodec: Codec[Currency] = deriveCodec

  def validate(currency: Currency, allowed: Set[Currency]): Boolean = ???
}
