package eu.stratosphere.procrustes.experiments.operator.stats;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.accumulators.Accumulator;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.DiscardingOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.operatorstatistics.OperatorStatistics;
import org.apache.flink.contrib.operatorstatistics.OperatorStatisticsAccumulator;
import org.apache.flink.contrib.operatorstatistics.OperatorStatisticsConfig;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class Frequency {


    private static final Logger LOG = LoggerFactory.getLogger(Processing.class);

    private static final String ACCUMULATOR_NAME = "op-stats";

    private static final String[] ALGORITHMS = {"lossy", "countmin"};

    public static void main(String[] args) throws Exception {

        if (!parseParameters(args)) {
            return;
        }

        final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        DataSet<Tuple2<Integer, Integer>> heavyHitters =
                env.readTextFile(inputPath)
                   .flatMap(new CountInt(configOperatorStatistics()))
                   .groupBy(0)
                   .sum(1)
                   .filter(new CountGreaterThan(Math.round(cardinality * fraction)));

        heavyHitters.printToErr();
        // heavyHitters.writeAsCsv("/home/tamara/Desktop/out");

        JobExecutionResult result = env.execute("Heavy Hitters Experiment");

        OperatorStatistics globalStats = result.getAccumulatorResult(ACCUMULATOR_NAME);
        LOG.info("\nGlobal Stats: " + globalStats.toString());
        System.out.println("\nGlobal Stats: " + globalStats.toString());
    }

    // *************************************************************************
    // USER FUNCTIONS
    // *************************************************************************

    public static class CountInt extends RichFlatMapFunction<String, Tuple2<Integer, Integer>> {

        OperatorStatisticsConfig operatorStatisticsConfig;

        Accumulator<Object, Serializable> globalAccumulator;

        Accumulator<Object, Serializable> localAccumulator;

        public CountInt(OperatorStatisticsConfig config) {
            operatorStatisticsConfig = config;
        }

        @Override
        public void open(Configuration parameters) {

            globalAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME);
            if (globalAccumulator == null) {
                getRuntimeContext().addAccumulator(ACCUMULATOR_NAME, new OperatorStatisticsAccumulator(operatorStatisticsConfig));
                globalAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME);
            }

            int subTaskIndex = getRuntimeContext().getIndexOfThisSubtask();
            localAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME + "-" + subTaskIndex);
            if (localAccumulator == null) {
                getRuntimeContext().addAccumulator(ACCUMULATOR_NAME + "-" + subTaskIndex, new OperatorStatisticsAccumulator(operatorStatisticsConfig));
                localAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME + "-" + subTaskIndex);
            }
        }

        @Override
        public void flatMap(String value, Collector<Tuple2<Integer, Integer>> out) throws Exception {
            int intValue;
            try {
                intValue = Integer.parseInt(value);
                localAccumulator.add(intValue);
                out.collect(new Tuple2(intValue, 1));
            } catch (NumberFormatException ex) {}
        }

        @Override
        public void close() {
            globalAccumulator.merge(localAccumulator);
        }
    }

    public static class CountGreaterThan implements FilterFunction<Tuple2<Integer, Integer>> {

        long boundary = 0;

        public CountGreaterThan(long b) {
            boundary = b;
        }

        @Override
        public boolean filter(Tuple2<Integer, Integer> tuple) {
            return tuple.f1 > boundary;
        }
    }

    // *************************************************************************
    // UTIL METHODS
    // *************************************************************************

    private static String inputPath;

    private static String algorithm;

    private static long cardinality;

    private static int degreeOfParallelism;

    private static double error = -1;

    private static final int c = 10;

    private static double fraction;

    private static boolean parseParameters(String[] args) {

        if (args.length > 4 && args.length <= 5) {
            inputPath = args[0];
            algorithm = args[1];
            cardinality = Long.parseLong(args[2]);
            degreeOfParallelism = Integer.parseInt(args[3]);
            fraction = (double) 1 / (c * degreeOfParallelism);

            if (args.length == 5) {
                error = Double.parseDouble(args[4]);
                if (error < 0 || error > 1) {
                    return false;
                }
            }

            if (!Arrays.asList(ALGORITHMS).contains(algorithm)) {
                return false;
            } else {
                return true;
            }

        } else {
            LOG.error("Usage: OperatorStatistics <input path> <algorithm> <cardinality> <fraction>");
            LOG.error("Algorithm options: lossy, countmin");
            LOG.error("0 < fraction < 1");
            System.out.println("Usage: OperatorStatistics <input path> <algorithm> <cardinality> <fraction>");
            System.out.println("Algorithm options: lossy, countmin");
            System.out.println("0 < fraction < 1");
            return false;
        }
    }

    private static OperatorStatisticsConfig configOperatorStatistics() {

        OperatorStatisticsConfig opStatsConfig = new OperatorStatisticsConfig(false);
        System.out.println("Parsing configuration parameter");
        if (algorithm.equals("lossy")) {
            opStatsConfig.collectHeavyHitters = true;
            opStatsConfig.heavyHitterAlgorithm = OperatorStatisticsConfig.HeavyHitterAlgorithm.LOSSY_COUNTING;
        } else if (algorithm.equals("countmin")) {
            opStatsConfig.collectHeavyHitters = true;
            opStatsConfig.heavyHitterAlgorithm = OperatorStatisticsConfig.HeavyHitterAlgorithm.COUNT_MIN_SKETCH;
        }
        opStatsConfig.setHeavyHitterFraction(fraction);
        if (error != -1) {
            opStatsConfig.setHeavyHitterFraction(error);
        }
        return opStatsConfig;
    }
}
