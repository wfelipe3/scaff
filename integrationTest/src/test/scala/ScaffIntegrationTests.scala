import zio.{Managed, Task, ZIO}
import zio.test._
import zio.test.Assertion._
import zio.stream._
import zio.console._
import java.nio.file.Path
import java.nio.file.Files
import scala.sys.process._
import zio.nio.channels._
import zio.nio.core.file._
import java.util.Comparator
import scala.language.postfixOps
import file.testing.FileAssertions._
import file.FileEffects._
import zio.logging._
import scala.language.implicitConversions
import zio.console._

object ScaffIntegrationTests extends DefaultRunnableSpec:

  type Ouptut = String

  val env = Logging.console(
    logLevel = LogLevel.Info,
    format = LogFormat.ColoredLogFormat()
  ) >>> Logging.withRootLoggerName("test")

  def spec = 
      suite("Scaff")(
        testM("Create simple project with default arguments should create project in the specified path"){
          testScaffM(
            ScaffCommand.New(
              name = "inttest", 
              template = Template.Empty, 
              buildTool = BuildTool.Mill
            )
          ) { (path, _) =>
            for
              buildsc <- (path / "inttest" / "build.sc").text
            yield
              assert(path / "inttest")(pathExists) 
              && assert(path / "inttest" / "build.sc")(fileExists)
              && assert(buildsc)(startsWithString("""
              |import mill._
              |import mill.scalalib._
              """.stripMargin.trim))
          }
        }
      )

  def testScaff(command: ScaffCommand)(testF: (Path, Ouptut) => TestResult) = 
    testScaffM(command)((p, o) => ZIO.succeed(testF(p, o)))

  def testScaffM[R, E](command: ScaffCommand)(testF: (Path, Ouptut) => ZIO[R, E, TestResult]) = 
    withinTempDir("project").use { p =>
      for 
        output <- command.run(p)
        result <- testF(p, output) 
      yield result
    }

  enum Template(name: String):
    case Console extends Template("console")
    case Empty extends Template("empty")

    def getName = name

  enum BuildTool(name: String):
    case Mill extends BuildTool("mill")
    case Sbt extends BuildTool("sbt")

    def getName = name


  enum ScaffCommand:
    case New(name: String, template: Template, buildTool: BuildTool)

  extension (command: ScaffCommand)
    def run(path: Path): ZIO[Console, Throwable, String] =
      command match
        case ScaffCommand.New(name, template, buildTool) =>
          putStrLn(s"scaff new -n $name -p ${path.toAbsolutePath} -b ${buildTool.getName}") *> 
          Task(s"scaff new -n $name -p ${path.toAbsolutePath} -b ${buildTool.getName}".!!).tap{m =>
            putStrLn(m)
          }


    

