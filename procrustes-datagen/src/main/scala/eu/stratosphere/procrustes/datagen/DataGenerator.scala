package eu.stratosphere.procrustes.datagen

import net.sourceforge.argparse4j.inf.{Namespace, Subparser}

object DataGenerator {

  abstract class Command[A <: DataGenerator](implicit val m: scala.reflect.Manifest[A]) {

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
    def instantiate(ns: Namespace): DataGenerator = {
      val constructor = m.runtimeClass.getConstructor(classOf[Namespace])
      constructor.newInstance(ns).asInstanceOf[DataGenerator]
    }
  }

}

abstract class DataGenerator {
  val SEED = 5431423142056L

  def run(): Unit
}

