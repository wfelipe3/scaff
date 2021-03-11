package scaff.ports

import cats.implicits._
import cats.effect.Sync
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

trait FileCreator:
  extension [F[_]: Sync](files: FileStructure)
    def createFiles(path: JPath): F[JPath]

trait Storage[F[_]]:
  extension (project: ProjectErrorOr[Project])
    def create: F[ProjectErrors | UUID]