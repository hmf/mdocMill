<!-- cSpell:ignore agg, dep, scalameta, compiletime -->

# Example of using MDoc in a Mill project. 

We should invoke the MDoc compiler using only the required libraries. Here we show how to download those libraries and call MDoc. Care must be taken to setup the compiler libraries correctly.

To compile the documentation with MDoc execute this:


```shell
./mill -i tutorial.mdoc
```

or this: 


```shell
./mill -i --watch tutorial.mdoc
```

# The Mill-MDoc plugin

The code shown here was *"lifted"* from the [Mill-MDoc plugin](https://github.com/atooni/mill-mdoc). This was born of an attempt to correct the ["bug"](https://github.com/atooni/mill-mdoc/issues/5) in the mill-mdoc plugin. The `def mdoc : T[PathRef]` shown below is an altered version of the plugin's. 
  
## Issue with compiler version

Diagnose https://github.com/scalameta/mdoc/issues/702

[MDoc](https://scalameta.org/mdoc/) has its own dependencies on the Scala compiler and uses those. To use a later version of Scala 3, we need to download that specific version of the compiler. If we don't, a compilation error will occur because earlier versions of the compiler cannot process the newer format. Here is an example of the error:

```shell
[40/40] tutorial.mdoc 
info: Compiling 1 file to /home/user/VSCodeProjects/mdocMill/out/tutorial/mdoc.dest
error: 
test.md:2 (mdoc generated code) 
 package scala.compiletime does not have a member method summonFrom
object MdocSession extends _root_.mdoc.internal.document.DocumentBuilder {
...
```

We must therefore first exclude the Scala 3 compiler related libraries that are dependencies of MDoc. Then we add the compiler version we are using in our code. Here is the relevant code snippet of the build script:

```scala 
  def mdocDep: Task[Agg[Dep]] = T.task{ 
      Agg(
        ivy"org.scalameta::mdoc:${scalaMdocVersion()}"
          .exclude("org.scala-lang" -> "scala3-compiler_3")
          .exclude("org.scala-lang" -> "scala3-library_3"),
        ivy"org.scala-lang::scala3-compiler:${scalaVersion()}"
      )
    } 

  def mDocLibs = T{ resolveDeps(mdocDep) }
```

Their are several ways in which to exclude dependent libraries (see the [tests](https://github.com/com-lihaoyi/mill/blob/5fd2543b39448733872d63e9ca44a2ac4c76183a/scalalib/test/src/ResolveDepsTests.scala)). They can be done:

* [By organization and name](https://github.com/com-lihaoyi/mill/blob/5fd2543b39448733872d63e9ca44a2ac4c76183a/scalalib/test/src/ResolveDepsTests.scala#L42);
* [By organization only](https://github.com/com-lihaoyi/mill/blob/5fd2543b39448733872d63e9ca44a2ac4c76183a/scalalib/test/src/ResolveDepsTests.scala#L48);
* [By name only](https://github.com/com-lihaoyi/mill/blob/5fd2543b39448733872d63e9ca44a2ac4c76183a/scalalib/test/src/ResolveDepsTests.scala#L56)

To find out what to exclude we looked at the dependencies in the Maven repository [here](https://mvnrepository.com/artifact/org.scalameta/mdoc_3/2.3.4). We not that the `scala3-library_3` is a dependency of the compiler and it is therefore not strictly necessary to exclude that. We then add the `scala3-compiler_3` using [a version of our choice](https://mvnrepository.com/artifact/org.scala-lang/scala3-compiler_3/3.2.2-RC1-bin-20220920-b1b1dfd-NIGHTLY).


## Using only the necessary libraries for MDoc

Once we have the libraries for MDoc we can invoke it. the relevant code snippet is shown below:

```scala
  def mdoc : T[PathRef] = T {
  
    val rp = mDocLibs().map(_.path)
    val cp = runClasspath().map(_.path)
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
```

Note that the `Jvm.runLocal("mdoc.Main", rp, dirParams)` uses the MDoc libraries and the MDoc compiler compiles the code using the project's `runClasspath()`. If this is different from the `compileClasspath()`, then the compilation dependencies should be used.

## Previous attempt

We did alternative tests to download directly via Coursier. We can fo this, but we need to select the correct binary compatible library. For 2.12 we add no extension, for 2.13 we add _2.13 and for 3 we add _3. This allows using libraries that are published for various versions of Scala. However using Coursier's `ResolutionParams` does **not** work. 

To see how Mill parsers and changes the library names see Mill's 

* `ZincWorkerAPI.Util.scalaBinaryVersion(scalaVersion: String)`
* `scalalib.Lib.depToDependency`
* `scalalib.Dep.toDependency`
* `scalalib.CoursierModule.resolveDeps`
on how Mill parsers and sets the correct library name using the expected 
conventions
https://get-coursier.io/docs/api#resolution-parameters
https://github.com/com-lihaoyi/mill/discussions/2045


```scala
  def mDocLibsTest = 
      T{
        import coursier._
        import coursier.params._

        val params = ResolutionParams()
                      // .withScalaVersion(ScalaVersion)
                      .withScalaVersion("3.1.3")    // Download ok with :: but gets version 2.13?
                      // .withScalaVersion("2.13.8") // Download ok with ::

        // For Scala 2 set explicitly Ok
        // val files = Fetch().addDependencies(dep"org.scalameta::mdoc:2.3.3").run()
        // For Scala 3 set explicitly Ok
        // val files = Fetch().addDependencies(dep"org.scalameta:mdoc_3:2.3.3").run()
        val files = Fetch()
                      .addDependencies(dep"org.scalameta::mdoc:2.3.3")  // uses the latest Scala 2 version
                      .withResolutionParams(params)
                      .run()

        val pathRefs = files.map(f => PathRef(os.Path(f)))
        printf(pathRefs.mkString(","))
        Agg(pathRefs : _*)
      }
```

