package forex.services.rates.cache

trait Algebra[F[_]] {
  def setAll(data: Map[String, String]): F[Unit]
  def get(key: String): F[Option[String]]
  def tryAcquire: F[Boolean]
}
