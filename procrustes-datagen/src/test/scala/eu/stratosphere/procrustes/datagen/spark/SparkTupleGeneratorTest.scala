package eu.stratosphere.procrustes.datagen.spark

import eu.stratosphere.procrustes.datagen.util.Distributions._
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class SparkTupleGeneratorTest extends AssertionsForJUnit {

  @Test def integrationTest() {
    val numTasks = 4
    val tuplesPerTask = 2500
    val payload = 5
    val keyDist = Pareto(1)
    val aggDist = Uniform(20)
    // master with given numTasks
    val master = s"local[$numTasks]"
    // input and output path
    val input = getClass.getResource("/clusterCenters.csv")
    val output = s"${System.getProperty("java.io.tmpdir")}/data/tupleGeneratorOutput"

    new SparkTupleGenerator(master, numTasks, tuplesPerTask, keyDist, payload, aggDist, output)

  }

}
