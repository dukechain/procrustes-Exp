package eu.stratosphere.emma.experiments.spark.kmeans

import eu.stratosphere.emma.experiments.spark.Algorithm
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.apache.spark.util.Vector

object KMeans {

  // constnats
  val SEED = 5431423142056L
  val EPSILON = 1e-4

  /** find closest center */
  def closestPoint(p: Vector, centers: Array[(Int, Vector)]): Int = {
    val dists = centers.map(c => (c._1, p.squaredDist(c._2))) // (id, dist)
    var min = Double.PositiveInfinity
    var closest = Int.MaxValue
    for (d <- dists) {
      if (d._2 < min) {
        min = d._2
        closest = d._1
      }
    }
    closest
  }

  object Command {
    // argument names
    val KEY_K = "k"
    val KEY_MAXITER = "maxiter"
    val KEY_INPUT = "input"
    val KEY_OUTPUT = "output"
  }

  class Command extends Algorithm.Command[KMeans]() {

    // algorithm names
    override def name = "k-means"

    override def description = "Cluster points according to the K-Means algorithm"

    override def setup(parser: Subparser): Unit = {
      super.setup(parser)

      // add arguments
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
      parser.addArgument(Command.KEY_MAXITER)
        .`type`[Int](classOf[Int])
        .dest(Command.KEY_MAXITER)
        .metavar("MAXITER")
        .help("maximum number of iterations")
      parser.addArgument(Command.KEY_OUTPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_OUTPUT)
        .metavar("OUTPUT")
        .help("output file ")
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 5) {
      throw new RuntimeException("Arguments count != 5")
    }

    val K = args(0).toInt
    val maxIter = args(1).toInt
    val input = args(2).toString
    val output = args(3).toString
    val master = args(4).toString

    val generator = new KMeans(K, maxIter, input, output, master)
    generator.run()
  }
}

class KMeans(val K: Int, val maxIter: Int, val input: String, val output: String, val master: String) extends Algorithm(master) {

  def this(ns: Namespace) = this(
    ns.get[Int](KMeans.Command.KEY_K),
    ns.get[Int](KMeans.Command.KEY_MAXITER),
    ns.get[String](KMeans.Command.KEY_INPUT),
    ns.get[String](KMeans.Command.KEY_OUTPUT),
    ns.get[String](Algorithm.Command.KEY_MASTER))

  import org.apache.spark.{SparkConf, SparkContext}
  import org.apache.spark.SparkContext._

  def run() = {
    val sc = new SparkContext(
      new SparkConf()
        .setAppName(new KMeans.Command().name)
        .setMaster(master))

    var tmpDist = Double.PositiveInfinity
    var iter = 0

    val data = sc.textFile(input).map { line =>
      val l = line.split(',')
      (l(0).toInt, Vector(l.drop(1).map(_.toDouble)))
    }
    data.cache() // cache points

    // initialize centroids
    var oldCentroids = sc.broadcast(data.takeSample(withReplacement = false, num = K).map(c => c._2).zipWithIndex.map(c => (c._2, c._1)))
    var newCentroids = oldCentroids
    // initialize the closest cluster for every point
    var closest = data.map(p => (KMeans.closestPoint(p._2, newCentroids.value), p._2)).cache()

    // MAIN LOOP
    while (tmpDist > KMeans.EPSILON && iter < maxIter) {
      // compute new cluster centers
      newCentroids = sc.broadcast(closest
        .mapValues(x => (x, 1))
        .reduceByKey((x, y) => (x._1 + y._1, x._2 + y._2))
        .map(p => (p._1, p._2._1 / p._2._2))
        .collect())

      // compute change between old and new clusters
      tmpDist = newCentroids.value.map(_._2).zip(oldCentroids.value.map(_._2))
        .map(ctr => ctr._1.squaredDist(ctr._2)) // compute pairwise dist between old and new
        .foldLeft(0.0)((a, b) => a + b) // compute sum over all


      // reassign points to cluster
      closest = data.map(p => (KMeans.closestPoint(p._2, newCentroids.value), p._2)).cache()

      // make new centroids the old centroids for the next iteration
      oldCentroids = newCentroids
      newCentroids.unpersist()

      iter += 1
      println(s"Finished iteration $iter (delta = $tmpDist)")
    }

    closest.saveAsTextFile(output + "/points")
    sc.parallelize(oldCentroids.value).saveAsTextFile(output + "/clusters")
    sc.stop()
  }
}
