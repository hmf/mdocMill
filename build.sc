
// cSpell:ignore javac, xlint, Yrangepos, Dorg, deps, classpath, RC
// cSpell:ignore munit, agg, dep

import mill._
import mill.api.Loose
import mill.define.{Target, Task}
import scalalib._
import mill.modules.Jvm



// https://github.com/scalameta/mdoc/issues/702
val ScalaVersion         = "3.3.0" // "3.2.1-RC2" "3.2.1-RC1" "3.2.0" "3.1.3"

val mUnitVersion         = "1.0.0-M8" // "1.0.0-M3" https://mvnrepository.com/artifact/org.scalameta/munit
val ivyMunit                = ivy"org.scalameta::munit::$mUnitVersion"
val ivyMunitInterface = "munit.Framework"


/**
 * Example of using MDoc with the projects Scala version. 
 * @ see [[mdocDep]]
 * 
 * ./mill -i tutorial.runMain core.HelloWorld
 * ./mill -i --watch tutorial.runMain core.HelloWorld
 * ./mill -i tutorial.mdoc
 * ./mill -i  --watch tutorial.mdoc
 * ./mill -i tutorial.mdoc | grep -i mdoc
 */
object tutorial extends ScalaModule {
  // Also used by mill-mdoc
  override def scalaVersion = T{ ScalaVersion }

  override def javacOptions = T{ Seq("-source", "17", "-target", "17", "-Xlint") }
  override def scalacOptions = T{ Seq("-deprecation", "-feature") }

  // mdoc
  def scalaMdocVersion = T("2.3.7") // "2.3.4" "2.3.3" "2.2.4"
  // def mdocSources = T.sources{ T.workspace / "docs" }  
  def mdocSources = T.sources { super.millSourcePath / "docs" }

  // https://github.com/scalameta/mdoc/issues/702
  // MDoc has its own dependencies on the Scala compiler and uses those
  // To use a later version of Scala 3, we need to download that version of the compiler
  def mdocDep: Task[Agg[Dep]] = T.task{ 
      Agg(
        ivy"org.scalameta::mdoc:${scalaMdocVersion()}"
          .exclude("org.scala-lang" -> "scala3-compiler_3")
          .exclude("org.scala-lang" -> "scala3-library_3"),
        ivy"org.scala-lang::scala3-compiler:${scalaVersion()}"
      )
    } 

  // Only downloads source code
  // resolveDeps(mdocDep, sources = true)
  def mDocLibs = T{ resolveDeps(mdocDep) }

  val separator = java.io.File.pathSeparatorChar
  def toArgument(p: Agg[os.Path]) = p.iterator.mkString(s"$separator")
  def toArg(p: Set[os.Path]) = p.mkString(s"$separator")
  def toArgumentDebug(p: Agg[os.Path]) = p.iterator.mkString(s"\n")

  // Correct the bug in the mill-mdoc plugin
  // https://github.com/atooni/mill-mdoc/issues/5
  def mdocLocal : T[PathRef] = T {
  
    val rp = mDocLibs().map(_.path)
    // val cp = runClasspath().map(_.path)
    val cp = compileClasspath().map(_.path)
    // println(toArgumentDebug(rp))
    // println(toArgument(cp))

    // Set-up parameters to execute MDoc
    val dir = T.dest.toIO.getAbsolutePath
    val dirParams = mdocSources().map(pr => Seq(
        "--classpath", toArgument(cp),
        "--in", pr.path.toIO.getAbsolutePath, 
        "--out",  dir)
      ).iterator.flatten.toSeq
  
    // Execute MDoc, only pass the MDoc class path to execute the MDoc compiler
    Jvm.runLocal("mdoc.Main", rp, dirParams)
  
    PathRef(T.dest)
  }

  def mdoc: T[PathRef] = T {
    //val cp = runClasspath().map(_.path)
    val cp = compileClasspath().map(_.path)
    val rp = mDocLibs().map(_.path)
    val dir = T.dest.toIO.getAbsolutePath
    val dirParams = mdocSources().map(pr => Seq(
      s"--in", pr.path.toIO.getAbsolutePath,
      "--out", dir)
    ).iterator.flatten
    val docClasspath = toArgument(cp)
    val params = Seq("--classpath", s"$docClasspath") ++ dirParams.toSeq

    Jvm.runSubprocess(
      mainClass = "mdoc.Main",
      classPath = rp,
      jvmArgs = forkArgs(),
      envArgs = forkEnv(),
      mainArgs = params,
      // Defaults
      workingDir = forkWorkingDir(),
      useCpPassingJar = runUseArgsFile()
    )

    PathRef(T.dest)
  }


  override def ivyDeps = T{ Agg() }

  object test extends Tests with TestModule.Munit {

    override def ivyDeps = Agg(ivyMunit)
    //override def testFramework = ivyMunitInterface
  }
}

