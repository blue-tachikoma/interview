package forex

package object services {
  type RatesService[F[_]] = rates.Algebra[F]
  type CacheService[F[_]] = cache.Algebra[F]

  val RatesServices = rates.Interpreters
  val CacheServices = cache.Interpreters
}
