package eu.stratosphere.procrustes.datagen.spark

import eu.stratosphere.procrustes.datagen.util.Distributions._
import org.junit.Test


class SparkIntGeneratorTest {

  @Test def integrationTest() {
    val numTasks = 4
    val tuplesPerTask = 2500
    val payload = 5
    //val keyDist = Pareto(1)
    val master = s"local[$numTasks]"
    val output = s"/tmp/input/intGeneratorOutput"

//    val gen = new SparkIntGenerator(master, numTasks, tuplesPerTask, payload, output)
    val gen = new SparkIntGenerator(master, numTasks, tuplesPerTask, SparkIntGenerator.parseDist("Uniform[100]"), payload, output)
    gen.run()
  }
}
