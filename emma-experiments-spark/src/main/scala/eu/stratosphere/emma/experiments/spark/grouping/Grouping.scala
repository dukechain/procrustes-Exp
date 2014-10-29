package eu.stratosphere.emma.experiments.spark.grouping

import eu.stratosphere.emma.experiments.spark.Algorithm
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}

object Grouping {

  object Command {
    // argument names
    val KEY_INPUT = "input"
    val KEY_OUTPUT = "output"
  }

  class Command extends Algorithm.Command[Grouping]() {

    // algorithm names
    override def name = "grouping"

    override def description = "Sums up the groups"

    override def setup(parser: Subparser): Unit = {
      super.setup(parser)

      // add arguments
      parser.addArgument(Command.KEY_INPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_INPUT)
        .metavar("INPUT")
        .help("input data")
      parser.addArgument(Command.KEY_OUTPUT)
        .`type`[String](classOf[String])
        .dest(Command.KEY_OUTPUT)
        .metavar("OUTPUT")
        .help("output file ")
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      throw new RuntimeException("Arguments count != 5")
    }
    val input = args(0).toString
    val output = args(1).toString
    val master = args(2).toString

    val generator = new Grouping(input, output, master)
    generator.run()
  }
}

class Grouping(val input: String, val output: String, val master: String) extends Algorithm(master) {

  def this(ns: Namespace) = this(
    ns.get[String](Grouping.Command.KEY_INPUT),
    ns.get[String](Grouping.Command.KEY_OUTPUT),
    ns.get[String](Algorithm.Command.KEY_MASTER))

  import org.apache.spark.SparkContext._
  import org.apache.spark.{SparkConf, SparkContext}

  def run() = {
    val sc = new SparkContext(
      new SparkConf()
        .setAppName(new Grouping.Command().name)
        .setMaster(master))

    val data = sc.textFile(input).map { line =>
      val l = line.split(',')
      (l(0).toInt, (l(1), l(2).toInt))
    }

    val aggregates = data.reduceByKey((a, b) => (a._1, a._2 + b._2))

    aggregates.saveAsTextFile(output + "/groups")
    sc.stop()
  }
}
