package forex.services.rates.cache

abstract class RedisError(message: String) extends Throwable(message)
case class RetryableError(message: String) extends RedisError(message)
case class FatalError(message: String) extends RedisError(message)
