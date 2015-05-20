package eu.stratosphere.procrustes.datagen.spark

import eu.stratosphere.procrustes.datagen.util.Distributions._
import eu.stratosphere.procrustes.datagen.util.RanHash
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.Random

object SparkIntGenerator {

  object Patterns {
    val Uniform = "\\bUniform\\(\\d\\)".r
    val Gaussian = "\\bGaussian\\(\\d,\\d\\)".r
    val Pareto = "\\bPareto\\(\\d\\)".r
  }
  object Command {
    // argument names
    val KEY_N = "N"
    val KEY_DOP = "dop"
    val KEY_OUTPUT = "output"
    //val KEY_KEYDIST = "key-distribution"
  }

  class Command extends SparkDataGenerator.Command[SparkIntGenerator]() {

    override def name = "IntGenerator"
    override def description = "Generate a dataset that consists of Ints"

    override def setup(parser: Subparser): Unit = {
      super.setup(parser)

      // add arguments

      parser.addArgument(Command.KEY_DOP)
        .`type`[Int](classOf[Int])
        .dest(Command.KEY_DOP)
        .metavar("DOP")
        .help("degree of parallelism")
      parser.addArgument(Command.KEY_N)
        .`type`[Int](classOf[Int])
        .dest(Command.KEY_N)
        .metavar("N")
        .help("number of points to generate")
      parser.addArgument(Command.KEY_OUTPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_OUTPUT)
        .metavar("OUTPUT")
        .help("output file ")
      /*parser.addArgument(Command.KEY_KEYDIST)
        .`type`[String](classOf[String])
        .dest(Command.KEY_KEYDIST)
        .metavar("DISTRIBUTION")
        .help("distribution to use for the keys")*/
    }
  }

  // --------------------------------------------------------------------------------------------
  // ----------------------------------- Schema -------------------------------------------------
  // --------------------------------------------------------------------------------------------

  def main(args: Array[String]): Unit = {
    if (args.length != 5) {
      throw new RuntimeException("Arguments count != 4")
    }

    val master: String = args(0)
    val numTasks: Int = args(1).toInt
    val tuplesPerTask: Int = args(2).toInt
    val output: String = args(3)

    val generator = new SparkIntGenerator(master, numTasks, tuplesPerTask, output)
    generator.run()
  }

  def parseDist(s: String): Distribution = s match {
    case Patterns.Pareto(a) => Pareto(a.toDouble)
    case Patterns.Gaussian(a,b) => Gaussian(a.toDouble, b.toDouble)
    case Patterns.Uniform(a) => Uniform(a.toInt)
    case _ => Uniform(10)
  }
}

class SparkIntGenerator(master: String, numTasks: Int, tuplesPerTask: Int, output: String) extends SparkDataGenerator(master) {


  def this(ns: Namespace) = this(
    ns.get[String](SparkDataGenerator.Command.KEY_MASTER),
    ns.get[Int](SparkIntGenerator.Command.KEY_DOP),
    ns.get[Int](SparkIntGenerator.Command.KEY_N),
    //SparkIntGenerator.parseDist(ns.get[String](SparkIntGenerator.Command.KEY_KEYDIST)),
    ns.get[String](SparkIntGenerator.Command.KEY_OUTPUT))

  def run() = {
    val conf = new SparkConf().setAppName(new SparkIntGenerator.Command().name).setMaster(master)
    val sc = new SparkContext(conf)

    val n = tuplesPerTask
    val seed = this.SEED

    val kd = Pareto(1)//Fix the distribution

    val dataset = sc.parallelize(0 until numTasks, numTasks).flatMap(i => {
      val partitionStart = n * i // the index of the first point in the current partition
      val randStart = partitionStart
      val rand = new RanHash(seed)
      rand.skipTo(seed + randStart)

      for (j <- partitionStart until (partitionStart + n)) yield {
        kd.sample(rand).toInt
      }
    })

    dataset.saveAsTextFile(output)
    sc.stop()
  }
}
