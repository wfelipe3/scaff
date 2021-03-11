val scala3Version = "3.0.0-M3"
val scala2Version = "2.13.4"
// val scala3Version = "2.13.4"
val zioVersion = "1.0.4-2"

// lazy val root = project
//   .in(file("."))
//   .dependsOn(infra)
//   .settings(
//     name := "scafM3
//     version := "0.1.0",
//     scalaVersion := scala3Version,
//     libraryDependencies ++= Seq(
//       "dev.zio" % "zio-test_2.13" % zioVersion % "test",
//       "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
//       "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test"
//     ),
//     libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
//     libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
//     libraryDependencies += "com.github.julien-truffaut" % "monocle-core_2.13" % "2.0.5",
//     libraryDependencies += "com.monovore" % "decline_2.13" % "1.0.0",
//     libraryDependencies += "com.monovore" % "decline-effect_2.13" % "1.0.0",
//     testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
//     crossScalaVersions := Seq(scala3Version, scala2Version)
//   )
//   .aggregate(integrationTest)
ThisBuild / scalacOptions ++= Seq("-Yindent-colons")

lazy val integrationTest = project
  .in(file("integrationTest"))
  .dependsOn(infra)
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
    libraryDependencies ++= Seq(
      "dev.zio" % "zio-test_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val infra = (project in file("infra")).settings(
  scalaVersion := scala3Version,
  libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
  libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
  libraryDependencies += "dev.zio" % "zio-logging_2.13" % "0.5.7",
  libraryDependencies += "com.github.eikek" % "yamusca-core_2.13" % "0.6.2",
  libraryDependencies ++= Seq(
    "dev.zio" % "zio-test_2.13" % zioVersion % "test",
    "dev.zio" % "zio-test-sbt_2.13" % zioVersion,
    "dev.zio" % "zio-test-magnolia_2.13" % zioVersion
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
)

lazy val poc = (project in file("poc")).settings(
  scalaVersion := scala3Version,
  libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
  libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
  libraryDependencies += "dev.zio" % "zio-logging_2.13" % "0.5.7",
  libraryDependencies += "com.github.eikek" % "yamusca-core_2.13" % "0.6.2",
  libraryDependencies += "com.github.julien-truffaut" % "monocle-core_2.13" % "3.0.0-M3",
  libraryDependencies += "org.typelevel" % "cats-effect_2.13" % "2.3.1",
  libraryDependencies += "dev.zio" % "zio-interop-cats_2.13" % "2.3.1.0",
  libraryDependencies ++= Seq(
    "dev.zio" % "zio-test_2.13" % zioVersion % "test",
    "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
    "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test",
    "org.scalatest" % "scalatest_2.13" % "3.2.2" % "test"
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  crossScalaVersions ++= Seq("2.13.4", scala3Version)
)

lazy val model = project
  .in(file("model"))
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
    libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
    libraryDependencies += "dev.zio" % "zio-logging_2.13" % "0.5.7",
    libraryDependencies += "com.github.eikek" % "yamusca-core_2.13" % "0.6.2",
    libraryDependencies += "com.github.julien-truffaut" % "monocle-core_2.13" % "3.0.0-M3",
    libraryDependencies += "org.typelevel" % "cats-effect_2.13" % "2.3.1",
    libraryDependencies += "dev.zio" % "zio-interop-cats_2.13" % "2.3.1.0",
    libraryDependencies ++= Seq(
      "dev.zio" % "zio-test_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test",
      "org.scalatest" % "scalatest_2.13" % "3.2.2" % "test"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val usecase = (project in file("usecase"))
  .dependsOn(model)
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
    libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
    libraryDependencies += "dev.zio" % "zio-logging_2.13" % "0.5.7",
    libraryDependencies += "com.github.eikek" % "yamusca-core_2.13" % "0.6.2",
    libraryDependencies += "com.github.julien-truffaut" % "monocle-core_2.13" % "3.0.0-M3",
    libraryDependencies += "org.typelevel" % "cats-effect_2.13" % "2.3.1",
    libraryDependencies += "dev.zio" % "zio-interop-cats_2.13" % "2.3.1.0",
    libraryDependencies ++= Seq(
      "dev.zio" % "zio-test_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test",
      "org.scalatest" % "scalatest_2.13" % "3.2.2" % "test"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val adapters = (project in file("adapters/build-interpreters"))
  .dependsOn(model, usecase)
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
    libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
    libraryDependencies += "dev.zio" % "zio-logging_2.13" % "0.5.7",
    libraryDependencies += "com.github.eikek" % "yamusca-core_2.13" % "0.6.2",
    libraryDependencies += "com.github.julien-truffaut" % "monocle-core_2.13" % "3.0.0-M3",
    libraryDependencies += "org.typelevel" % "cats-effect_2.13" % "2.3.1",
    libraryDependencies += "dev.zio" % "zio-interop-cats_2.13" % "2.3.1.0",
    libraryDependencies += "org.typelevel" % "mouse_2.13" % "0.26.2",
    libraryDependencies += "org.mongodb.scala" % "mongo-scala-driver_2.13" % "4.2.0",
    libraryDependencies ++= Seq(
      "dev.zio" % "zio-test_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
      "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test",
      "org.scalatest" % "scalatest_2.13" % "3.2.2" % "test"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val console =
  project
    .in(file("app/console"))
    .dependsOn(model, usecase, adapters)
    .settings(
      scalaVersion := scala3Version,
      libraryDependencies ++= Seq(
        "dev.zio" % "zio-test_2.13" % zioVersion % "test",
        "dev.zio" % "zio-test-sbt_2.13" % zioVersion % "test",
        "dev.zio" % "zio-test-magnolia_2.13" % zioVersion % "test"
      ),
      libraryDependencies += "dev.zio" % "zio_2.13" % zioVersion,
      libraryDependencies += "dev.zio" % "zio-streams_2.13" % zioVersion,
      libraryDependencies += "com.github.julien-truffaut" % "monocle-core_2.13" % "3.0.0-M3",
      libraryDependencies += "com.monovore" % "decline_2.13" % "1.0.0",
      libraryDependencies += "com.monovore" % "decline-effect_2.13" % "1.0.0",
      libraryDependencies += "org.typelevel" % "cats-effect_2.13" % "2.3.1",
      libraryDependencies += "com.github.eikek" % "yamusca-core_2.13" % "0.6.2",
      libraryDependencies += "com.lihaoyi" % "fastparse_2.13" % "2.2.2",
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )
