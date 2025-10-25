import Dependencies._
import CompilerOptions._

name := "forex"
version := "1.0.1"
scalaVersion := "2.13.17"
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalacOptions ++= CompilerOptions.common
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addCommandAlias("lint", ";scalafixAll;scalafmtAll;scalafmtSbt")

lazy val ItTest = config("it") extend Test
inConfig(ItTest)(Defaults.testSettings)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .configs(ItTest)
  .settings(
    Compile / run / fork := true,
    ItTest / fork := true,
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8080),
    ItTest / scalaSource := baseDirectory.value / "src/it/scala",
    ItTest / resourceDirectory := baseDirectory.value / "src/it/resources",
    ItTest / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
    ItTest / parallelExecution := false,
    libraryDependencies ++= Dependencies.Libraries.common,
    libraryDependencies ++= Dependencies.Libraries.test,
    libraryDependencies ++= Dependencies.Libraries.it
  )
