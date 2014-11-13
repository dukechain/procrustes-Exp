package eu.stratosphere.procrustes.datagen.spark

import eu.stratosphere.procrustes.datagen.util.Distributions._
import eu.stratosphere.procrustes.datagen.util.RanHash
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.Random

object SparkTupleGenerator {

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
    val KEY_KEYDIST = "key-distribution"
    val KEY_AGGDIST = "aggregate-distribution"
    val KEY_PAYLOAD = "payload"
  }

  class Command extends SparkDataGenerator.Command[SparkTupleGenerator]() {

    // algorithm names
    override def name = "TupleGenerator"

    override def description = "Generate a dataset that consists of Tuples (K, V, A)"

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
      parser.addArgument(Command.KEY_PAYLOAD)
        .`type`[Int](classOf[Int])
        .dest(Command.KEY_PAYLOAD)
        .metavar("PAYLOAD")
        .help("length of the string value")
      parser.addArgument(Command.KEY_KEYDIST)
        .`type`[String](classOf[String])
        .dest(Command.KEY_KEYDIST)
        .metavar("DISTRIBUTION")
        .help("distribution to use for the keys")
      parser.addArgument(Command.KEY_AGGDIST)
        .`type`[String](classOf[String])
        .dest(Command.KEY_AGGDIST)
        .metavar("DISTRIBUTION")
        .help("distribution to use for the keys")
    }
  }

  // --------------------------------------------------------------------------------------------
  // ----------------------------------- Schema -------------------------------------------------
  // --------------------------------------------------------------------------------------------

  object Schema {

    case class KV(key: Int, value: String, aggregation: Int) {
      override def toString = s"$key,$value,$aggregation"
    }

  }

  def main(args: Array[String]): Unit = {
    if (args.length != 7) {
      throw new RuntimeException("Arguments count != 7")
    }

    val master: String = args(0)
    val numTasks: Int = args(1).toInt
    val tuplesPerTask: Int = args(2).toInt
    val keyDist: Distribution = parseDist(args(3))
    val pay: Int = args(4).toInt
    val aggDist: Distribution = parseDist(args(5))
    val output: String = args(6)

    val generator = new SparkTupleGenerator(master, numTasks, tuplesPerTask, keyDist, pay, aggDist, output)
    generator.run()
  }

  def parseDist(s: String): Distribution = s match {
    case Patterns.Pareto(a) => Pareto(a.toDouble)
    case Patterns.Gaussian(a,b) => Gaussian(a.toDouble, b.toDouble)
    case Patterns.Uniform(a) => Uniform(a.toInt)
    case _ => Uniform(10)
  }
}

class SparkTupleGenerator(master: String, numTasks: Int, tuplesPerTask: Int, keyDist: Distribution, pay: Int, aggDist: Distribution, output: String) extends SparkDataGenerator(master) {

  import eu.stratosphere.procrustes.datagen.spark.SparkTupleGenerator.Schema.KV

  def this(ns: Namespace) = this(
    ns.get[String](SparkDataGenerator.Command.KEY_MASTER),
    ns.get[Int](SparkTupleGenerator.Command.KEY_DOP),
    ns.get[Int](SparkTupleGenerator.Command.KEY_N),
    SparkTupleGenerator.parseDist(ns.get[String](SparkTupleGenerator.Command.KEY_KEYDIST)),
    ns.get[Int](SparkTupleGenerator.Command.KEY_PAYLOAD),
    SparkTupleGenerator.parseDist(ns.get[String](SparkTupleGenerator.Command.KEY_AGGDIST)),
    ns.get[String](SparkTupleGenerator.Command.KEY_OUTPUT))

  def run() = {
    val conf = new SparkConf().setAppName(new SparkTupleGenerator.Command().name).setMaster(master)
    val sc = new SparkContext(conf)

    val n = tuplesPerTask
    val N = tuplesPerTask * numTasks // number of points generated in total
    val s = new Random(SEED).nextString(pay)
    val seed = this.SEED

    val kd = this.keyDist
    val ag = this.aggDist

    val dataset = sc.parallelize(0 until numTasks, numTasks).flatMap(i => {
      val partitionStart = n * i // the index of the first point in the current partition
      val randStart = partitionStart * 2 // the start for the prng (times 2 because we need 2 numbers for each tuple)
      val rand = new RanHash(seed)
      rand.skipTo(seed + randStart)

      for (j <- partitionStart until (partitionStart + n)) yield {
        KV(kd.sample(rand).toInt, s, ag.sample(rand).toInt)
      }
    })

    dataset.saveAsTextFile(output)
    sc.stop()
  }
}
