package forex.domain

import java.time.OffsetDateTime
import io.circe.Codec
import io.circe.generic.semiauto._

case class Timestamp(value: OffsetDateTime) extends AnyVal

object Timestamp {
  implicit val timestampCodec: Codec[Timestamp] = deriveCodec

  def now: Timestamp =
    Timestamp(OffsetDateTime.now)
}
