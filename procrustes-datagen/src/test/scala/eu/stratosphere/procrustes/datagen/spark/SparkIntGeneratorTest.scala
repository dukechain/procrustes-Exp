package eu.stratosphere.procrustes.datagen.spark

import eu.stratosphere.procrustes.datagen.util.Distributions._
import org.junit.Test


class SparkIntGeneratorTest {

  @Test def integrationTest() {
    val numTasks = 4
    val tuplesPerTask = 2500
    val keyDist = Pareto(1)
    // master with given numTasks
    val master = s"local[$numTasks]"
    // input and output path
    val output = s"/tmp/input/tupleGeneratorOutput"

    val gen = new SparkIntGenerator(master, numTasks, tuplesPerTask, output)
    gen.run()
  }
}
