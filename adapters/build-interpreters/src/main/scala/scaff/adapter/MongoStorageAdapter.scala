package scaff.adapter

import org.mongodb.scala._
import model.Filters
import model.Projections._
import scaff.config._
import scaff.model.project._
import scaff.ports._
import scala.util.chaining._
import cats.effect._
import java.util.UUID
import cats.implicits._
import StorageConfig.MongoStorage
import concurrent.ExecutionContext.Implicits.global
import scala.io.Source


given mongoStorage(using MongoStorage): Storage[IO] with

  extension (projectOrError: Project.ProjectErrorOr[Project])
    def store = 
      projectOrError match
        case e: ProjectErrors =>
          IO.pure(e)
        case p: Project =>
          for
            conn <- connection(summon[MongoStorage], "projects")
            id <- save(p)(conn)
          yield id

  def save(project: Project)(collection: MongoCollection[Document]) =
    for
      id <- IO(UUID.randomUUID)
      doc <- IO.fromFuture(IO(collection.insertOne(project.toDocument(id)).toFuture))
    yield id
    
  extension (project: Project)
    def toDocument(id: UUID) =
      Document("_id" -> id.toString, "name" -> project.name.show)

end mongoStorage
    
given mongoTemplateStorage(using MongoStorage): TemplateStorage with

    def template[F[_]: Async](name: String): F[String] =
      for
        conn <- connectionF(summon[MongoStorage], "templates") 
        foundTemplate <- Async[F].fromFuture[String] {
          Async[F].delay {
            conn
              .find[Document](Filters.equal("name", name))
              .projection(fields(include("template"), excludeId))
              .first
              .map(j => j.getString("template"))
              .head
          }
        }
      yield foundTemplate

    def store[F[_]: Async](name: String, template: String): F[Unit] = 
      for
        collection <- connectionF(summon[MongoStorage], "templates")
        id <- Async[F].delay(UUID.nameUUIDFromBytes(name.getBytes))
        doc <- Async[F].fromFuture(Async[F].delay(collection.insertOne(Document("_id" -> id.toString, "name" -> name, "template" -> template)).toFuture))
      yield ()

end mongoTemplateStorage

object MongoTemplateLoader extends TemplateLoader[IO]:

  def loadTemplates(using storage: TemplateStorage): IO[Unit] = 
    for
      buildsc <- getTemplateFromResources[IO]("build.sc.mustache")
      buildsbt <- getTemplateFromResources[IO]("build.sbt.mustache")
      _ <- storage.store[IO]("build.sc.mustache", buildsc)
      _ <- storage.store[IO]("build.sbt.mustache", buildsbt)
    yield ()

  private def getTemplateFromResources[F[_]: Sync](path: String) =
    Sync[F].bracket {
      Sync[F].delay(Source.fromResource(path))
    }{s => 
      Sync[F].delay(s.mkString)
    }(s => Sync[F].delay(s.close))
    
end MongoTemplateLoader
      
def connection(mongoConfig: MongoStorage, collection: String) =
  IO:
    MongoClient(s"mongodb://root:example@${mongoConfig.host}:${mongoConfig.port}/")
      .getDatabase(mongoConfig.database)
      .getCollection(collection)

def connectionF[F[_]: Sync](mongoConfig: MongoStorage, collection: String) =
  Sync[F].delay:
    MongoClient(s"mongodb://root:example@${mongoConfig.host}:${mongoConfig.port}/")
      .getDatabase(mongoConfig.database)
      .getCollection(collection)