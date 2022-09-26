
// cSpell:ignore javac, xlint, Yrangepos, Dorg, deps, classpath, RC
// cSpell:ignore munit

import mill._
import mill.api.Loose
import mill.define.{Target, Task}
import scalalib._
import mill.modules.Jvm



// https://github.com/scalameta/mdoc/issues/702
val ScalaVersion         = "3.2.1-RC2" // "3.2.1-RC2" "3.2.1-RC1" "3.2.0" "3.1.3"

val mUnitVersion         = "1.0.0-M6" // "1.0.0-M3" https://mvnrepository.com/artifact/org.scalameta/munit
val ivyMunit                = ivy"org.scalameta::munit::$mUnitVersion"
val ivyMunitInterface = "munit.Framework"



/**
 * ./mill -i tutorial.runMain core.HelloWorld
 * ./mill -i --watch tutorial.runMain core.HelloWorld
 * ./mill -i tutorial.mdoc
 * ./mill -i  --watch tutorial.mdoc
 * ./mill -i tutorial.mdoc | grep -i mdoc
 */
// object tutorial extends ScalaModule with MDocModule {
object tutorial extends ScalaModule {
  // Also used by mill-mdoc
  override def scalaVersion = T{ ScalaVersion }

  override def javacOptions = T{ Seq("-source", "17", "-target", "17", "-Xlint") }
  override def scalacOptions = T{ Seq("-deprecation", "-feature") }

  // mdoc
  def scalaMdocVersion = T("2.3.3") // "2.3.4" "2.3.3" "2.2.4"
  // def mdocSources = T.sources{ T.workspace / "docs" }  
  def mdocSources = T.sources { super.millSourcePath / "docs" }

  def mdocDep: Task[Agg[Dep]] = T.task{ Agg(ivy"org.scalameta::mdoc:${scalaMdocVersion()}") } 

  // Only downloads source code
  // resolveDeps(mdocDep, sources = true)
  def mDocLibs = T{ resolveDeps(mdocDep) }

  val separator = java.io.File.pathSeparatorChar
  def toArgument(p: Agg[os.Path]) = p.mkString(s"$separator")
  def toArg(p: Set[os.Path]) = p.mkString(s"$separator")

  // Correct the bug in the mill-mdoc plugin
  // https://github.com/atooni/mill-mdoc/issues/5
  def mdoc : T[PathRef] = T {
  
    val rp = mDocLibs().map(_.path)
    val cp = runClasspath().map(_.path)
    // println(toArgument(rp))
    // println(toArgument(cp))

    // We only add the required libraries. 
    // Here we debug to check class paths are ok
    // val s1 = Set(cp.toList:_*)
    // val s2 = Set(rp.toList:_*)
    // val s = s1 -- s2
    // println(toArg(s))

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

  override def ivyDeps = T{ Agg() }

  object test extends Tests with TestModule.Munit {

    def ivyDeps = Agg(ivyMunit)
    def testFramework = ivyMunitInterface
  }
}

