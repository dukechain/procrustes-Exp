package eu.stratosphere.emma.experiments.spark

import eu.stratosphere.emma.experiments.core.{Algorithm=>CoreAlgorithm}
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}

object Algorithm {

  object Command {
    // argument names
    val KEY_MASTER = "master"
  }

  abstract class Command[A <: Algorithm](implicit m: scala.reflect.Manifest[A]) extends CoreAlgorithm.Command {

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

abstract class Algorithm(val sparkMaster: String) extends CoreAlgorithm {

  def this(ns: Namespace) = this(ns.get[String](Algorithm.Command.KEY_MASTER))
}

