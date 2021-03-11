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

case class ModuleWithDependency[A](module: ModuleName, dependency: A)

object CreateProjectUseCase:

  def createProject(
    name: ProjectName, 
    modules: List[Module], 
    path: JPath,
    dependencies: List[ModuleWithDependency[Dependency]],
    dependenciesGroup: List[ModuleWithDependency[List[Dependency]]]
  )(config: ScaffConfig)(using BuildToolInterpreter)(using FileCreator)(using Storage[IO]) = 
    for
      project <- IO.pure {
        Project
          .project(name)
          .addModules(modules)
          .addModuleWithDependency(dependencies)
          .addAllModulesWithDepedency(dependenciesGroup)
      }
      files <- IO.fromEither {
        project
          .toFiles
          .leftMap(new Exception(_))
      }
      p <- files.createFiles[IO](path)
      id <- project.create
    yield Message.Ok(s"project created in path ${p.toString}")

extension (project: Project.ProjectErrorOr[Project])
  def addModuleWithDependency(dependencies: List[ModuleWithDependency[Dependency]]) =
    dependencies.foldLeft(project):
      (p, d) =>
        p.addDependency(d.module, d.dependency)

  def addAllModulesWithDepedency(dependencies: List[ModuleWithDependency[List[Dependency]]]) =
    dependencies.foldLeft(project):
      (p, d) =>
        p.addDependencies(d.module, d.dependency.toSet)

