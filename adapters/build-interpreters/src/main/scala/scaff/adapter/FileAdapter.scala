package scaff.adapter

import java.nio.file.{Path => JPath}
import scaff.ports.FileCreator
import scaff.model.file._
import FileStructure._
import Name.Extensions._
import cats.effect._
import java.nio.file.Files
import java.nio.file.StandardOpenOption._
import scala.io.Source
import cats.implicits._
import scala.util.chaining._
import yamusca.imports._
import mouse.all._

given fileCreator: FileCreator with
  extension [F[_]: Sync](files: FileStructure)
    def createFiles(path: JPath): F[JPath] =
      files match
        case Dir(name, files) => 
          for
            root <- createDir(path, name)
            paths <- files
              .values
              .toList
              .traverse(_.createFiles(root))
          yield root
        case File(name, content) =>
          createFile(path, name, content)
        case TemplateFile(name, templatePath, data, template) =>
          createTemplate(path, name, templatePath, template.toTemplate(data))

  def createDir[F[_]: Sync](path: java.nio.file.Path, name: Name) =
    Sync[F].delay {
      path
        .resolve(name.toPath) 
        .pipe{ p =>
          Files
            .exists(p)
            .fold(p, Files.createDirectory(p))
        }
    }

  def createTemplate[F[_]: Sync, E](
    path: java.nio.file.Path, 
    name: Name, 
    templatePath: Path, 
    data: GenericContext
  ) = 
    for 
      temp <- getTemplate(templatePath)
      value = data.toYamuscaValue.asContext
      content = mustache.render(temp.toOption.get)(value)
      p <- write(path.resolve(name.toPath), content)
    yield p

  def getTemplate[F[_]: Sync, E](path: Path) =
    path match 
      case Path.Resources(p) =>
        getTemplateFromResources(p)
      case Path.External(p) =>
        getExternalTemplate(p)

  def getTemplateFromResources[F[_]: Sync, E](path: String) =
    Sync[F].bracket {
      Sync[F].delay(Source.fromResource(path))
    }{s => 
      Sync[F].delay(s.mkString).map(mustache.parse)
    }(s => Sync[F].delay(s.close))

  def getExternalTemplate[F[_]: Sync, E](path: String) =
    Sync[F].bracket {
      Sync[F].delay(Source.fromFile(java.io.File(path)))
    }{s => 
      Sync[F].delay(s.mkString).map(mustache.parse)
    }(s => Sync[F].delay(s.close))

  def createFile[F[_]: Sync](path: java.nio.file.Path, name: Name, content: String) =
    write(path.resolve(name.toPath), content)

  def write[F[_]: Sync](path: java.nio.file.Path, content: String) =
    Sync[F].delay {
      Files.write(path, content.getBytes, TRUNCATE_EXISTING, CREATE)
    }

  extension (context: GenericContext)
    def toYamuscaValue: Value =
      context match
        case GenericContext.Literal(value) =>
          Value.fromString(value)
        case GenericContext.Values(values) =>
          Value.fromSeq(values.map(a => a.toYamuscaValue))
        case GenericContext.Entity(values) =>
          Value.fromMap(values.map { (key, value) =>
            (key, value.toYamuscaValue)
          })