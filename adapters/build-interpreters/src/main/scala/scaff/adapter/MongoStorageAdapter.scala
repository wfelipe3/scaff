package scaff.adapter

import org.mongodb.scala._
import scaff.config._
import scaff.model.project._
import scaff.ports._
import scala.util.chaining._
import cats.effect._
import java.util.UUID
import cats.implicits._


given mongoStorage(using MongoStorage)(using ContextShift[IO]): Storage[IO] with
  extension (projectOrError: Project.ProjectErrorOr[Project])
    def create = 
      projectOrError match
        case e: ProjectErrors =>
          IO.pure(e)
        case p: Project =>
          for
            conn <- connection(summon[MongoStorage])
            id <- save(p)(conn)
          yield id

def connection(mongoConfig: MongoStorage) =
  IO {
    MongoClient(s"mongodb://root:example@${mongoConfig.host}:${mongoConfig.port}/")
      .getDatabase(mongoConfig.database)
      .getCollection("projects")
  }

def save(project: Project)(collection: MongoCollection[Document])(using ContextShift[IO]) =
  for
    id <- IO(UUID.randomUUID)
    doc <- IO.fromFuture(IO(collection.insertOne(project.toDocument(id)).toFuture))
  yield id
    
extension (project: Project)
  def toDocument(id: UUID) =
    Document("_id" -> id.toString, "name" -> project.name.show)
      