package forex

package object programs {
  type RatesProgram[F[_]] = rates.Algebra[F]
  val RatesProgram = rates.Program
}
