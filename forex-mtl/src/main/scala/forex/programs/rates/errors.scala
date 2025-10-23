package forex.programs.rates

import forex.services.rates.oneframe.errors.{ Error => RatesServiceError }

object errors {

  sealed trait Error extends Exception {
    def message: String
  }
  object Error {
    final case class RateLookupFailed(message: String) extends Error
    final case class ValidationError(message: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.OneFrameLookupFailed(msg) => Error.RateLookupFailed(msg)
  }
}
