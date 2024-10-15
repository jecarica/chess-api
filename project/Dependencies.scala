import Versions.{circeV, tapirV, zioV}
import sbt._
import sbt.librarymanagement.ModuleID

object Versions {
  val tapirV = "1.11.7"
  val zioV = "2.1.11"
  val circeV = "0.14.3"
}

object Dependencies {

  private val config = Seq(
    "com.typesafe"           % "config"     % "1.4.3",
    "com.github.pureconfig" %% "pureconfig" % "0.17.6"
  )

  lazy val zio: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio"       % zioV,
    "dev.zio" %% "zio-kafka" % "2.7.4",
    "dev.zio" %% "zio-http" % "3.0.1"
  )

  lazy val tapir: Seq[ModuleID] = Seq("com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-zio" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirV
  )


  lazy val test =  Seq(
    "dev.zio" %% "zio-test"     % zioV % Test,
    "dev.zio" %% "zio-test-sbt" % zioV % Test,
    "dev.zio" %% "zio-kafka-testkit" % "2.8.2" % Test,
    "dev.zio" %% "zio-mock" % "1.0.0-RC12"
  )

  lazy val circe = Seq(
    "io.circe" %% "circe-core" % circeV,
    "io.circe" %% "circe-generic" % circeV,
    "io.circe" %% "circe-parser" % circeV
  )


  val application: Seq[ModuleID] = zio ++ tapir  ++ test
  val client: Seq[ModuleID]      = config ++ zio
}
