package file.testing

import zio.test._
import java.nio.file.Path
import java.nio.file.Files

object FileAssertions:

  def pathExists= 
    Assertion.assertion[Path]("exists")()(p => Files.exists(p))

  def isDir =
    Assertion.assertion[Path]("isDir")()(p => Files.isDirectory(p))

  def isFile =
    Assertion.assertion[Path]("isFile")()(p => Files.isRegularFile(p))

  def dirExists =
    pathExists && isDir

  def fileExists =
    pathExists && isFile
