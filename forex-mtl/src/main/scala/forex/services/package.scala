package forex

package object services {
  type RatesService[F[_]] = rates.oneframe.Algebra[F]
  type CacheService[F[_]] = rates.cache.Algebra[F]

  val RatesServices = rates.oneframe.Interpreters
  val CacheServices = rates.cache.Interpreters
}
