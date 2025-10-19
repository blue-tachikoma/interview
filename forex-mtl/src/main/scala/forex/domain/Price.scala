package forex.domain

import io.circe.Codec
import io.circe.generic.semiauto._

case class Price(value: BigDecimal) extends AnyVal

object Price {
  implicit val priceCodec: Codec[Price] = deriveCodec

  def apply(value: Integer): Price =
    Price(BigDecimal(value))
}
