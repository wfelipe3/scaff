package scaff.adapter

import java.nio.file.{Path => JPath}
import scaff.ports.FileCreator
import scaff.ports.FileValues
import scaff.ports.TemplateStorage
import scaff.model.file._
import FileStructure._
import Name.Extensions._
import Name.Extensions.given
import cats.effect._
import java.nio.file.Files
import java.nio.file.StandardOpenOption._
import scala.io.Source
import cats.implicits._
import scala.util.chaining._
import yamusca.imports._
import mouse.all._

given fileCreator: FileCreator with

  extension(files: FileStructure)
    def createFiles[F[_]: Async](path: JPath)(using TemplateStorage): F[JPath] =
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

    def createF[F[_]: Async](using TemplateStorage): F[FileValues] = 
        files match
          case Dir(name, files) => 
            files
              .values
              .toList
              .traverse(_.createF)
              .map:
                f => FileValues.Dir(name.show, f)
          case File(name, content) =>
            Sync[F].delay:
              FileValues.File(name.show, content)
          case TemplateFile(name, templatePath, data, template) =>
            getContent(templatePath, template.toTemplate(data)).map:
              content =>
                FileValues.File(name.show, content)

  def createDir[F[_]: Async](path: java.nio.file.Path, name: Name) =
    Sync[F].delay {
      path
        .resolve(name.toPath) 
        .pipe{ p =>
          Files
            .exists(p)
            .fold(p, Files.createDirectory(p))
        }
    }

  def createTemplate[F[_]: Async, E](
    path: java.nio.file.Path, 
    name: Name, 
    templatePath: Path, 
    data: GenericContext
  )(using TemplateStorage) = 
    for 
      content <- getContent(templatePath, data)
      p <- write(path.resolve(name.toPath), content)
    yield p

  def getContent[F[_]: Async, E](path: Path, data: GenericContext)(using TemplateStorage) = 
    for
      temp <- getTemplateContent(path)
      value = data.toYamuscaValue.asContext
      content = mustache.render(temp.toOption.get)(value)
    yield content

  def getTemplateContent[F[_]: Async, E](path: Path)(using tempStorage: TemplateStorage) =
    tempStorage.template(path.value).map(mustache.parse)

  def createFile[F[_]: Async](path: java.nio.file.Path, name: Name, content: String) =
    write(path.resolve(name.toPath), content)

  def write[F[_]: Async](path: java.nio.file.Path, content: String) =
    Async[F].delay {
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

end fileCreator

given fileTemplateStorage: TemplateStorage with

  def template[F[_] : Async](name: String): F[String] =
    getTemplateFromResources(name)

  def store[F[_]: Async](name: String, template: String): F[Unit] = 
    Sync[F].unit

  private def getTemplateFromResources[F[_]: Sync, E](path: String) =
    Sync[F].bracket {
      Sync[F].delay(Source.fromResource(path))
    }{s => 
      Sync[F].delay(s.mkString)
    }(s => Sync[F].delay(s.close))

  private def getExternalTemplate[F[_]: Async, E](path: String) =
    Async[F].bracket {
      Async[F].delay(Source.fromFile(java.io.File(path)))
    }{s => 
      Async[F].delay(s.mkString)
    }(s => Async[F].delay(s.close))