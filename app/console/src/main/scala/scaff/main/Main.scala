package scaff.main

import zio.App
import zio.ZEnv
import zio.ZIO
import zio.Task
import zio.console._
import cats.effect.IO
import cats.effect.ExitCode
import java.nio.file.Path
import cats.implicits._
import scala.Console
import scala.util.chaining._
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import scaff.model.project._
import scaff.usecase.CreateProjectUseCase
import scaff.usecase.Message
import scaff.ports.BuildToolInterpreter
import scaff.adapter.fileCreator
import scaff.adapter.mongoStorage
import cats.data.NonEmptyList
import scaff.config._
import java.nio.file.Paths
import scaff.parser._
import StorageConfig.MongoStorage

object Main extends CommandIOApp(
  name = "scaff", 
  header = "scaffolder cli for scala projects", 
  version = "0.1"
):

  enum Command:
    case New(
      name: String, 
      modules: NonEmptyList[String],
      path: Path, 
      buildTool: BuildTool,
      dependencies: List[String],
      dependencyGroups: List[String]
    )

  object BuildTools:
    def parseBuildTool(name: String): Option[BuildTool] =
      BuildTool.values.find(_.getName === name)

    def buildTools =
      BuildTool.values.map(_.getName).mkString("[", ", ", "]")

  def newCommand =
      Opts.subcommand(
          name = "new",
          help = "creates a new project"
      ){
          val name = Opts.option[String](
              long = "name",
              short = "n",
              help = "name"
          )

          val path = Opts.option[Path](
            long = "path",
            short = "p",
            help = "path for the project to be created"
          )
          
          val modules =
            Opts.options[String](
              long = "module",
              short = "m",
              help = "Module"
            )

          val dependenciesOpt =
            Opts
              .options[String](
                long = "dependency",
                short = "d",
                help = "Dependency"
              )
              .map(_.toList)
              .withDefault(List.empty)

          val dependenciesGroupOpt =
            Opts
              .options[String](
                long = "dependency-group",
                short = "g",
                help = "Dependency group"
              )
              .map(_.toList)
              .withDefault(List.empty)

          val buildTool = Opts.option[String](
            long = "buildtool",
            short = "b",
            help = "build tool to use in the project, allowed values are mill and sbt"
          )
          .map(BuildTools.parseBuildTool)
          .validate(s"the given build tool is not valid, valid build tools are ${BuildTools.buildTools}".red)(_.isDefined)
          .map(_.get)
          
          (name, modules, path, buildTool, dependenciesOpt, dependenciesGroupOpt).mapN(Command.New.apply)
      }

  given MongoStorage = MongoStorage("localhost", 27017, "projects")

  def main: Opts[IO[ExitCode]] = 
    newCommand.map { 
        case Command.New(name, modules, path, buildTool, dependencies, dependencyGroups) =>
          given BuildToolInterpreter = interpreter(buildTool)

          val v = for
            values <- modules.traverse{ n =>
              ModuleName(n).map: 
                mn => config.defaults.module(mn)
            } 
            .orError(s"Invalid module names ${modules.toList.mkString(", ")}")
            prj <- ProjectName(name).orError(s"Invalid project name $name")
            deps <- dependencies.traverse(parseDependency(_)(config.dependencies.get))
            groups <- dependencyGroups.traverse(parseDependency(_)(config.dependencyGroups.get))
          yield 
            CreateProjectUseCase
              .createProject(prj, values.toList, path, deps, groups)(config)
              .flatMap:
                case Message.Ok(m) =>
                  success(m)
                case Message.Error(m) =>
                  error(m)
              
          v.fold(error, identity)
    }

  def interpreter(buildTool: BuildTool) =
    buildTool match
      case BuildTool.Sbt =>
        scaff.adapter.sbtBuildToolInterpreter
      case BuildTool.Mill =>
        scaff.adapter.millBuildToolInterpreter

  def success(message: String) =
    IO(println(message.green)).as(ExitCode.Success)

  def error(message: String) =
    IO(println(message.red)).as(ExitCode.Error)

  extension (s: String)
    def red =
      s"${Console.RED}$s${Console.RESET}"

    def green =
      s"${Console.GREEN}$s${Console.RESET}"

  extension [A, E](option: Option[A])
    def orError(e: => E) =
       option.fold(e.asLeft)(_.asRight)

//4488210001057391