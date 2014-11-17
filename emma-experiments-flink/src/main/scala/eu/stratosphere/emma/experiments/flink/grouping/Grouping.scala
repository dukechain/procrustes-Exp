package eu.stratosphere.emma.experiments.flink.grouping

import org.apache.flink.api.scala._
import org.apache.flink.core.fs.FileSystem

object Grouping {

  def run(variant: String, inputPath: String, outputPath: String) = {
    val env = ExecutionEnvironment.getExecutionEnvironment

    val data = env.readCsvFile[(Int, String, Int)](inputPath, fieldDelimiter = ',')
    val aggregates =
      if (variant == "reduce") {
        data.groupBy(_._1).reduce((a, b) => (a._1, a._2, Math.min(a._3, b._3)))
      } else {
        data.groupBy(_._1).reduceGroup(g => {
          g.minBy(t => t._3)
        })
      }
    aggregates.writeAsCsv(outputPath, "\n", ",", FileSystem.WriteMode.OVERWRITE)

    env.execute(s"Flink Grouping -- $variant")
  }

  def main(args: Array[String]) {
    assert(args.length == 3, "Arguments count != 3")
    assert(args(0) == "reduce" || args(0) == "groupReduce", "Invalid reduce-variant! (Variants: reduce, groupReduce), was: " + args(0))
    run(args(0), args(1), args(2))
  }
}

