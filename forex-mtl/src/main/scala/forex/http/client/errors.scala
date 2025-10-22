package forex.http.client

abstract class HttpClientError(message: String) extends Throwable(message)
case class LogicalError(status: Int, message: String) extends HttpClientError(message)
case class TechnicalError(status: Int, message: String) extends HttpClientError(message)
