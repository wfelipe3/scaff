package scaff.model.file

import cats.implicits._
import cats.Show
import cats.data.NonEmptyList
import monocle.Lens
import monocle.Prism
import monocle.function.At._
import scala.util.chaining._
import java.nio.file.Paths


opaque type Name = String

object Name:
  val nameRegex = raw"[a-zA-Z//.][a-zA-Z0-9\-\.]*".r
  def apply(name: String): Option[Name] =
    name match 
      case nameRegex(_*) => name.some
      case _ => None

  def name(n: String): Either[String, Name] =
    apply(n).fold(s"not a valid file name $n".asLeft)(_.asRight)

  object Extensions:
    given Show[Name] = 
        Show.show[Name](identity)

    extension (n: Name)
      def toPath =
        Paths.get(n.show)

  given GenericContextConverter[Name] with
    extension (name: Name)
      def toGenericContext = GenericContext.literal(name)

type Files = Map[Name, FileStructure]

trait Templetable[A, B]:
  extension (a: A)
    def toTemplate: B

type TemplatableContext[A] = Templetable[A, GenericContext]

enum FileStructure(name: Name):
  case TemplateFile[A](
    name: Name, 
    template: Path, 
    data: A,
    templatable: TemplatableContext[A]
  ) extends FileStructure(name)
  case Dir(name: Name, files: Files = Map.empty[Name, FileStructure]) extends FileStructure(name)
  case File(name: Name, content: String) extends FileStructure(name)

  def getName = name

object FileStructure:

  def dir(name: Name, files: Files = Map.empty[Name, FileStructure]): FileStructure =
    FileStructure.Dir(name, files)

  def file(name: Name, content: String): FileStructure =
    FileStructure.File(name, content)

  def template[A: TemplatableContext](name: Name, template: Path, data: A)(using templatable: TemplatableContext[A]) =
    FileStructure.TemplateFile(name, template, data, templatable)

enum Path(path: String):
  case Resources(path: String) extends Path(path)
  case External(path: String) extends Path(path)

  def value = path

object Path:
  def resources(path: String) =
    Path.Resources(path)

  def external(path: String) =
    Path.External(path)

type FilePath = NonEmptyList[Name]

extension (name: Name)
  def /(n: Name): FilePath =
    NonEmptyList.of(name, n)

extension (path: FilePath)
    def /(name: Name): FilePath =
      path.append(name)

def path(name: Name): FilePath =
  NonEmptyList.one(name)

def filesLens = Lens[FileStructure, Files]{
  case FileStructure.Dir(name, files) =>
    files
  case _ =>
    Map.empty
}(f => s => {
  s match
    case d: FileStructure.Dir =>
      d.copy(files = f)

    case _ =>
      s
})

def optionFileLens = Lens[Option[FileStructure], Files] {
  _.fold(Map.empty){
    case FileStructure.Dir(name, files) =>
      files
    case _ =>
      Map.empty
  }
}(f => s => {
  s.flatMap{
    case d: FileStructure.Dir =>
      d.copy(files = f).some
    case _ =>
      s
  }
})

extension (files: FileStructure)

  def addAll(otherFiles: List[FileStructure]) =
    otherFiles.foldLeft(files)(_ add _)

  def add(file: FileStructure): FileStructure =
    filesLens.modify(_ + (file.getName -> file))(files)

  def get(name: Name): Option[FileStructure] =
    get(path(name))

  def get(path: FilePath) =
    path
      .map(n => optionFileLens.andThen(at[Files, Name, Option[FileStructure]](n)))
      .reduce(_ andThen _)
      .get(files.some)

  def add(path: FilePath, file: FileStructure): FileStructure =
    path
      .map(n => optionFileLens.andThen(at[Files, Name, Option[FileStructure]](n)))
      .reduce(_ andThen _)
      .modify(_.map(_.add(file)))(files.some)
      .get

  def addCreating(path: FilePath, file: FileStructure) = 
    files.create(path).add(path, file)

  def create(path: FilePath) =
    path.foldLeft((files, List.empty[Name])){ case ((f, currentPath), name) =>
      val newNamesPath = currentPath :+ name
      val newPath = NonEmptyList.fromList(newNamesPath).get
      f.get(newPath)  
        .fold{
          if currentPath.isEmpty then
            (f.add(FileStructure.Dir(name)), newNamesPath)
          else
            (f.add(NonEmptyList.fromList(currentPath).get, FileStructure.Dir(name)), newNamesPath)
        }(_ => (f, newNamesPath))
    }._1

  def traverse[A](f: FileStructure => A) =
    f(files)


enum GenericContext:
  case Literal(value: String)
  case Entity(values: Map[String, GenericContext])
  case Values(values: Seq[GenericContext])

trait GenericContextConverter[A]:
  extension (a: A)
    def toGenericContext: GenericContext

object GenericContext:
  def literal(value: String) =
    GenericContext.Literal(value)

  def entity(values: Map[String, GenericContext]) =
    GenericContext.Entity(values)

  def entityValues(values: (String, GenericContext)*) =
    GenericContext.entity(values.toMap)

  def values(vals: Seq[GenericContext]) =
    GenericContext.Values(vals)

  def of[A: GenericContextConverter](a: A) =
    summon[GenericContextConverter[A]].toGenericContext(a)

  def many[A: GenericContextConverter](value: A, tail: A*) = 
    (value +: tail)
      .map(of)
      .pipe(values)
    
  given GenericContextConverter[Int] with
    extension (a: Int)
      def toGenericContext: GenericContext = 
        literal(a.toString)

  given GenericContextConverter[String] with
    extension (a: String)
      def toGenericContext: GenericContext = 
        literal(a)

  given [A](using GenericContextConverter[A]): GenericContextConverter[Seq[A]] with
    extension (a: Seq[A])
      def toGenericContext: GenericContext =
        GenericContext.Values(a.map(_.toGenericContext))

  given [A](using GenericContextConverter[A]): GenericContextConverter[Map[String, A]] with
    extension (m: Map[String, A])
      def toGenericContext: GenericContext =
        GenericContext.entity(m.map{ case (key, value) => (key, of(value))})