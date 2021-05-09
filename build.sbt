val scala3Version = "3.0.0-RC1"
val zioVersion = "1.0.5"

enablePlugins(DockerPlugin)
ThisBuild / scalacOptions ++= Seq("-Yindent-colons")

val zio = "dev.zio" %% "zio" % zioVersion
val zioStreams = "dev.zio" %% "zio-streams" % zioVersion
val zioLogging = "dev.zio" %% "zio-logging" % "0.5.8"
val zioTest = "dev.zio" %% "zio-test" % zioVersion
val zioSbtTest = "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
val zioMagnoliaTest = "dev.zio" %% "zio-test-magnolia" % zioVersion % "test"
val zioTestAll = Seq(zioTest, zioSbtTest, zioMagnoliaTest)
val yamusca = "com.github.eikek" % "yamusca-core_2.13" % "0.6.2"
val monocle = "com.github.julien-truffaut" %% "monocle-core" % "3.0.0-M3"
// val catsEffect = "org.typelevel" %% "cats-effect" % "2.4.1"
val catsEffect = "org.typelevel" %% "cats-effect" % "3.0.1"
val zioCatsInterop = "dev.zio" %% "zio-interop-cats" % "2.4.0.0"
val mouse = "org.typelevel" %% "mouse" % "1.0.0"
val http4sVersion = "1.0.0-M20"
val http4s = Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion,
  "io.circe" %% "circe-generic" % "0.14.0-M4",
  "io.circe" %% "circe-core" % "0.14.0-M4",
  "io.circe" %% "circe-parser" % "0.14.0-M4"
)
val log4cats = Seq(
  "org.typelevel" %% "log4cats-core" % "2.0.1",
  "org.typelevel" %% "log4cats-slf4j" % "2.0.1"
)
val prometheus = Seq(
  "io.prometheus" % "simpleclient" % "0.10.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.10.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.10.0",
  "io.prometheus" % "simpleclient_pushgateway" % "0.10.0"
)

lazy val integrationTest = project
  .in(file("integrationTest"))
  .dependsOn(infra)
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += zio,
    libraryDependencies ++= zioTestAll,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val infra = (project in file("infra")).settings(
  scalaVersion := scala3Version,
  libraryDependencies += zio,
  libraryDependencies += zioStreams,
  libraryDependencies += zioLogging,
  libraryDependencies += yamusca,
  libraryDependencies ++= zioTestAll,
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
)

lazy val model = project
  .in(file("model"))
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += zio,
    libraryDependencies += zioStreams,
    libraryDependencies += zioLogging,
    libraryDependencies += yamusca,
    libraryDependencies += monocle,
    libraryDependencies += catsEffect,
    libraryDependencies += zioCatsInterop,
    libraryDependencies ++= zioTestAll,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val usecase = (project in file("usecase"))
  .dependsOn(model)
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += zio,
    libraryDependencies += zioStreams,
    libraryDependencies += zioLogging,
    libraryDependencies += yamusca,
    libraryDependencies += monocle,
    libraryDependencies += catsEffect,
    libraryDependencies += zioCatsInterop,
    libraryDependencies ++= zioTestAll,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val adapters = (project in file("adapters/build-interpreters"))
  .dependsOn(model, usecase)
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += zio,
    libraryDependencies += zioStreams,
    libraryDependencies += zioLogging,
    libraryDependencies += yamusca,
    libraryDependencies += monocle,
    libraryDependencies += catsEffect,
    libraryDependencies += zioCatsInterop,
    libraryDependencies += mouse,
    // libraryDependencies += "dev.profunktor" %% "redis4cats-effects" % "0.13.1",
    libraryDependencies += "org.mongodb.scala" % "mongo-scala-driver_2.13" % "4.2.0",
    libraryDependencies ++= zioTestAll,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val console =
  project
    .in(file("app/console"))
    .dependsOn(model, usecase, adapters)
    .settings(
      scalaVersion := scala3Version,
      libraryDependencies ++= zioTestAll,
      libraryDependencies += zio,
      libraryDependencies += zioStreams,
      libraryDependencies += monocle,
      // libraryDependencies += "com.monovore" % "decline_2.13" % "1.0.0",
      // libraryDependencies += "com.monovore" % "decline-effect_2.13" % "1.0.0",
      libraryDependencies += catsEffect,
      libraryDependencies += yamusca,
      libraryDependencies += "com.lihaoyi" % "fastparse_2.13" % "2.3.2",
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

lazy val rest =
  project
    .in(file("app/rest"))
    .dependsOn(model, usecase, adapters)
    .settings(
      mainClass in assembly := Some("scaff.main.Main"),
      scalaVersion := scala3Version,
      libraryDependencies ++= zioTestAll,
      libraryDependencies += zio,
      libraryDependencies += zioStreams,
      libraryDependencies += monocle,
      libraryDependencies += catsEffect,
      libraryDependencies += yamusca,
      libraryDependencies ++= http4s,
      libraryDependencies ++= log4cats,
      libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.30",
      libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.30",
      libraryDependencies += "com.github.fd4s" %% "fs2-kafka" % "3.0.0-M3",
      libraryDependencies ++= prometheus,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )
    .settings(
      dockerfile in docker := {
        // The assembly task generates a fat JAR file
        val artifact: File = assembly.value
        val artifactTargetPath = s"/app/${artifact.name}"

        new Dockerfile {
          from("openjdk:8-jre")
          add(artifact, artifactTargetPath)
          entryPoint("java", "-jar", artifactTargetPath)
        }
      }
    )
