package forex

import cats.effect._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Module
      .wire[IO](executionContext)
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
