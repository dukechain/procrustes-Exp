package eu.stratosphere.emma.experiments.core

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import scala.io.Source

trait ClusterTest {

  val tmpFolder = new TemporaryFolder()

  @Rule def tmpFolderDef = tmpFolder

  def compareCenters(expCentersPath: String, cmpCentersPath: String): Double = {

    val OC = originalCenters(expCentersPath)
    val NC = newCenters(cmpCentersPath)

    if (NC.length != OC.length)
      throw new IllegalArgumentException(s"Unequal number of cluster centers! (original: ${OC.length}, new: ${NC.length})")

    (for (nc <- NC) yield OC.map(oc => euclideanDist(oc, nc)).min).fold(0.0)((a, b) => a + b)
  }

  def euclideanDist(v1: Vector[Double], v2: Vector[Double]): Double = {
    if (v1.length != v2.length)
      throw new IllegalArgumentException("Vectors must have the same dimension!")

    val dist = v1.zip(v2)
      .map(x => (x._1 - x._2) * (x._1 - x._2))
      .foldLeft(0.0)((a, b) => a + b)
    Math.sqrt(dist)
  }

  def newCenters(path: String): Array[Vector[Double]]

  def originalCenters(path: String) = {
    val vectors = for (line <- Source.fromFile(path).getLines()) yield line.split(",").map(_.toDouble).toVector
    vectors.toArray.sortBy(v => v(0)).map(v => v.slice(1, v.length))
  }

}
