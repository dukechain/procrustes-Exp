package eu.stratosphere.emma.experiments.core

import net.sourceforge.argparse4j.inf.{Namespace, Subparser}

object Algorithm {

  abstract class Command[A <: Algorithm](implicit val m: scala.reflect.Manifest[A]) {

    /**
     * Algorithm key.
     */
    def name: String

    /**
     * Algorithm name.
     */
    def description: String

    /**
     * Algorithm subparser configuration.
     *
     * @param parser The subparser for this algorithm.
     */
    def setup(parser: Subparser): Unit

    /**
     * Create an instance of the algorithm.
     *
     * @param ns The parsed arguments to be passed to the algorithm constructor.
     * @return
     */
    def instantiate(ns: Namespace): Algorithm = {
      val constructor = m.runtimeClass.getConstructor(classOf[Namespace])
      constructor.newInstance(ns).asInstanceOf[Algorithm]
    }
  }

}

abstract class Algorithm {

  def run(): Unit
}

