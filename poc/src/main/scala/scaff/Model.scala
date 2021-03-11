package scaff

import cats.implicits._
import cats.Show
import cats.data.NonEmptyList
import monocle.Lens
import monocle.Prism
import monocle.function.At._
import scala.util.chaining._
import java.nio.file.Paths

object FileModel:

  opaque type Name = String

  object Name:
    val nameRegex = raw"[a-zA-Z][a-zA-Z0-9\-\.]*".r
    def apply(name: String): Option[Name] =
      name match 
        case nameRegex(_*) => name.some
        case _ => None

    object Extensions:
      given Show[Name] = 
          Show.show[Name](identity)

      extension (n: Name)
        def toPath =
          Paths.get(n.show)

    given GenericContextConverter[Name] with
      extension (name: Name)
        def toGeneric = GenericContext.Literal(name)

  type Files = Map[Name, FileStructure]

  trait Templetable[A, B]:
    extension (a: A)
      def toTemplate: B

  type TemplatableContext[A] = Templetable[A, GenericContext]

  enum FileStructure(name: Name):
    case TemplateFile[A](
      name: Name, 
      template: Path, 
      data: A,
      templatable: TemplatableContext[A]
    ) extends FileStructure(name)
    case Dir(name: Name, files: Files = Map.empty[Name, FileStructure]) extends FileStructure(name)
    case File(name: Name, content: String) extends FileStructure(name)

    def getName = name

  enum Path:
    case Resources(path: String)
    case External(path: String)

  object FileStructure:
    def dir(name: Name, files: Files = Map.empty[Name, FileStructure]): FileStructure =
      FileStructure.Dir(name, files)

    def file(name: Name, content: String): FileStructure =
      FileStructure.File(name, content)

    def template[A: TemplatableContext](name: Name, template: Path, data: A)(using templatable: TemplatableContext[A]) =
      FileStructure.TemplateFile(name, template, data, templatable)

  type FilePath = NonEmptyList[Name]

  object FilePath:
    extension (name: Name)
      def /(n: Name): FilePath =
        NonEmptyList.of(name, n)

    extension (path: FilePath)
        def /(name: Name): FilePath =
          path.append(name)

    def path(name: Name): FilePath =
      NonEmptyList.one(name)

  def filesLens = Lens[FileStructure, Files]{
    case FileStructure.Dir(name, files) =>
      files
    case _ =>
      Map.empty
  }(f => s => {
    s match
      case d: FileStructure.Dir =>
        d.copy(files = f)

      case _ =>
        s
  })

  def optionFileLens = Lens[Option[FileStructure], Files] {
    _.fold(Map.empty){
      case FileStructure.Dir(name, files) =>
        files
      case _ =>
        Map.empty
    }
  }(f => s => {
    s.flatMap{
      case d: FileStructure.Dir =>
        d.copy(files = f).some
      case _ =>
        s
    }
  })

  extension (files: FileStructure)

    def addAll(otherFiles: List[FileStructure]) =
      otherFiles.foldLeft(files)(_ add _)

    def add(file: FileStructure): FileStructure =
      filesLens.modify(_ + (file.getName -> file))(files)

    def get(name: Name): Option[FileStructure] =
      get(FilePath.path(name))

    def get(path: FilePath) =
      path
        .map(n => optionFileLens.andThen(at[Files, Name, Option[FileStructure]](n)))
        .reduce(_ andThen _)
        .get(files.some)

    def add(path: FilePath, file: FileStructure): FileStructure =
      path
        .map(n => optionFileLens.andThen(at[Files, Name, Option[FileStructure]](n)))
        .reduce(_ andThen _)
        .modify(_.map(_.add(file)))(files.some)
        .get

    def addCreating(path: FilePath, file: FileStructure) = 
      files.create(path).add(path, file)

    def create(path: FilePath) =
      path.foldLeft((files, List.empty[Name])){ case ((f, currentPath), name) =>
        val newNamesPath = currentPath :+ name
        val newPath = NonEmptyList.fromList(newNamesPath).get
        f.get(newPath)  
          .fold{
            if currentPath.isEmpty then
              (f.add(FileStructure.Dir(name)), newNamesPath)
            else
              (f.add(NonEmptyList.fromList(currentPath).get, FileStructure.Dir(name)), newNamesPath)
          }(_ => (f, newNamesPath))
      }._1


  enum GenericContext:
    case Literal(value: String)
    case Entity(values: Map[String, GenericContext])
    case Values(values: Seq[GenericContext])

  trait GenericContextConverter[A]:
    extension (a: A)
      def toGeneric: GenericContext

  object GenericContext:
    def literal(value: String) =
      GenericContext.Literal(value)

    def entity(values: Map[String, GenericContext]) =
      GenericContext.Entity(values)

    def entityValues(values: (String, GenericContext)*) =
      GenericContext.entity(values.toMap)

    def values(vals: Seq[GenericContext]) =
      GenericContext.Values(vals)

    def of[A: GenericContextConverter](a: A) =
      summon[GenericContextConverter[A]].toGeneric(a)

    def many[A: GenericContextConverter](value: A, tail: A*) = 
      (value +: tail)
        .map(of)
        .pipe(values)
      
    given GenericContextConverter[Int] with
      extension (a: Int)
        def toGeneric: GenericContext = 
          literal(a.toString)

    given GenericContextConverter[String] with
      extension (a: String)
        def toGeneric: GenericContext = 
          literal(a)

    given [A](using GenericContextConverter[A]): GenericContextConverter[Seq[A]] with
      extension (a: Seq[A])
        def toGeneric: GenericContext =
          GenericContext.Values(a.map(_.toGeneric))

    given [A](using GenericContextConverter[A]): GenericContextConverter[Map[String, A]] with
      extension (m: Map[String, A])
        def toGeneric: GenericContext =
          GenericContext.entity(m.map{ case (key, value) => (key, of(value))})


