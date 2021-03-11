package template

import zio.ZIO
import yamusca.imports._
import yamusca.implicits._
import yamusca.converter.ValueConverter
import file.FileEffects._
import java.nio.file.Path

def renderTemplate(template: String)(context: Context) =
  ZIO.fromEither(
    mustache.parse(template).map(d => mustache.render(d)(context))
  )
  .mapError(e => new Exception(e._2))
  .orDie

extension (path: Path)
  def render(context: Context) =
    for
      t <- path.readFileFromResources
      c <- renderTemplate(t)(context)
    yield c
