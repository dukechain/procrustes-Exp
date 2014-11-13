package eu.stratosphere.procrustes.datagen.spark

import eu.stratosphere.procrustes.datagen.DataGenerator
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}

object SparkDataGenerator {

  object Command {
    // argument names
    val KEY_MASTER = "master"
  }

  abstract class Command[A <: SparkDataGenerator](implicit m: scala.reflect.Manifest[A]) extends DataGenerator.Command {

    override def setup(parser: Subparser): Unit = {
      // add parameters
      parser.addArgument(s"--${Command.KEY_MASTER}")
        .`type`[String](classOf[String])
        .dest(Command.KEY_MASTER)
        .metavar("URL")
        .help("Spark master (default: local[*])")

      parser.setDefault(Command.KEY_MASTER, "local[*]")
    }
  }

}

abstract class SparkDataGenerator(val sparkMaster: String) extends DataGenerator {

  def this(ns: Namespace) = this(ns.get[String](SparkDataGenerator.Command.KEY_MASTER))
}

