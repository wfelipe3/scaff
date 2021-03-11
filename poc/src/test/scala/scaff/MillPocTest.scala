package scaff

import scaff.ProjectModel._
import scaff.FileModel._
import Name.Extensions._
import Name.Extensions.given
import GenericContext.given
import FilePath._
import FileStructure._
import zio.Task
import zio.test._
import zio.test.Assertion._
import zio.interop.catz._
import zio.console._
import cats.implicits._
import yamusca.imports._
import scala.util.chaining._
import cats.effect._
import cats.effect.implicits._
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption._
import scala.io.Source
import mouse.all._

object MillPocTest extends DefaultRunnableSpec:
  def spec =
    suite("Mill file structure")(
      test("Name show test") {
        assert(1.show)(equalTo("1")) &&
        assert(Name("hello").map(_.show))(isSome(equalTo("hello")))
      },
      testM("convert simple project to mill file structure") {
        Project
         .create(poc)
         .addModule(main.get)
         .addModule(infra.get)
         .toFiles(SbtInterpreter)
         .map(create[Task](Paths.get("/tmp")))
         .fold(s => putStrLn(s), identity)
         .map{ 
            case u: Unit => assert(false)(isTrue)
            case p: java.nio.file.Path => assert(true)(isTrue)
         }
      },
      test("Generic context") {
        import GenericContext.of
        assert(of(10))(equalTo(GenericContext.Literal("10"))) &&
        assert(of(Seq("1", "2")))(equalTo(GenericContext.Values(Seq(of("1"), of("2"))))) &&
        assert("hello".toGeneric)(equalTo(of("hello"))) &&
        assert(List(1,2,3,4).toGeneric)(equalTo(GenericContext.Values(Seq(of(1), of(2), of(3), of(4))))) &&
        assert(of(Map("hello" -> "world")))(equalTo(GenericContext.Entity(Map("hello" -> of("world")))))
      },
      // test("template configuration") {
      //   ProjectTemplate.make(

      //   )
      // }
    )

  val main = 
    for 
      name <- ModuleName("main")
      version <- ScalaVersion("2.13.4")
      testFramework <- TestFramework("zio.test.sbt.ZTestFramework")
    yield
      Module(
        name,
        version,
        Set.empty,
        Set.empty,
        Set.empty,
        ProjectModel.TestConfig(
          testFramework.some,
          Set(
            Dependency.Scala(
              org = "dev.zio",
              name = "zio-test_2.13",
              version = "1.0.3"
            )
          )
        ).pure[List]
      )

  val infra = 
    for 
      name <- ModuleName("infra")
      version <- ScalaVersion("2.13.4")
      testFramework <- TestFramework("zio.test.sbt.ZTestFramework")
    yield
      Module(
        name,
        version,
        Set.empty,
        Set.empty,
        Set.empty,
        ProjectModel.TestConfig(
          testFramework.some,
          Set(
            Dependency.Scala(
              org = "dev.zio",
              name = "zio-test_2.13",
              version = "1.0.3"
            )
          )
        ).pure[List]
      )

  trait BuildToolInterpreter:
    def toFiles(project: Project): Either[String, FileStructure]
    def toFiles(module: Module): Either[String, FileStructure]

  extension (projectOrError: ProjectErrors | Project)
    def toFiles(buildToolInterpreter: BuildToolInterpreter) = 
      for
        project <- projectOrError
          .toEither
          .leftMap(_.toString)
        files <- buildToolInterpreter.toFiles(project)
      yield files

  extension (module: Module)
    def toFiles(buildToolInterpreter: BuildToolInterpreter) =
      buildToolInterpreter.toFiles(module)

  val poc = ProjectName("poc").get

  extension [E, A](o: Option[A])
    def toEither(e: E) =
      o.fold(e.asLeft[A])(_.asRight[E])

  object SbtInterpreter extends BuildToolInterpreter:

    given GenericContextConverter[Module] with
      extension (module: Module)
        def toGeneric: GenericContext = 
          GenericContext.entityValues(
            "name" -> module.name.toGeneric,
            "scalaVersion" -> module.scalaVersion.toGeneric,
            "scalaOptions" -> module.scalaOptions.toSeq.toGeneric,
          )

    given Templetable[Project, GenericContext] with
      extension (project: Project)
        def toTemplate: GenericContext = 
          GenericContext.entityValues(
            "name" ->  project.name.toGeneric,
            "modules" -> project.modules.values.toSeq.toGeneric,
          )

    private def toFiles(testConfigs: List[ProjectModel.TestConfig]) =
      if testConfigs.isEmpty then None
      else Name("test")

    def toFiles(module: Module): Either[String, FileStructure] =
      for 
        name <- module.name
          .toFileName
          .toEither(s"${module.name} name not allowed")
        src <- Name("src")
          .toEither("src name not allowed")
        main <- Name("main")
          .toEither("main name not allowed")
        scala <- Name("scala")
          .toEither("scala name not allowed")
        resources <- Name("resources")
          .toEither("resources name not allowed")
      yield 
        dir(name)
          .create(src / main / scala)
          .create(src / main / resources)
          .pipe { mod =>
            toFiles(module.testConfigs)
              .fold(mod)(t => mod.create(src / t / scala))
          }
      
    def toFiles(project: Project): Either[String, FileStructure] = 
      for
        projectName <- project
          .name
          .toFileName
          .toEither(s"${project.name} name not allowed")
        modules <- project
          .modules
          .values
          .map(toFiles)
          .toList
          .sequence
        buildSbt <- Name("build.sbt")
          .toEither("build.sc name not allowed")
        scalaFmt <- Name(".scalafmt.conf")
          .toEither(".scalafmt.conf name not allowed")
        src <- Name("src")
          .toEither("src name not allowed")
        main <- Name("main")
          .toEither("main name not allowed")
        scala <- Name("scala")
          .toEither("scala name not allowed")
        resources <- Name("resources")
          .toEither("resources name not allowed")
        test <- Name("test")
          .toEither("test name not allowed")
      yield 
        dir(projectName)
          .create(src / main / scala)
          .create(src / main / resources)
          .create(src / test / scala)
          .add(file(scalaFmt, """version = "2.7.5""""))
          .add(template(buildSbt, Path.Resources("build.sbt.mustache"), project))
          .addAll(modules)


  object MillInterpreter extends BuildToolInterpreter:

    given GenericContextConverter[Module] with
      extension (module: Module)
        def toGeneric: GenericContext = 
          GenericContext.entityValues(
            "name" -> module.name.toGeneric,
            "scalaVersion" -> module.scalaVersion.toGeneric,
            "scalaOptions" -> module.scalaOptions.toSeq.toGeneric,
          )

    given Templetable[Project, GenericContext] with
      extension (project: Project)
        def toTemplate: GenericContext = 
          GenericContext.entityValues(
            "name" ->  project.name.toGeneric,
            "modules" -> project.modules.values.toSeq.toGeneric,
          )

    private def toFiles(testConfigs: List[ProjectModel.TestConfig]) =
      if testConfigs.isEmpty then None
      else Name("test").map(dir(_))

    def toFiles(module: Module): Either[String, FileStructure] =
      for 
        name <- module.name
          .toFileName
          .toEither(s"${module.name} name not allowed")
        src <- Name("src")
          .toEither("src name not allowed")
        resources <- Name("resources")
          .toEither("resources name not allowed")
      yield 
        dir(name)
          .add(dir(resources))
          .add(dir(src))
          .pipe { mod =>
            toFiles(module.testConfigs)
              .fold(mod)(t => mod.add(t))
          }
      
    def toFiles(project: Project): Either[String, FileStructure] = 
      for
        projectName <- project
          .name
          .toFileName
          .toEither(s"${project.name} name not allowed")
        modules <- project
          .modules
          .values
          .map(_.toFiles(this))
          .toList
          .sequence
        buildSc <- Name("build.sc")
          .toEither("build.sc name not allowed")
        scalaFmt <- Name(".scalafmt.conf")
          .toEither(".scalafmt.conf name not allowed")
      yield 
        dir(projectName)
          .add(file(scalaFmt, """version = "2.7.5""""))
          .add(template(buildSc, Path.Resources("build.sc.mustache"), project))
          .pipe(root => modules.foldLeft(root)(_ add _))
            

  def printFiles(indent: Int = 0)(files: FileStructure): Unit =
    val pos = List.fill(indent)("+").mkString
    files match
      case Dir(name, files) => 
        println(s"$pos$name") 
        files.map { case (_, file) =>
          printFiles(indent + 1)(file)
        }
      case File(name, content) => 
        println(s"$pos$name -> $content")
      case TemplateFile(name, path, data, template) =>
        println(s"$pos${name} -> ${template.toTemplate(data)}")

  def create[F[_]: Sync](path: java.nio.file.Path)(files: FileStructure): F[java.nio.file.Path] = 
    files match
      case Dir(name, files) => 
        for
          root <- createDir(path, name)
          paths <- files
            .values
            .toList
            .traverse(create(root))
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
      other = mustache.render(temp.toOption.get)(value)
      _ <- Sync[F].delay(println(other))
      p <- write(path.resolve(name.toPath), other)
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