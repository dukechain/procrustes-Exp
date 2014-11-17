package eu.stratosphere.emma.experiments.flink.kmeans

import org.apache.flink.api.common.functions._
import org.apache.flink.api.scala._
import org.apache.flink.configuration.Configuration

import scala.collection.JavaConverters._

object Kmeans {

  def run() = {

    val env = ExecutionEnvironment.getExecutionEnvironment

    val points: DataSet[Point] = getPointDataSet(env)
    val centroids: DataSet[Centroid] = getCentroidDataSet(env)

    val finalCentroids = centroids.iterate(numIterations) { currentCentroids =>
      val newCentroids = points
        .map(new SelectNearestCenter).withBroadcastSet(currentCentroids, "centroids")
        .map { x => (x._1, x._2, 1L) }
        .groupBy(0)
        .reduce { (p1, p2) => (p1._1, p1._2.add(p2._2), p1._3 + p2._3) }
        .map { x => new Centroid(x._1, x._2.div(x._3)) }
      newCentroids
    }

    val clusteredPoints: DataSet[(Int, Point)] =
      points.map(new SelectNearestCenter).withBroadcastSet(finalCentroids, "centroids")


    clusteredPoints.writeAsCsv(outputPath, "\n", ",")

    println("centroids: ")
    println(finalCentroids)

    env.execute("KMeans")
  }

  def main(args: Array[String]) {
    parseParameters(args)
    run()
  }

  private def parseParameters(programArguments: Array[String]) = {
      pointsPath = programArguments(0)
      centersPath = programArguments(1)
      outputPath = programArguments(2)
      numIterations = Integer.parseInt(programArguments(3))
  }

  private def getPointDataSet(env: ExecutionEnvironment): DataSet[Point] = {
    env.readCsvFile[(Int, Int, Double, Double, Double)](
      pointsPath,
      fieldDelimiter = ',')
      //includedFields = Array(2, 3, 4))
      .map { x => new Point(x._3, x._4, x._5)}
  }

  private def getCentroidDataSet(env: ExecutionEnvironment): DataSet[Centroid] = {
    env.readCsvFile[(Int, Int, Double, Double, Double)](
      centersPath,
      fieldDelimiter = ',')
      //includedFields = Array(0, 2, 3, 4))
      .map { x => new Centroid(x._1, x._3, x._4, x._5)}
  }

  private var pointsPath: String = null
  private var centersPath: String = null
  private var outputPath: String = null
  private var numIterations: Int = 10

  /**
   * A simple two-dimensional point.
   */
  class Point(var x: Double, var y: Double, var z: Double) extends Serializable {
    def this() {
      this(0, 0, 0)
    }

    def add(other: Point): Point = {
      x += other.x
      y += other.y
      z += other.z
      this
    }

    def div(other: Long): Point = {
      x /= other
      y /= other
      z /= other
      this
    }

    def euclideanDistance(other: Point): Double = {
      Math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z))
    }

    def clear(): Unit = {
      x = 0
      y = 0
      z = 0
    }

    override def toString: String = {
      x + " " + y + " " + z
    }
  }

  /**
   * A simple two-dimensional centroid, basically a point with an ID.
   */
  class Centroid(var id: Int, x: Double, y: Double, z: Double) extends Point(x, y, z) {
    def this() {
      this(0, 0, 0, 0)
    }

    def this(id: Int, p: Point) {
      this(id, p.x, p.y, p.z)
    }

    override def toString: String = {
      id + " " + super.toString
    }
  }

  /** Determines the closest cluster center for a data point. */
  final class SelectNearestCenter extends RichMapFunction[Point, (Int, Point)] {
    private var centroids: Traversable[Centroid] = null

    /** Reads the centroid values from a broadcast variable into a collection. */
    override def open(parameters: Configuration) {
      centroids = getRuntimeContext.getBroadcastVariable[Centroid]("centroids").asScala
    }

    def map(p: Point): (Int, Point) = {
      var minDistance: Double = Double.MaxValue
      var closestCentroidId: Int = -1
      for (centroid <- centroids) {
        val distance = p.euclideanDistance(centroid)
        if (distance < minDistance) {
          minDistance = distance
          closestCentroidId = centroid.id
        }
      }
      (closestCentroidId, p)
    }

  }
}

