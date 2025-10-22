package forex.http.client

abstract class HttpClientError(message: String) extends Throwable(message)
case class RetryableError(status: Int, message: String) extends HttpClientError(message)
case class FatalError(status: Int, message: String) extends HttpClientError(message)
