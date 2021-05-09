package scaff.usecase

import scaff.model.project._
import scaff.model.file._
import scaff.config._
import scaff.ports._
import cats.implicits._
import cats.effect.IO
import java.nio.file.{Path => JPath}
import scala.util.chaining._

enum Message:
  case Ok(message: String)
  case Error(message: String)

case class ModuleWithDependency(
  module: Module, 
  dependencies: List[Dependency], 
  dependencyGroups: List[List[Dependency]]
)

object CreateProjectUseCase:

  def createFiles(
    name: ProjectName, 
    modules: List[ModuleWithDependency],
  )(config: ScaffConfig)(using BuildToolInterpreter)(using FileCreator)(using Storage[IO])(using TemplateStorage) =
    for
      project <- IO.pure:
        Project
          .project(name)
          .addAllModulesWithDepedencies(modules)

      files <- IO.fromEither:
        project
          .toFiles
          .leftMap(new Exception(_))

      values <- files.createF[IO]

      id <- project.store
    yield 
      values

  def createProject(
    name: ProjectName, 
    path: JPath,
    modules: List[ModuleWithDependency], 
  )(config: ScaffConfig)(using BuildToolInterpreter)(using FileCreator)(using Storage[IO])(using TemplateStorage) = 
    for
      project <- IO.pure {
        Project
          .project(name)
          .addAllModulesWithDepedencies(modules)
      }
      files <- IO.fromEither {
        project
          .toFiles
          .leftMap(new Exception(_))
      }
      p <- files.createFiles[IO](path)
      id <- project.store
    yield Message.Ok(s"project created in path ${p.toString}")

extension (project: Project.ProjectErrorOr[Project])

  def addAllModulesWithDepedencies(dependencies: List[ModuleWithDependency]) =
    dependencies.foldLeft(project):
      (p, d) =>
        p.addModule(d.module)
          .addDependencies(d.module.name, d.dependencies.toSet)
          .addDependencies(d.module.name, d.dependencyGroups.flatten.toSet)


