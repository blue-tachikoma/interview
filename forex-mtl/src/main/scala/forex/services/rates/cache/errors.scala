package forex.services.rates.cache

abstract class CacheError(message: String) extends Throwable(message)
case class RetryableError(message: String) extends CacheError(message)
case class FatalError(message: String) extends CacheError(message)
