package forex.domain

import io.circe.Codec
import io.circe.generic.semiauto._

import java.time.OffsetDateTime

case class Timestamp(value: OffsetDateTime) extends AnyVal

object Timestamp {
  implicit val timestampCodec: Codec[Timestamp] = deriveCodec

  def now: Timestamp =
    Timestamp(OffsetDateTime.now)
}
