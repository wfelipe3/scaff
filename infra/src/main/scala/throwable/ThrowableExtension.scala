package throwable

import java.io.PrintWriter
import java.io.StringWriter
import zio.console._

extension (t: Throwable)
  def stackTraceToString =
    val s = new StringWriter
    t.printStackTrace(new PrintWriter(s))
    s.toString
  
  def logError =
    putStrLnErr(t.stackTraceToString)