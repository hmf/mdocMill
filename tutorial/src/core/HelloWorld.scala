package core

// cSpell:ignore empty

/**
* Using Mill
* ./mill mill.scalalib.GenIdea/idea
*
* ./mill -i tutorial.run 
* ./mill -i tutorial.runMain core.HelloWorld
* ./mill -i --watch tutorial.runMain core.HelloWorld
* ./mill -i tutorial.assembly
* 
*/
object HelloWorld:

  def main(args: Array[String] ): Unit = 
    println("Hello world!")


end HelloWorld
