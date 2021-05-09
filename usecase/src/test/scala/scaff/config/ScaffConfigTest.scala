package scaff.config

import zio.test._
import zio.test.Assertion._
import scaff.config._
import cats.implicits._
import cats.Show
import scaff.model.project._

object ScaffConfigTest extends DefaultRunnableSpec:

  case class Person(name: String, lastName: String)

  def spec = 
    suite("hello")(
      test("hi"){
        assert(config.defaults.scalaVersion)(equalTo(ScalaVersion("2.13.4").get))
      }
    )
