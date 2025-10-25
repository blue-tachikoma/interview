import sbt._

object Dependencies {

  object Versions {
    val cats       = "2.6.1"
    val catsEffect = "2.5.1"
    val fs2        = "2.5.4"
    val http4s     = "0.22.15"
    val circe      = "0.14.2"
    val pureConfig = "0.17.4"
    val redis4cats = "0.14.0"
    val catsRetry  = "2.1.0"

    val kindProjector       = "0.13.4"
    val betterMonadicFor    = "0.3.1"
    val logback             = "1.2.3"
    val log4cats            = "1.7.0"
    val scalaCheck          = "1.15.3"
    val scalaTest           = "3.2.7"
    val catsEffectScalaTest = "0.5.4"
    val catsScalaCheck      = "0.3.2"
    val testcontainers      = "0.43.0"
  }

  object Libraries {
    def circe(artifact: String): ModuleID          = "io.circe"       %% artifact % Versions.circe
    def http4s(artifact: String): ModuleID         = "org.http4s"     %% artifact % Versions.http4s
    def redis4cats(artifact: String): ModuleID     = "dev.profunktor" %% artifact % Versions.redis4cats
    def log4cats(artifact: String): ModuleID       = "org.typelevel"  %% artifact % Versions.log4cats
    def testcontainers(artifact: String): ModuleID = "com.dimafeng"   %% artifact % Versions.testcontainers

    lazy val cats       = "org.typelevel" %% "cats-core"   % Versions.cats
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2        = "co.fs2"        %% "fs2-core"    % Versions.fs2

    lazy val http4sDsl         = http4s("http4s-dsl")
    lazy val http4sServer      = http4s("http4s-blaze-server")
    lazy val http4sClient      = http4s("http4s-blaze-client")
    lazy val http4sCirce       = http4s("http4s-circe")
    lazy val circeCore         = circe("circe-core")
    lazy val circeGeneric      = circe("circe-generic")
    lazy val circeGenericExt   = circe("circe-generic-extras")
    lazy val circeParser       = circe("circe-parser")
    lazy val redis4catsEffects = redis4cats("redis4cats-effects")
    lazy val redis4catsLog     = redis4cats("redis4cats-log4cats")
    lazy val log4catsSlf4j     = log4cats("log4cats-slf4j")
    lazy val log4catsNoop      = log4cats("log4cats-noop")
    lazy val pureConfig        = "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig
    lazy val catsRetry         = "com.github.cb372"      %% "cats-retry" % Versions.catsRetry

    // Compiler plugins
    lazy val kindProjector    = "org.typelevel" %% "kind-projector"     % Versions.kindProjector cross CrossVersion.full
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Versions.betterMonadicFor

    // Runtime
    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    // Test
    lazy val scalaTest           = "org.scalatest"     %% "scalatest"                     % Versions.scalaTest
    lazy val catsEffectScalaTest = "com.codecommit"    %% "cats-effect-testing-scalatest" % Versions.catsEffectScalaTest
    lazy val scalaCheck          = "org.scalacheck"    %% "scalacheck"                    % Versions.scalaCheck
    lazy val catsScalaCheck      = "io.chrisdavenport" %% "cats-scalacheck"               % Versions.catsScalaCheck
    lazy val testcontainersScalatest = testcontainers("testcontainers-scala-scalatest")
    lazy val testcontainersRedis     = testcontainers("testcontainers-scala-redis")

    lazy val common: Seq[ModuleID] = Seq(
      cats,
      catsEffect,
      fs2,
      http4sDsl,
      http4sServer,
      http4sClient,
      http4sCirce,
      circeCore,
      circeGeneric,
      circeGenericExt,
      circeParser,
      redis4catsEffects,
      redis4catsLog,
      pureConfig,
      catsRetry,
      logback,
      log4catsSlf4j,
      log4catsNoop,
      compilerPlugin(kindProjector),
      compilerPlugin(betterMonadicFor)
    )

    lazy val test: Seq[ModuleID] =
      Seq(scalaTest % Test, catsEffectScalaTest % Test, scalaCheck % Test, catsScalaCheck % Test)

    lazy val it: Seq[ModuleID] =
      Seq(testcontainersScalatest % "it", testcontainersRedis % "it")
  }

}