object ProjectModel:
  import FileModel.GenericContext
  import FileModel.GenericContextConverter

  opaque type ProjectName = String

  object ProjectName:
    val nameRegex = raw"[a-zA-Z][a-zA-Z0-9\-\.]*".r
    def apply(name: String): Option[ProjectName] =
      name match 
        case nameRegex(_*) => name.some
        case _ => None

    given GenericContextConverter[ProjectName] with
      extension (name: ProjectName)
        def toGeneric = GenericContext.Literal(name)

    extension (projectName: ProjectName)
      def toFileName: Option[FileModel.Name] = FileModel.Name(projectName)

  opaque type ModuleName = String

  object ModuleName:
    val nameRegex = raw"[a-zA-Z0-9]+".r
    def apply(name: String): Option[ModuleName] =
      fold(name)(
        ifTrue = Option.apply,
        ifFalse = _ => None
      )

    private def fold[T](name: String)(
      ifTrue: String => T, 
      ifFalse: String => T
    ) =
      name match
        case nameRegex(_*) => ifTrue(name)
        case _ => ifFalse(name)
         
    extension (m: ModuleName)
      def value: String = m

      def toFileName: Option[FileModel.Name] = FileModel.Name(m)

    given GenericContextConverter[ModuleName] with
      extension (name: ModuleName)
        def toGeneric = GenericContext.Literal(name)

    opaque type DependencyName = String

  enum Dependency:
    case Scala(
      org: String,
      name: String, 
      version: String
    )
    case Java(
      org: String,
      name: String, 
      version: String
    )

  opaque type ScalaVersion = String

  object ScalaVersion:
    def apply(version: String): Option[ScalaVersion] = 
      version match
        case "2.13.3" => version.some
        case "2.13.4" => version.some
        case "3.0.0-M3" => version.some
        case _ => None

    given GenericContextConverter[ScalaVersion] with
      extension (version: ScalaVersion)
        def toGeneric = GenericContext.Literal(version)

  opaque type ScalaOption = String

  object ScalaOption:
    def apply(scalaOption: String): Option[ScalaOption] =
      if scalaOption.isEmpty then None 
      else scalaOption.some

    given GenericContextConverter[ScalaOption] with
      extension (option: ScalaOption)
        def toGeneric = GenericContext.Literal(option)

  opaque type TestFramework = String

  object TestFramework:
    def apply(testFramework: String): Option[TestFramework] =
      if testFramework.isEmpty then None 
      else testFramework.some

  final case class TestConfig(
    testFramework: Option[TestFramework],
    dependencies: Set[Dependency]
  )

  final case class Module(
    name: ModuleName,
    scalaVersion: ScalaVersion,
    scalaOptions: Set[ScalaOption],
    dependencies: Set[Dependency],
    moduleDependencies: Set[ModuleDependency],
    testConfigs: List[TestConfig]
  )

  final case class Project(
    name: ProjectName,
    modules: Map[ModuleName, Module]
  )

  final case class ModuleDependency(name: ModuleName)

  enum ProjectErrors:
    case CyclicDependencies(name: ModuleName, deps: List[ModuleName])
    case ModulesNotFound(names: List[ModuleName])
    case ModuleAlreadyInProject(name: ModuleName)
  
  object Project:
    def create(name: ProjectName, modules: Module*) = 
      Project(name, modules.map(m => (m.name, m)).toMap)

    val projectModuleLens = Lens[Project, Map[ModuleName, Module]](_.modules)(m => p => p.copy(modules = m)) 
    def moduleNameLens(name: ModuleName) = at[Map[ModuleName, Module], ModuleName, Option[Module]](name)
    val moduleDependenciesLens = Lens[Option[Module], Set[Dependency]](_.fold(Set.empty)(_.dependencies))(d => m => m.map(_.copy(dependencies = d)) )
    val moduleScalaVersionLens = Lens[Option[Module], Option[ScalaVersion]](_.map(_.scalaVersion)) { v => m =>
      for 
        module <- m
        version <- v
      yield module.copy(scalaVersion = version)
    }
    val scalaDependenciesLens = Lens[Set[Dependency], Set[Dependency.Scala]] { 
      toSpecificDependencies {
        case s: Dependency.Scala =>
          s.some
        case _ =>
          None
      }
    }(s => d => d ++ s)
    val javaDependenciesLens = Lens[Set[Dependency], Set[Dependency.Java]]{
      toSpecificDependencies {
        case s: Dependency.Java =>
          s.some
        case _ =>
          None
      }
    }(s => d => d ++ s)
    val scalaOptionsLens = Lens[Option[Module], Set[ScalaOption]](_.fold(Set.empty)(_.scalaOptions))(o => m => m.map(_.copy(scalaOptions = o)))
    val testConfigLens = Lens[Option[Module], List[TestConfig]](_.fold(List.empty)(_.testConfigs))(t => m => m.map(_.copy(testConfigs = t)))
    val moduleDependenciesToOtherModulesLens = Lens[Option[Module], Set[ModuleDependency]](_.fold(Set.empty)(m => m.moduleDependencies))(d => m => m.map(_.copy(moduleDependencies = d)))

    def toSpecificDependencies[A <: Dependency](f: Dependency => Option[A])(dependencies: Set[Dependency]): Set[A] =
      dependencies
        .view
        .map(f)
        .filterNot(_.isEmpty)
        .toList
        .sequence
        .fold(Set.empty)(_.toSet)

    type ProjectErrorOr[A] = ProjectErrors | A

    extension [A](aOrError: ProjectErrorOr[A])
      def toEither =
        aOrError match
          case error: ProjectErrors =>
            error.asLeft[A]
          case a: A =>
            a.asRight[ProjectErrors]

    extension [A](projectOrError: ProjectErrorOr[Project])
      def addModule(module: Module): ProjectErrors | Project =
        map(_.addModule(module))

      def getModule(name: ModuleName): ProjectErrorOr[Option[Module]] =
        map(_.getModule(name))

      def addModuleDeps(name: ModuleName, deps: Set[ModuleDependency]): ProjectErrorOr[Project] =
        map(_.addModuleDeps(name, deps))

      def addDependency(name: ModuleName, dependency: Dependency): ProjectErrorOr[Project] =
        map(_.addDependency(name, dependency))

      def addDependencies(name: ModuleName, dependencies: Set[Dependency]): ProjectErrorOr[Project] =
        map(_.addDependencies(name, dependencies))

      def getDependencies(name: ModuleName): ProjectErrorOr[Set[Dependency]] =
        map(_.getDependencies(name))

      def getJavaDependencies(name: ModuleName): ProjectErrorOr[Set[Dependency.Java]] =
        map(_.getJavaDependencies(name))

      def getScalaDependencies(name: ModuleName): ProjectErrorOr[Set[Dependency.Scala]] =
        map(_.getScalaDependencies(name))

      def addModuleDependencies(name: ModuleName, deps: Set[ModuleDependency]): ProjectErrorOr[Project] =
        map(_.addModuleDeps(name, deps))

      def getModuleDeps(name: ModuleName): ProjectErrorOr[Set[ModuleDependency]] =
        map(_.getModuleDeps(name))

      def updateScalaVersion(name: ModuleName, version: ScalaVersion): ProjectErrorOr[Project] =
        map(_.updateScalaVersion(name, version))

      def getScalaVersion(name: ModuleName): ProjectErrorOr[Option[ScalaVersion]] =
        map(_.getScalaVersion(name))

      def addScalaOptions(name: ModuleName, options: Set[ScalaOption]): ProjectErrorOr[Project] = 
        map(_.addScalaOptions(name, options))

      def getScalaOptions(name: ModuleName): ProjectErrorOr[Set[ScalaOption]] =
        map(_.getScalaOptions(name))

      def addTestConfig(name: ModuleName, testConfig: TestConfig): ProjectErrorOr[Project] = 
        map(_.addTestConfig(name, testConfig))

      def addTestConfigs(name: ModuleName, testConfigs: List[TestConfig]): ProjectErrorOr[Project] = 
        map(_.addTestConfigs(name, testConfigs))

      def getTestConfigs(name: ModuleName): ProjectErrorOr[List[TestConfig]] = 
        map(_.getTestConfigs(name))

      private def map(f: Project => A | ProjectErrors): ProjectErrors | A =
        projectOrError match
          case project: Project => f(project)
          case e: ProjectErrors => e

    extension (project: Project)
      def addModule(module: Module): ProjectErrors | Project = 
        project
          .getModule(module.name)
          .fold(
            projectModuleLens
              .andThen(moduleNameLens(module.name))
              .modify(a => module.some)(project)
          )(m => moduleAlreadyInProject(module.name))

      def getModule(name: ModuleName) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .get(project)

      def addDependency(name: ModuleName, dependency: Dependency): ProjectErrors | Project =
        project
          .getModule(name)
          .fold(moduleNotFound(name)) { _ =>
            projectModuleLens
              .andThen(moduleNameLens(name))
              .andThen(moduleDependenciesLens)
              .modify(_ + dependency)(project)
          }

      def addDependencies(name: ModuleName, dependencies: Set[Dependency]) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleDependenciesLens)
          .modify(_ ++ dependencies)(project)

      def getDependencies(name: ModuleName) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleDependenciesLens)
          .get(project)

      def getScalaDependencies(name: ModuleName) = 
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleDependenciesLens)
          .andThen(scalaDependenciesLens)
          .get(project)

      def getJavaDependencies(name: ModuleName) = 
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleDependenciesLens)
          .andThen(javaDependenciesLens)
          .get(project)

      def addModuleDeps(name: ModuleName, deps: Set[ModuleDependency]): ProjectErrors | Project =
        filterModulesThatAreInProject(deps)(project)
          .pipe { deps =>
            val cycles = findCycles(name, deps)(project)
            if cycles.isEmpty then
              unsafeAddModuleDep(name, deps)
            else
              cyclicDeps(name, cycles)
          }

      def getModuleDeps(name: ModuleName) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleDependenciesToOtherModulesLens)
          .get(project)

      def updateScalaVersion(name: ModuleName, version: ScalaVersion) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleScalaVersionLens)
          .replace(version.some)(project)

      def getScalaVersion(name: ModuleName) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleScalaVersionLens)
          .get(project)

      def addScalaOptions(name: ModuleName, options: Set[ScalaOption]) = 
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(scalaOptionsLens)
          .modify(_ ++ options)(project)

      def getScalaOptions(name: ModuleName) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(scalaOptionsLens)
          .get(project)

      def addTestConfig(name: ModuleName, testConfig: TestConfig) = 
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(testConfigLens)
          .modify(_ :+ testConfig)(project)

      def addTestConfigs(name: ModuleName, testConfigs: List[TestConfig]) = 
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(testConfigLens)
          .modify(_ ++ testConfigs)(project)

      def getTestConfigs(name: ModuleName) = 
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(testConfigLens)
          .get(project)

      private def moduleAlreadyInProject(name: ModuleName) =
        ProjectErrors.ModuleAlreadyInProject(name)

      private def cyclicDeps(name: ModuleName, deps: List[ModuleName]) =
        ProjectErrors.CyclicDependencies(name, deps)

      private def moduleNotFound(name: ModuleName) =
        ProjectErrors.ModulesNotFound(name.pure[List])

      private def unsafeAddModuleDep(name: ModuleName, deps: Set[ModuleDependency]) =
        projectModuleLens
          .andThen(moduleNameLens(name))
          .andThen(moduleDependenciesToOtherModulesLens)
          .modify(_ ++ deps)(project)


    private def filterModulesThatAreInProject(deps: Set[ModuleDependency])(project: Project) =
      project
        .modules
        .keys
        .toList
        .pipe { modules =>
          deps.filter{ d => 
            modules.contains(d.name)
          }
        }

    private def findCycles(name: ModuleName, deps: Set[ModuleDependency])(project: Project) =
      deps
        .view
        .foldLeft(Map.empty[ModuleName, Set[ModuleName]]) { (m, d) =>
          project.getModule(d.name).fold(m){ mod =>
            m + (mod.name -> mod.moduleDependencies.map(_.name))
          }
        }
        .filter{ case (modName, deps) =>
          deps.contains(name)
        }
        .map { case (modName, _) =>
          modName
        }
        .toList

object CodeTemplateModel:
  final case class CodeModule(name: String, dependencies: Set[String], codePackage: String)
  final case class CodeTemplate(modules: List[CodeModule])
          