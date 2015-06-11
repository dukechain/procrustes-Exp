/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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

/**
 * OperatorStatistics Experiments job
 */
public class Processing {

    private static final Logger LOG = LoggerFactory.getLogger(Processing.class);

    private static final String ACCUMULATOR_NAME = "op-stats";

    private static final String[] ALGORITHMS = {"none", "minmax", "lossy", "countmin", "hyperloglog", "linearc"};

    public static void main(String[] args) throws Exception {

        if (!parseParameters(args)) {
            return;
        }

        final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        DataSet data =
                env.readTextFile(inputPath).flatMap(new CountInt(configOperatorStatistics())).groupBy(0).sum(1).filter(new CountGreaterThan(1000));
        // data.print();
        data.output(new DiscardingOutputFormat());

        JobExecutionResult result = env.execute("Processing Experiment");

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

        int boundary = 0;

        public CountGreaterThan(int b) {
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

    private static boolean parseParameters(String[] args) {

        // parse input arguments
        if (args.length == 2) {
            inputPath = args[0];
            algorithm = args[1];
            if (!Arrays.asList(ALGORITHMS).contains(algorithm)) {
                return false;
            } else {
                return true;
            }
        } else {
            LOG.error("Usage: OperatorStatistics <input path> <algorithm>");
            LOG.error("Algorithm options: none, minmax, lossy, countmin, hyperloglog, linearc");
            System.out.println("Usage: OperatorStatistics <input path> <algorithm>");
            System.out.println("Algorithm options: none, minmax, lossy, countmin, hyperloglog, linearc");
            return false;
        }
    }

    private static OperatorStatisticsConfig configOperatorStatistics() {

        OperatorStatisticsConfig opStatsConfig = new OperatorStatisticsConfig(false);
        System.out.println("Parsing configuration parameter");
        if (algorithm.equals("minmax")) {
            opStatsConfig.collectMin = true;
            opStatsConfig.collectMax = true;
        } else if (algorithm.equals("hyperloglog")) {
            opStatsConfig.collectCountDistinct = true;
            opStatsConfig.countDistinctAlgorithm = OperatorStatisticsConfig.CountDistinctAlgorithm.HYPERLOGLOG;
        } else if (algorithm.equals("linearc")) {
            opStatsConfig.collectCountDistinct = true;
            opStatsConfig.countDistinctAlgorithm = OperatorStatisticsConfig.CountDistinctAlgorithm.LINEAR_COUNTING;
        } else if (algorithm.equals("lossy")) {
            opStatsConfig.collectHeavyHitters = true;
            opStatsConfig.heavyHitterAlgorithm = OperatorStatisticsConfig.HeavyHitterAlgorithm.LOSSY_COUNTING;
        } else if (algorithm.equals("countmin")) {
            opStatsConfig.collectHeavyHitters = true;
            opStatsConfig.heavyHitterAlgorithm = OperatorStatisticsConfig.HeavyHitterAlgorithm.COUNT_MIN_SKETCH;
        }
        return opStatsConfig;
    }
}
