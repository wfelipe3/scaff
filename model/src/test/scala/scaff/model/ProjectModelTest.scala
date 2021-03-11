package scaff.model

import zio.test._
import zio.test.Assertion._
import cats.implicits._
import scaff.model.project._
import scaff.model.project.{TestConfig => TConfig}
import Project._
import ProjectErrors.{moduleAlreadyInProject, modulesNotFound, cyclicDependencies}

object ProjectModelTest extends DefaultRunnableSpec:

  def spec =
    suite("Project")(
      test("Create empty project"){
        assert(project(poc))(equalTo(Project(poc, Map.empty)))
      },
      test("Add module to project") {
        assert(
          pocProject.addModule(coreModule())
        )(equalTo(project(poc, coreModule())))
      },
      test("Get module from project"){
        val project = pocProject.addModule(coreModule())
        assert(project.getModule(core).toEither)(isRight(isSome(equalTo(coreModule()))))
      },
      test("Add dependency to project") {
        val project = pocProject.addModule(coreModule())
        assert(
          project.addDependency(core, zio)
        )(equalTo(Project.create(poc, coreModule(Set(zio)))))
      },
      test("Get dependencies for a module should return all dependencies or empty") {
        assert(
          pocProject
            .addModule(coreModule())
            .addDependency(core, zio)
            .getDependencies(core)
            .toEither
        )(isRight(contains(zio))) && 
        assert(
          pocProject
            .addModule(coreModule())
            .getDependencies(core)
            .toEither
        )(isRight(isEmpty)) && 
        assert(
            pocProject.getDependencies(core).toEither
        )(isRight(isEmpty)) &&
        assert(
          pocProject
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependency(core, cats)
            .getDependencies(core)
            .toEither
        )(isRight(contains(zio) && contains(cats))) &&
        assert(
          pocProject
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependency(core, cats)
            .getDependencies(infra)
            .toEither
        )(isRight(isEmpty))  &&
        assert(
          pocProject
            .addModule(module(core))
            .addModule(module(infra))
            .addModule(module(core))
        )(equalTo(moduleAlreadyInProject(core)))
      },
      test("update scala version to module") {
        assert(
          pocProject
            .addModule(coreModule())
            .updateScalaVersion(core, scala_3_0_0)
        )(equalTo(
          Project(poc, Map(core -> coreModule(version = scala_3_0_0)))
        ))
      }, 
      test("Get scala version from module") {
        assert(
          pocProject
            .addModule(coreModule())
            .updateScalaVersion(core, scala_3_0_0)
            .getScalaVersion(core)
            .toEither
        )(isRight(isSome(equalTo(scala_3_0_0)))) &&
        assert(
          pocProject
            .addModule(coreModule())
            .updateScalaVersion(core, scala_3_0_0)
            .getScalaVersion(infra)
            .toEither
        )(isRight(isNone))
      },
      test("Get scala dependnecies") {
        import Project._
        assert(
          pocProject
            .getScalaDependencies(core)
            .toEither
        )(isRight(isEmpty)) &&
        assert(
          pocProject
            .addModule(coreModule())
            .getScalaDependencies(core)
            .toEither
        )(isRight(isEmpty)) &&
        assert(
          pocProject
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependencies(core, Set(cats, azure))
            .getScalaDependencies(core)
            .toEither
        )(isRight(contains(zio) && contains(cats) && not(contains(azure))))
      },
      test("Get java dependencies") {
        import Project._
        assert(
          pocProject
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependencies(core, Set(cats, azure))
            .getJavaDependencies(core)
            .toEither
        )(isRight(not(contains(zio)) && not(contains(cats)) && contains(azure)))
      },test("add many things") {
        assert(
          pocProject
            .addModule(coreModule())
            .addDependencies(core, Set(zio, cats))
            .addModule(module(infra))
            .addDependency(infra, azure)
        )(equalTo(
          Project.create(poc, 
            module(name = core, dependencies = Set(zio, cats)), 
            module(name = infra, dependencies = Set(azure))
          )
        ))
      },
      test("Add scala options to module") {
        assert(
          pocProject
            .addModule(module(core))
            .addScalaOptions(core, Set(crossCompile))
            .addScalaOptions(core, Set(higherKindedTypes))
        )(equalTo(Project.create(poc,
          module(name = core, scalaOptions = Set(crossCompile, higherKindedTypes))
        )))
      },
      test("get scala options for a given module") {
        assert(
          pocProject
            .addModule(module(core))
            .addScalaOptions(core, Set(higherKindedTypes, crossCompile))
            .getScalaOptions(core)
            .toEither
        )(isRight(contains(higherKindedTypes) && contains(crossCompile))) &&
        assert(
          pocProject
            .addModule(module(core))
            .addScalaOptions(core, Set(higherKindedTypes, crossCompile))
            .getScalaOptions(infra)
            .toEither
        )(isRight(isEmpty))
      },
      test("Add test config to module") {
        assert(
          pocProject
            .addModule(module(core))
            .addTestConfig(core, zioTestFramework)
        )(equalTo(Project.create(poc, module(name = core, testConfigs = zioTestFramework.pure[List])))) &&
        assert(
          pocProject
            .addModule(module(core))
            .addTestConfig(infra, zioTestFramework)
        )(equalTo(Project.create(poc, module(name = core)))) && 
        assert(
          pocProject
            .addModule(module(core))
            .addTestConfigs(core, List(zioTestFramework, scalaTestFramework))
        )(equalTo(
          Project.create(poc, module(
            name = core, 
            testConfigs = List(zioTestFramework, scalaTestFramework)
          )
        )))
      },
      test("Get test config") {
        assert(
          pocProject
            .addModule(module(core))
            .addTestConfig(core, scalaTestFramework)
            .addTestConfigs(core, List(zioTestFramework))
            .getTestConfigs(core)
            .toEither
        )(isRight(contains(zioTestFramework) && contains(scalaTestFramework)))
      },
      test("Add module dependency should return project with module dependency") {
        assert(
          pocProject
            .addModule(module(core))
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDependency(infra)))
        )(equalTo(
          pocProject
            .addModule(module(infra))
            .addModule(module(
              name = core, 
              moduleDependencies = Set(moduleDependency(infra))
            ))
        ))
      },
      test("add module dependency for module that does not exist then return initial project") {
        assert(
          pocProject
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDependency(infra)))
        )(equalTo(
          pocProject
            .addModule(module(infra))
        )) &&
        assert(
          pocProject
            .addModule(module(core))
            .addModuleDeps(core, Set(moduleDependency(infra)))
        )(equalTo(
          pocProject
            .addModule(module(core))
        )) &&
        assert(
          pocProject
            .addModule(module(core))
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDependency(infra)))
            .addModuleDeps(infra, Set(moduleDependency(core)))
        )(equalTo(
          cyclicDependencies(infra, List(core))
        )) &&
        assert(
          pocProject
            .addModule(module(core))
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDependency(infra)))
        )(equalTo(
          pocProject
            .addModule(module(name = core, moduleDependencies = Set(moduleDependency(infra))))
            .addModule(module(infra))
        ))
      },
    )

  def poc = ProjectName("poc").get

  val core = ModuleName("core").get
  val infra = ModuleName("infra").get

  val scala_2_13_4 = ScalaVersion("2.13.4").get
  val scala_2_13_3 = ScalaVersion("2.13.3").get
  val scala_3_0_0 = ScalaVersion("3.0.0-M3").get

  def pocProject = 
    Project.create(poc)

  def coreModule(dependencies: Set[Dependency] = Set.empty, version: ScalaVersion = scala_2_13_4) = 
    module(name = core, dependencies = dependencies, version = version)

  def module(
    name: ModuleName, 
    dependencies: Set[Dependency] = Set.empty, 
    moduleDependencies: Set[ModuleDependency] = Set.empty,
    version: ScalaVersion = scala_2_13_4, 
    scalaOptions: Set[ScalaOption] = Set.empty,
    testConfigs: List[TConfig] = List.empty
  ) = 
    Project.module(name, dependencies, moduleDependencies, version, scalaOptions, testConfigs)

  val crossCompile = ScalaOption("crossCompile").get
  val higherKindedTypes = ScalaOption("higherKindedTypes").get

  val zio: Dependency = 
    Dependency.Scala(
      "org.zio", 
      "zio", 
      "1.0.0"
    )

  val cats: Dependency = 
    Dependency.Scala(
      "org.typelevel", 
      "cats", 
      "2.0.0"
    )

  val azure: Dependency = 
    Dependency.Java(
      "org.azure",
      "azure",
      "10.0.0"
    )

  val zioTest: Dependency = 
    Dependency.Scala(
      "org.zio", 
      "zio-test", 
      "1.0.0"
    )
    
  val scalaTest: Dependency = 
    Dependency.Scala(
      "org.scalatest", 
      "test-scalatest", 
      "1.0.0"
    )

  val zioTestFramework =
    TConfig(
      TestFramework("zioTestFramework"),
      Set(zioTest)
    )

  val scalaTestFramework =
    TConfig(
      None,
      Set(zioTest, scalaTest)
    )