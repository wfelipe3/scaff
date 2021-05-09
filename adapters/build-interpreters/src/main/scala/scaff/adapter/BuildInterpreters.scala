package scaff.adapter

import scaff.ports._
import scaff.model.file._
import scaff.model.file.FileStructure._
import scaff.model.project.TestFramework.given
import scaff.model.project.Project.getJavaDependencies
import scaff.model.project.Project.getScalaDependencies
import scaff.model.project._
import cats.implicits._
import scala.util.chaining._
import GenericContext.given
import ScalaVersion.given
import ScalaOption.given
import ModuleName.given

given sbtBuildToolInterpreter: BuildToolInterpreter with

  def toFiles(module: Module) = 
    for
      name <- module.name.toFileName
      src <- Name.name("src")
      main <- Name.name("main")
      scala <- Name.name("scala")
      test <- Name.name("test")
      resources <- Name.name("resources")
    yield 
      dir(name)
        .addCreating(src / main, dir(scala))
        .addCreating(src / main, dir(resources))
        .addCreating(src / test, dir(scala))
        .addCreating(src / test, dir(resources))

  def toFiles(project: Project)= 
    for
      projectName <- project
        .name
        .toFileName
      modules <- project
        .modules
        .values
        .toList
        .traverse(toFiles)
      buildSbt <- Name.name("build.sbt")
      scalaFmt <- Name.name(".scalafmt.conf")
    yield 
      dir(projectName)
        .add(file(scalaFmt, """version = "2.7.5""""))
        .add(template(buildSbt, Path.Resources("build.sbt.mustache"), project))
        .addAll(modules)

  given GenericContextConverter[Dependency] with
    extension (dependency: Dependency)
      def toGenericContext: GenericContext =
        dependency match
          case Dependency.Scala(org, name, version) =>
            GenericContext.entityValues(
              "org" -> org.toGenericContext,
              "name" -> name.toGenericContext,
              "version" -> version.toGenericContext
            )
          case Dependency.Java(org, name, version) =>
            GenericContext.entityValues(
              "org" -> org.toGenericContext,
              "name" -> name.toGenericContext,
              "version" -> version.toGenericContext
            )

    given GenericContextConverter[Dependency.Scala] with
      extension (dependency: Dependency.Scala)
        def toGenericContext: GenericContext =
          GenericContext.entityValues(
            "org" -> dependency.org.toGenericContext,
            "name" -> dependency.name.toGenericContext,
            "version" -> dependency.version.toGenericContext
          )

  given GenericContextConverter[TestConfig] with
    extension (testConfig: TestConfig)
      def toGenericContext: GenericContext =
        GenericContext.entityValues(
          "dependencies" -> testConfig.dependencies.toSeq.sorted.toGenericContext,
          "testFramework" -> testConfig.testFramework.fold(List.empty)(n => n.show.pure[List]).toSeq.toGenericContext
        )

  given GenericContextConverter[Module] with
    extension (module: Module)
      def toGenericContext: GenericContext = 
        GenericContext.entityValues(
          "name" -> module.name.toGenericContext,
          "scalaVersion" -> module.scalaVersion.toGenericContext,
          "scalaOptions" -> module.scalaOptions.toSeq.toGenericContext,
          "javaDependencies" -> module.getJavaDependencies.toSeq.sorted.toGenericContext,
          "scalaDependencies" -> module.getScalaDependencies.toSeq.sorted.toGenericContext,
          "TestModules" -> module.testConfigs.toSeq.toGenericContext
        )

  given Templetable[Project, GenericContext] with
    extension (project: Project)
      def toTemplate: GenericContext =
        GenericContext.entityValues(
          "modules" -> project.modules.values.toSeq.toGenericContext
        )

given millBuildToolInterpreter: BuildToolInterpreter with

  def toFiles(module: Module): Either[String, FileStructure] =
    for 
      name <- module.name.toFileName
      src <- Name.name("src")
      resources <- Name.name("resources")
    yield 
      dir(name)
        .add(dir(resources))
        .add(dir(src))
        .pipe: 
          mod =>
            toFiles(module.testConfigs)
              .fold(mod)(t => mod.add(t))
    
  def toFiles(project: Project): Either[String, FileStructure] = 
    for
      projectName <- project
        .name
        .toFileName
      modules <- project
        .modules
        .values
        .toList
        .traverse(toFiles)
      buildSc <- Name.name("build.sc")
      scalaFmt <- Name.name(".scalafmt.conf")
    yield 
      dir(projectName)
        .add(file(scalaFmt, """version = "2.7.5""""))
        .add(template(buildSc, Path.Resources("build.sc.mustache"), project))
        .addAll(modules)

  given GenericContextConverter[TestConfig] with
    extension (config: TestConfig)
      def toGenericContext: GenericContext =
        GenericContext.entityValues(
          "name" -> "test".toGenericContext,
          "dependencies" -> config.dependencies.toSeq.sorted.toGenericContext,
          "testFramework" -> config.testFramework.fold(List.empty)(n => n.show.pure[List]).toSeq.toGenericContext
        )

  given GenericContextConverter[Dependency] with
    extension (dependency: Dependency)
      def toGenericContext: GenericContext =
        dependency match
          case Dependency.Scala(org, name, version) =>
            s"$org::$name:$version".toGenericContext
          case Dependency.Java(org, name, version) =>
            s"$org:$name:$version".toGenericContext
        
  given GenericContextConverter[Module] with
    extension (module: Module)
      def toGenericContext: GenericContext = 
        GenericContext.entityValues(
          "name" -> module.name.toGenericContext,
          "scalaVersion" -> module.scalaVersion.toGenericContext,
          "scalaOptions" -> module.scalaOptions.toSeq.toGenericContext,
          "dependencies" -> module.dependencies.toSeq.sorted.toGenericContext,
          "testModules" -> module.testConfigs.toSeq.toGenericContext
        )

  given Templetable[Project, GenericContext] with
    extension (project: Project)
      def toTemplate: GenericContext = 
        GenericContext.entityValues(
          "name" ->  project.name.toGenericContext,
          "modules" -> project.modules.values.toSeq.toGenericContext,
        )

  private def toFiles(testConfigs: List[TestConfig]) =
    if testConfigs.isEmpty then None
    else Name("test").map(dir(_))

  extension (name: Option[Name])
    def toEither(n: String) =
      name.fold(n.asLeft)(_.asRight)
