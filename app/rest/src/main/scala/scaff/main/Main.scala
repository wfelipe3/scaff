package scaff.main

import cats.effect._

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

import io.prometheus.client.Counter

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import scala.util.chaining._
import fs2._
import scala.concurrent.duration._
import scala.language.postfixOps
import cats.effect.Ref
import fs2.kafka._

import org.http4s.server.middleware.Metrics
import org.http4s.metrics.prometheus.Prometheus
import org.http4s.metrics.prometheus.PrometheusExportService

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.effect.Sync
import cats.implicits._
import cats.Show
import cats.effect.std.Queue
import scaff.model.project.{Project, Module, Dependency, ModuleName, ProjectName}
import java.nio.file.{Path => JPath}
import java.nio.file.Paths
import scaff.usecase._
import scaff.config._
import scaff.ports._
import scaff.adapter.fileCreator
// import scaff.adapter.fileTemplateStorage
import scaff.adapter.mongoTemplateStorage
import scaff.adapter.mongoStorage
import StorageConfig.MongoStorage
import scala.concurrent.ExecutionContext.Implicits.global
import scaff.adapter.MongoTemplateLoader

object Main extends IOApp:

    given Encoder[ScaffModule] = Encoder.instance:
        module =>
            Json.obj(
                "name" -> Json.fromString(module.name), 
                "dependencies" -> Json.fromValues(module.dependencies.map(Json.fromString)),
                "dependencyGroups" -> Json.fromValues(module.dependencyGroups.map(Json.fromString))
            )

    given Decoder[ScaffModule] = Decoder.instance:
        json =>
            for 
                name <- json.downField("name").as[String]
                deps <- json.downField("dependencies").as[List[String]]
                depGroups <- json.downField("dependencyGroups").as[List[String]]
            yield ScaffModule(name, deps, depGroups)

    given Encoder[FileValues] = Encoder.instance:
        case FileValues.Dir(name, files) =>
            Json.obj(
                "name" -> Json.fromString(name),
                "files" -> Json.fromValues(files.map(_.asJson))
            )
        case FileValues.File(name, content) =>
            Json.obj(
                "name" -> Json.fromString(name),
                "content" -> Json.fromString(content)
            )

    enum BuildTool:
        case Sbt
        case Mill

    given Encoder[BuildTool] = Encoder.instance:
        buildTool =>
            buildTool match
                case BuildTool.Mill =>
                    Json.fromString("mill")
                case BuildTool.Sbt =>
                    Json.fromString("sbt")

    given Decoder[BuildTool] = Decoder.instance:
        json =>
            json.as[String].flatMap:
                _ match
                    case "sbt" => BuildTool.Sbt.asRight
                    case "mill"  => BuildTool.Mill.asRight
                    case _ => DecodingFailure(s"build tool not supported", List.empty).asLeft

    def interpreter(buildTool: BuildTool) =
        buildTool match
        case BuildTool.Sbt =>
            scaff.adapter.sbtBuildToolInterpreter
        case BuildTool.Mill =>
            scaff.adapter.millBuildToolInterpreter

    case class CreateProjectCommand(
        name: String, 
        buildTool: BuildTool,
        modules: List[ScaffModule]
    )

    case class ScaffModule(
        name: String, 
        dependencies: List[String], 
        dependencyGroups: List[String]
    )

    case class Error(errors: List[String])

    given EntityDecoder[IO, CreateProjectCommand] = jsonOf[IO, CreateProjectCommand]
    given EntityDecoder[IO, Error] = jsonOf[IO, Error]

    given Show[CreateProjectCommand] = Show.show:
        scaff => scaff.asJson.spaces2

    extension [A, E](option: Option[A])
        def orError(e: => E) =
            option.fold(e.asLeft)(_.asRight)

    def validate(scaff: CreateProjectCommand): Error | (ProjectName, List[ModuleWithDependency]) =
        (validateModules(scaff.modules)
        , validateProject(scaff.name)
        ).parMapN:
            (modules, project) => (project, modules)
        .fold(Error.apply, identity)
        
    def validateModules(modules: List[ScaffModule]) =
        modules.traverse:
            module => 
                (validateModuleName(module.name)
                , validateDependencies(module.dependencies)
                , validateDependencyGroups(module.dependencyGroups)
                ).parMapN:
                    (module, deps, depGroups) => ModuleWithDependency(module, deps, depGroups)

    def validateModuleName(name: String) =
        ModuleName(name).map:
            mn => config.defaults.module(mn)
        .orError(s"Invalid module name '$name'".pure[List])

    def validateDependencies(dependencies: List[String]) = 
        dependencies.parTraverse:        
            dep => 
                config
                    .dependencies
                    .get(dep)
                    .orError(s"Dependency '$dep' not found".pure[List])

    def validateDependencyGroups(dependencyGroups: List[String]) =
        dependencyGroups.parTraverse:
            dep => 
                config
                    .dependencyGroups
                    .get(dep)
                    .orError(s"Dependency group '$dep' not found".pure[List])

    def validateProject(name: String) =
        ProjectName(name).orError(List(s"Invalid project name '$name'"))

    given MongoStorage = MongoStorage("mongo", 27017, "projects")

    val seconds = Stream.awakeEvery[IO](1 second).take(5)

    val meteredRoutes: Resource[IO, HttpRoutes[IO]] =
        for 
            registry <- Prometheus.collectorRegistry[IO]
            metrics <- Prometheus.metricsOps[IO](registry, "server")
            routes = PrometheusExportService[IO](registry).routes
            counter <- Resource.eval: 
                Ref[IO].of:
                    Counter.build()
                        .name("hello")
                        .help("hello world")
                        .register(registry)
        yield Metrics[IO](metrics)(createScaffService(counter)) <+> routes

    def createScaffService(counter: Ref[IO, Counter]) = HttpRoutes.of[IO]:
        case GET -> Root / "seconds" =>
            Ok(seconds.map(_.toString))

        case GET -> Root / "ws" =>
            val toClient: Stream[IO, WebSocketFrame] = 
                for
                    c <- Stream.eval(counter.get)
                    d <- Stream.awakeEvery[IO](1.seconds)
                yield
                    c.inc(10)
                    println(d.toString)
                    Text(s"Ping! $d")

            val fromClient: Pipe[IO, WebSocketFrame, Unit] = _.evalMap:
                case Text(t, _) => IO.delay(println(t))
                case f => IO.delay(println(s"Unknown type: $f"))

            WebSocketBuilder[IO].build(toClient, fromClient)

        case req@POST -> Root / "scaff" =>
            for
                logger <- Slf4jLogger.create[IO]
                scaff <- req.as[CreateProjectCommand]
                value = validate(scaff)
                ok <- value match
                    case e: Error =>
                        Status.BadRequest(e)
                    case (name, modules) =>
                        given BuildToolInterpreter = interpreter(scaff.buildTool)
                        CreateProjectUseCase.createFiles(name, modules)(config).flatMap:
                            files => Ok(files.asJson)
            yield ok

    def run(args: List[String]): IO[ExitCode] = 
        val consumerSettings =
            ConsumerSettings[IO, Unit, String]
                .withAutoOffsetReset(AutoOffsetReset.Earliest)
                .withBootstrapServers("kafka:9092")
                .withGroupId("group")

        def processRecord(record: ConsumerRecord[Unit, String]): IO[Unit] =
            IO(println(s"Processing record: $record"))

        val stream =
            KafkaConsumer.stream(consumerSettings)
                .evalTap(_.subscribeTo("quickstart-events"))
                .flatMap(_.partitionedStream)
                .map { partitionStream =>
                    partitionStream.evalMap { committable =>
                        processRecord(committable.record)
                    }
                }
                .parJoinUnbounded

        val loadTemplates = Stream.eval(MongoTemplateLoader.loadTemplates)

        (loadTemplates ++ stream).concurrently:
            Stream.eval:
                meteredRoutes.use:
                    routes =>
                        BlazeServerBuilder[IO](global)
                            .bindHttp(8080, "0.0.0.0")
                            .withHttpApp(routes.orNotFound)
                            .resource
                            .use(_ => IO.never)
                            .as(ExitCode.Success)
        .compile.drain.as(ExitCode.Success)

end Main