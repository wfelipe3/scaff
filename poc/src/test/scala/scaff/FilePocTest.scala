package scaff

import zio.test._
import zio.test.Assertion._
import scaff.FileModel._
import FileModel.FileStructure._
import cats.data.NonEmptyList
import FilePath._

object FilePocTest extends DefaultRunnableSpec:
  def spec =
    suite("Files")(
      test("add file to fileStructure") {
        assert(root)(equalTo(dir(name("root")))) &&
        assert(
          root.add(helloWorld)
        )(equalTo(
          dir(name("root"), Map(name("hello.txt") -> helloWorld))
        )) &&
        assert(
          helloWorld.add(helloWorld)
        )(equalTo(helloWorld))
      },
      test("Get file from file structure") {
        assert(root.add(helloWorld).get(name("hello.txt")))(isSome(equalTo(helloWorld))) &&
        assert(root.add(helloWorld).get(name("hello2.txt")))(isNone) &&
        assert(helloWorld.get(name("root")))(isNone)
      },
      test("Get with path") {
        assert(root.add(helloWorld).get(NonEmptyList.one(name("hello.txt"))))(isSome(equalTo(helloWorld))) &&
        assert(
          root
            .add(dir(name("src"))
            .add(dir(name("scala")).add(helloWorld)))
            .get(name("src") / name("scala") / name("hello.txt"))
        )(isSome(equalTo(helloWorld)))
      },
      test("Add with path") {
        assert(
          root.add(dir(name("src"))).add(path(name("src2")), helloWorld)
        )(equalTo(root.add(dir(name("src"))))) &&
        assert(
          root.add(dir(name("src"))).add(path(name("src")), helloWorld)
        )(equalTo(root.add(dir(name("src")).add(helloWorld)))) &&
        assert(
          root
            .add(dir(name("src")).add(dir(name("main")).add(dir(name("scala")))))
            .add(name("src") / name("main") / name("scala"), helloWorld)
            .get(name("src") / name("main") / name("scala") / name("hello.txt"))
        )(isSome(equalTo(helloWorld)))
      },
      test("add dir when dir already exists") {
        assert(
          root
            .add(dir(name("src")).add(dir(name("test")).add(helloWorld)))
            .add(path(name("src")), dir(name("test")))
            .get(name("src") / name("test"))
        )(isSome(equalTo(dir(name("test")))))
      },
      test("Add creating path") {
        assert(
          root
            .addCreating(name("src") / name("main") / name("scala"), helloWorld)
            .get(name("src") / name("main") / name("scala") / name("hello.txt"))
        )(isSome(equalTo(helloWorld))) &&
        assert(
          root
            .add(dir(name("src")))
            .add(path(name("src")), helloWorld)
            .addCreating(name("src") / name("main") / name("scala"), helloWorld)
            .get(name("src")/ name("hello.txt"))
        )(isSome(equalTo(helloWorld))) &&
        assert(
          root
            .add(dir(name("src")))
            .add(path(name("src")), helloWorld)
            .addCreating(name("src") / name("main") / name("scala"), helloWorld)
            .get(name("src") / name("main") / name("scala") / name("hello.txt"))
        )(isSome(equalTo(helloWorld)))
      },
      test("create path") {
        assert(
          root
            .create(name("src") / name("main") / name("scala"))
            .create(name("src") / name("test") / name("scala"))
        )(equalTo(
          root
            .add(dir(name("src")).add(dir(name("main")).add(dir(name("scala")))))
            .create(name("src") / name("test") / name("scala"))
        ))
      }
    )

  val root = dir(name("root"))
  val helloWorld = file(name("hello.txt"), "hello world")

  def name(n: String) =
    Name(n).get
