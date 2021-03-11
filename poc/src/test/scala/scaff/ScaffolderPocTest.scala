package scaff

import zio.test._
import zio.test.Assertion._
import cats.implicits._
import scaff.ProjectModel._
import Project._

object ScaffolderPocTest extends DefaultRunnableSpec:
   
  def spec =
    suite("Project")(
      test("Create empty project"){
        assert(Project.create(poc))(equalTo(Project(poc, Map.empty)))
      },
      test("Add module to project") {
        assert(
          project.addModule(coreModule())
        )(equalTo(Project.create(poc, coreModule())))
      },
      test("Get module from project"){
        val project = Project.create(poc).addModule(coreModule())
        assert(project.getModule(core).toEither)(isRight(isSome(equalTo(coreModule()))))
      },
      test("Add dependency to project") {
        val project = Project.create(poc).addModule(coreModule())
        assert(
          project.addDependency(core, zio)
        )(equalTo(Project.create(poc, coreModule(Set(zio)))))
      },
      test("Get dependencies for a module should return all dependencies or empty") {
        import Project._
        assert(
          Project
            .create(poc)
            .addModule(coreModule())
            .addDependency(core, zio)
            .getDependencies(core)
            .toEither
        )(isRight(contains(zio))) && 
        assert(
          Project
            .create(poc)
            .addModule(coreModule())
            .getDependencies(core)
            .toEither
        )(isRight(isEmpty)) && 
        assert(
          Project
            .create(poc)
            .getDependencies(core)
        )(isEmpty) &&
        assert(
          Project
            .create(poc)
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependency(core, cats)
            .getDependencies(core)
            .toEither
        )(isRight(contains(zio) && contains(cats))) &&
        assert(
          Project
            .create(poc)
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependency(core, cats)
            .getDependencies(infra)
            .toEither
        )(isRight(isEmpty))  &&
        assert(
          project
            .addModule(module(core))
            .addModule(module(infra))
            .addModule(module(core))
        )(equalTo(moduleAlredyInProject(core)))
      },
      test("update scala version to module") {
        assert(
          Project.create(poc)
            .addModule(coreModule())
            .updateScalaVersion(core, scala_3_0_0)
        )(equalTo(
          Project(poc, Map(core -> coreModule(version = scala_3_0_0)))
        ))
      }, 
      test("Get scala version from module") {
        assert(
          Project.create(poc)
            .addModule(coreModule())
            .updateScalaVersion(core, scala_3_0_0)
            .getScalaVersion(core)
            .toEither
        )(isRight(isSome(equalTo(scala_3_0_0)))) &&
        assert(
          Project.create(poc)
            .addModule(coreModule())
            .updateScalaVersion(core, scala_3_0_0)
            .getScalaVersion(infra)
            .toEither
        )(isRight(isNone))
      },
      test("Get scala dependnecies") {
        import Project._
        assert(
          project
            .getScalaDependencies(core)
        )(isEmpty) &&
        assert(
          project
            .addModule(coreModule())
            .getScalaDependencies(core)
            .toEither
        )(isRight(isEmpty)) &&
        assert(
          project
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
          project
            .addModule(coreModule())
            .addDependency(core, zio)
            .addDependencies(core, Set(cats, azure))
            .getJavaDependencies(core)
            .toEither
        )(isRight(not(contains(zio)) && not(contains(cats)) && contains(azure)))
      },test("add many things") {
        assert(
          project
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
          project
            .addModule(module(core))
            .addScalaOptions(core, Set(crossCompile))
            .addScalaOptions(core, Set(higherKindedTypes))
        )(equalTo(Project.create(poc,
          module(name = core, scalaOptions = Set(crossCompile, higherKindedTypes))
        )))
      },
      test("get scala options for a given module") {
        assert(
          project
            .addModule(module(core))
            .addScalaOptions(core, Set(higherKindedTypes, crossCompile))
            .getScalaOptions(core)
            .toEither
        )(isRight(contains(higherKindedTypes) && contains(crossCompile))) &&
        assert(
          project
            .addModule(module(core))
            .addScalaOptions(core, Set(higherKindedTypes, crossCompile))
            .getScalaOptions(infra)
            .toEither
        )(isRight(isEmpty))
      },
      test("Add test config to module") {
        assert(
          project
            .addModule(module(core))
            .addTestConfig(core, zioTestFramework)
        )(equalTo(Project.create(poc, module(name = core, testConfigs = zioTestFramework.pure[List])))) &&
        assert(
          project
            .addModule(module(core))
            .addTestConfig(infra, zioTestFramework)
        )(equalTo(Project.create(poc, module(name = core)))) && 
        assert(
          project
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
          project
            .addModule(module(core))
            .addTestConfig(core, scalaTestFramework)
            .addTestConfigs(core, List(zioTestFramework))
            .getTestConfigs(core)
            .toEither
        )(isRight(contains(zioTestFramework) && contains(scalaTestFramework)))
      },
      test("Add module dependency should return project with module dependency") {
        assert(
          project
            .addModule(module(core))
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDep(infra)))
        )(equalTo(
          project
            .addModule(module(infra))
            .addModule(module(
              name = core, 
              moduleDependencies = Set(moduleDep(infra))
            ))
        ))
      },
      test("add module dependency for module that does not exist then return initial project") {
        assert(
          project
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDep(infra)))
        )(equalTo(
          project
            .addModule(module(infra))
        )) &&
        assert(
          project
            .addModule(module(core))
            .addModuleDeps(core, Set(moduleDep(infra)))
        )(equalTo(
          project
            .addModule(module(core))
        )) &&
        assert(
          project
            .addModule(module(core))
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDep(infra)))
            .addModuleDeps(infra, Set(moduleDep(core)))
        )(equalTo(
          cycleDeps(infra, List(core))
        )) &&
        assert(
          project
            .addModule(module(core))
            .addModule(module(infra))
            .addModuleDeps(core, Set(moduleDep(infra)))
        )(equalTo(
          project
            .addModule(module(name = core, moduleDependencies = Set(moduleDep(infra))))
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

  def project = 
    Project.create(poc)

  def coreModule(dependencies: Set[Dependency] = Set.empty, version: ScalaVersion = scala_2_13_4) = 
    module(name = core, dependencies = dependencies, version = version)

  def module(
    name: ModuleName, 
    dependencies: Set[Dependency] = Set.empty, 
    moduleDependencies: Set[ModuleDependency] = Set.empty,
    version: ScalaVersion = scala_2_13_4, 
    scalaOptions: Set[ScalaOption] = Set.empty,
    testConfigs: List[ProjectModel.TestConfig] = List.empty
  ) = 
    Module(
      name = name,
      scalaVersion = version,
      scalaOptions = scalaOptions,
      dependencies = dependencies,
      moduleDependencies = moduleDependencies,
      testConfigs = testConfigs
    )

  def moduleAlredyInProject(name: ModuleName) =
    ProjectErrors.ModuleAlreadyInProject(name)

  def cycleDeps(name: ModuleName, deps: List[ModuleName]) =
    ProjectErrors.CyclicDependencies(name, deps)

  def moduleDep(name: ModuleName) =
    ModuleDependency(name)

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
    ProjectModel.TestConfig(
      TestFramework("zioTestFramework"),
      Set(zioTest)
    )

  val scalaTestFramework =
    ProjectModel.TestConfig(
      None,
      Set(zioTest, scalaTest)
    )