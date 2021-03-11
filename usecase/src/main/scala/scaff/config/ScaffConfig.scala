package scaff.config

import scaff.model.project._
import Project._
import java.nio.file.Path
import cats.implicits._
import Dependency._

sealed trait StorageConfig
case class FileStorage(path: String) extends StorageConfig
case class MongoStorage(host: String, port: Int, database: String) extends StorageConfig

case class ScaffConfig(
    dependencies: Map[String, Dependency],
    dependencyGroups: Map[String, List[Dependency]],
    defaults: ScaffConfigDefaults,
    storage: StorageConfig,
    tests: Map[String, (TestFramework, List[Dependency])]
)

case class ScaffConfigDefaults(
    scalaVersion: ScalaVersion,
    scalaOptions: Set[ScalaOption],
    testConfig: (TestFramework, List[Dependency])
)

val dependencies = Map(
    "zio" -> scala("dev.zio", "zio", "1.0.3"),
    "zio-streams" -> scala("dev.zio", "zio-streams", "1.0.3"),
    "zio-logging" -> scala("dev.zio", "zio-logging", "0.5.3"),
    "zio-cats-interop" -> scala("dev.zio", "zio-interop-cats", "2.2.0.1"),
    "zio-config" -> scala("dev.zio", "zio-config", "1.0.0-RC29"),
    "zio-config-magnolia" -> scala("dev.zio", "zio-config-magnolia", "1.0.0-RC29"),
    "zio-config-typesafe" -> scala("dev.zio", "zio-config-typesafe", "1.0.0-RC29"),
    "zio-process" -> scala("dev.zio", "zio-process", "0.2.0"),
    "zio-actors" -> scala("dev.zio", "zio-actors", "0.0.7"),
    "zio-test" -> scala("dev.zio", "zio-test", "1.0.3"),
    "zio-test-sbt" -> scala("dev.zio", "zio-test-sbt", "1.0.3"),
    "zio-test-magnolia" -> scala("dev.zio", "zio-test-magnolia", "1.0.3"),
    "azure" -> java("com.microsoft.azure", "azure", "1.36.3"),
    "azure-storage" -> java("com.microsoft.azure", "azure-storage", "8.6.5"),
    "azure-blob" -> java("com.microsoft.azure", "azure-storage-blob", "12.9.0"),
    "azure-queue" -> java("com.microsoft.azure", "azure-storage-queue", "12.3.0"),
    "logback" -> java("ch.qos.logback", "logback-classic", "1.2.3"),
    "cats-core" -> scala("org.typelevel", "cats-core", "2.2.0"),
    "cats-effect" -> scala("org.typelevel", "cats-effect", "2.3.1"),
    "mouse" -> scala("org.typelevel", "mouse", "0.26.2"),
    "circe-core" -> scala("io.circe", "circe-core", "0.13.0"),
    "circe-generic" -> scala("io.circe", "circe-generic", "0.13.0"),
    "circe-parser" -> scala("io.circe", "circe-parser", "0.13.0"),
    "circe-optics" -> scala("io.circe", "circe-optics", "0.13.0"),
    "decline" -> scala("com.monovore", "decline", "1.2.0"),
    "decline-effect" -> scala("com.monovore", "decline-effect", "1.2.0"),
    "sttp-core" -> scala("com.softwaremill.sttp.client3", "core", "3.0.0-RC7"),
    "sttp-zio" -> scala("com.softwaremill.sttp.client3", "async-http-client-backend-zio", "3.0.0-RC7"),
    "sttp-circe" -> scala("com.softwaremill.sttp.client3", "circe", "3.0.0-RC7"),
    "doobie" -> scala("org.tpolecat", "doobie-core", "0.9.0"),
    "doobie-h2" -> scala("org.tpolecat", "doobie-h2", "0.9.0"),
    "doobie-hikari" -> scala("org.tpolecat", "doobie-hikari", "0.9.0"),
    "doobie-postgres" -> scala("org.tpolecat", "doobie-postgres", "0.9.0"),
    "doobie-quill" -> scala("org.tpolecat", "doobie-quill", "0.9.0"),
    "refined" -> scala("eu.timepit", "refined", "0.9.15"),
    "refined-cats" -> scala("eu.timepit", "refined-cats", "0.9.15"),
    "tapir-http4s" -> scala("com.softwaremill.sttp.tapir", "tapir-http4s-server", "0.16.15"),
    "tapir-circe" -> scala("com.softwaremill.sttp.tapir", "tapir-json-circe", "0.16.15"),
    "tapir-zio" -> scala("com.softwaremill.sttp.tapir", "tapir-zio","0.16.15"),
    "tapir-zio-http4s" -> scala("com.softwaremill.sttp.tapir", "tapir-zio-http4s-server", "0.16.15"),
    "tapir-swagger" -> scala("com.softwaremill.sttp.tapir", "tapir-swagger-ui-http4s", "0.16.16"),
    "tapir-openapi-docs" -> scala("com.softwaremill.sttp.tapir", "tapir-openapi-docs", "0.16.16"),
    "tapir-openapi-circe" -> scala("com.softwaremill.sttp.tapir", "tapir-openapi-circe-yaml", "0.16.16"),
    "monocle-core" -> scala("com.github.julien-truffaut", "monocle-core", "2.0.0"),
    "monocle-macro" -> scala("com.github.julien-truffaut", "monocle-macro", "2.0.0"),
    "swagger" -> java("org.webjars", "swagger-ui", "3.34.0"),
    "flywaydb" -> java("org.flywaydb", "flyway-core", "7.2.0"),
    "sqlserver" -> java("com.microsoft.sqlserver", "mssql-jdbc", "8.4.1.jre8")
  )

