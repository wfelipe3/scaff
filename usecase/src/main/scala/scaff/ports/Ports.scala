package scaff.ports

import cats.implicits._
import cats.effect.Sync
import cats.effect.Async
import scaff.model.project.Project.ProjectErrorOr
import scaff.model.project._
import scaff.model.file._
import java.nio.file.{Path => JPath}
import java.util.UUID

trait BuildToolInterpreter:
  def toFiles(project: Project): Either[String, FileStructure]
  def toFiles(module: Module): Either[String, FileStructure]

extension (project: ProjectErrorOr[Project])
  def toFiles(using buildToolInterpreter: BuildToolInterpreter) =
    for
      prj <- project
        .toEither
        .leftMap(_.show)
      files <- buildToolInterpreter
        .toFiles(prj)
    yield files

extension (module: Module)
  def toFiles(using buildToolInterpreter: BuildToolInterpreter) =
    buildToolInterpreter.toFiles(module)

enum FileValues:
  case Dir(name: String, files: List[FileValues])
  case File(name: String, content: String)

trait TemplateStorage:
  def template[F[_]: Async](name: String): F[String]
  def store[F[_]: Async](name: String, template: String): F[Unit]

trait FileCreator:
  extension (files: FileStructure)
    def createFiles[F[_]: Async](path: JPath)(using TemplateStorage): F[JPath]
    def createF[F[_]: Async](using TemplateStorage): F[FileValues]

trait Storage[F[_]]:
  extension (project: ProjectErrorOr[Project])
    def store: F[ProjectErrors | UUID]
  
trait TemplateLoader[F[_]]:
  def loadTemplates(using TemplateStorage): F[Unit]