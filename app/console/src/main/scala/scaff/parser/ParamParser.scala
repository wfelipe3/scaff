package scaff.parser

import cats.implicits._
import scaff.model.project._
import scaff.config._
import scala.util.chaining._
import scaff.usecase.ModuleWithDependency

extension [A](value: String)
  def parseDependency(f: String => Option[A]): Either[String, ModuleWithDependency[A]] =
    value
      .split(":")
      .pipe:  
        values =>
          if values.size != 2 then
            "dependency should have the format <modulename>:<dependency>".asLeft[ModuleWithDependency[A]]
          else
            for
              name <- ModuleName(values(0))
                .orError(s"Invalid module name ${value(0)}")
              value <- f(values(1))
                .orError(s"Invalid dependency name ${values(1)}")
            yield ModuleWithDependency(name, value)

end extension

extension [A, E](option: Option[A])
  def orError(e: => E) =
    option.fold(e.asLeft[A])(_.asRight[E])