extension (values: List[String])
  def deps =
    values.map(dependencies(_))

val config =
  ScaffConfig(
    storage = FileStorage(""),
    tests = Map(
      "zio" -> (TestFramework("zio.test.sbt.ZTestFramework").get, List("zio-test", "zio-test-sbt", "zio-test-magnolia").deps)
    ),
    dependencies = dependencies,
    dependencyGroups = Map(
      "tapir" -> List("tapir-http4s", "tapir-circe", "tapir-zio", "tapir-zio-http4s", "tapir-swagger", "tapir-openapi-docs", "tapir-openapi-circe", "swagger").deps,
      "doobie" -> List("doobie", "doobie-h2", "doobie-postgres", "doobie-hikari", "doobie-quill").deps,
      "zio" -> List("zio", "zio-streams", "zio-logging", "zio-cats-interop", "zio-actors").deps,
      "zio-config" -> List("zio-config", "zio-config-magnolia", "zio-config-typesafe").deps,
      "circe" -> List("circe-core", "circe-generic", "circe-parser", "circe-optics").deps,
      "sttp" -> List("sttp-core", "sttp-zio", "sttp-circe").deps,
      "cats" -> List("mouse", "cats-core", "cats-effect").deps,
      "monocle" -> List("monocle-core", "monocle-macro").deps,
      "decline" -> List("decline", "decline-effect").deps,
    ),
    defaults = ScaffConfigDefaults(
      ScalaVersion("2.13.4").get, 
      Set(ScalaOption("-Yliteral-types").get), 
      (TestFramework("zio.test.sbt.ZTestFramework").get, List("zio-test", "zio-test-sbt", "zio-test-magnolia").deps))
  )

extension (defaults: ScaffConfigDefaults)
  def module(name: ModuleName) =
    Project.module(
      name = name, 
      version = defaults.scalaVersion, 
      scalaOptions = defaults.scalaOptions,
      testConfigs = TestConfig(defaults.testConfig._1.some, defaults.testConfig._2.toSet).pure[List]
    )