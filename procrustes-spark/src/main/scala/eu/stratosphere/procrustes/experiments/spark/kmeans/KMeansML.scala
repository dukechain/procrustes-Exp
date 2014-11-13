package eu.stratosphere.procrustes.experiments.spark.kmeans

import eu.stratosphere.procrustes.experiments.spark.Algorithm
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.apache.spark.mllib.linalg.Vectors

object KMeansML {

  object Command {
    // argument names
    val KEY_K = "k"
    val KEY_MAXITER = "maxiter"
    val KEY_INPUT = "input"
    val KEY_INPUT_CENTERS = "centers"
    val KEY_OUTPUT = "output"
  }

  class Command extends Algorithm.Command[KMeansML] {
    // algorithm names
    override def name = "k-means-mllib"

    override def description = "Cluster points according to the K-Means algorithm"

    override def setup(parser: Subparser): Unit = {
      super.setup(parser)

      // add arguments
      parser.addArgument(Command.KEY_INPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_INPUT)
        .metavar("INPUT")
        .help("input data")
      parser.addArgument(Command.KEY_INPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_INPUT)
        .metavar("INPUT")
        .help("input data")
      parser.addArgument(Command.KEY_K)
        .`type`[Int](classOf[Int])
        .dest(Command.KEY_K)
        .metavar("K")
        .help("Number of clusters")
      parser.addArgument(Command.KEY_OUTPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_OUTPUT)
        .metavar("OUTPUT")
        .help("output file ")
      parser.addArgument(Command.KEY_MAXITER)
        .`type`[Int](classOf[Int])
        .dest(Command.KEY_MAXITER)
        .metavar("MAXITER")
        .help("maximum number of iterations")
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 6) {
      throw new RuntimeException("Arguments count != 6")
    }

    val K = args(0).toInt
    val maxIter = args(1).toInt
    val input = args(2).toString
    val centers = args(3).toString
    val output = args(4).toString
    val master = args(5).toString

    val generator = new KMeansML(K, maxIter, input, centers, output, master)
    generator.run()
  }
}

class KMeansML(val k: Int, val maxIterations: Int, val input: String, val centerInput: String, val output: String, master: String) extends Algorithm(master) {

  def this(ns: Namespace) = this(
    ns.get[Int](KMeansML.Command.KEY_K),
    ns.get[Int](KMeansML.Command.KEY_MAXITER),
    ns.get[String](KMeansML.Command.KEY_INPUT),
    ns.get[String](KMeansML.Command.KEY_INPUT_CENTERS),
    ns.get[String](KMeansML.Command.KEY_OUTPUT),
    ns.get[String](Algorithm.Command.KEY_MASTER))

  import org.apache.spark.{SparkConf, SparkContext}


  def run() = {
    val sc = new SparkContext(
      new SparkConf()
        .setAppName(new KMeansML.Command().name)
        .setMaster(master))

    val data = sc.textFile(input).map { line =>
      val l = line.split(',')
      eu.stratosphere.procrustes.experiments.spark.kmeans.ml.Vectors.dense(l.drop(1).map(_.toDouble))
    }

    val centers = sc.textFile(centerInput).map { line =>
      val l = line.split(',')
      eu.stratosphere.procrustes.experiments.spark.kmeans.ml.Vectors.dense(l.drop(1).map(_.toDouble))
    }.collect()

    val model = eu.stratosphere.procrustes.experiments.spark.kmeans.ml.KMeans.train(data, k, maxIterations, 1, centers)

    val points = model.predict(data)

    points.saveAsTextFile(output + "/points")
    sc.stop()
  }

}
