package file

import zio.ZIO
import zio.ZManaged
import zio.Managed
import zio.ZLayer
import zio.Task
import zio.stream.ZStream
import zio.blocking._
import java.nio.file.Path
import java.nio.file.Files
import java.util.Comparator
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption._
import throwable._
import scala.io.Source
import zio.console._

object FileEffects:

  extension (path: Path)
    def /(other: String) =
      path.resolve(other)

    def /(other: Path) =
      path.resolve(other)

    def createDir =
      blocking(Task(Files.createDirectory(path)))

    def createFile(text: String) =
      blocking(
        Task{
          Files.write(path, text.getBytes, CREATE, TRUNCATE_EXISTING)
        }
      )

    def readFileFromResources =
      ZIO.bracket(Task.effect(Source.fromResource(path.toString())))(s =>
        Task.effect(s.close()).orDie
      )(s => blocking(Task.effect(s.getLines().mkString("\n"))))  

    def readFileFromPath =
      ZIO.bracket(Task.effect(Source.fromFile(path.toString())))(s =>
        Task.effect(s.close()).orDie
      )(s => blocking(Task.effect(s.getLines().mkString("\n"))))  

    def text =
      ZStream
        .fromJavaStreamEffect(blocking(Task(Files.lines(path))))
        .runCollect
        .map(_.mkString("\n"))

  def withinTempDir(name: String) =
    Managed
      .make(blocking(Task(Files.createTempDirectory(name)))){ p => 
          // deleteDirectory(p).catchAll(_.logError)
          putStrLn(p.toString)
      }

  private def deleteDirectory(p: Path) =
    blocking(Task {
      Files.walk(p)
        .sorted(Comparator.reverseOrder())
        .map(p => p.toFile)
        .forEach(p => p.delete)
    })
