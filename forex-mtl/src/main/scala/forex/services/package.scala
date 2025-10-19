package forex

package object services {
  type RatesService[F[_]] = rates.Algebra[F]
  val RatesServices = rates.Interpreters
}
