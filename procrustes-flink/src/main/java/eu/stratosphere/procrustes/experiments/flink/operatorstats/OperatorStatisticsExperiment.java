/*
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> c425d73... failed attempt to get something to work
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
<<<<<<< HEAD
=======
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
>>>>>>> c38c35a... adding experiment flink job
=======
>>>>>>> c425d73... failed attempt to get something to work
 */
package eu.stratosphere.procrustes.experiments.flink.operatorstats;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.accumulators.Accumulator;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.DiscardingOutputFormat;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.operatorstatistics.OperatorStatistics;
import org.apache.flink.contrib.operatorstatistics.OperatorStatisticsAccumulator;
import org.apache.flink.contrib.operatorstatistics.OperatorStatisticsConfig;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * OperatorStatistics Experiments job
 */
public class OperatorStatisticsExperiment {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorStatisticsExperiment.class);

    private static final String ACCUMULATOR_NAME = "op-stats";

    private static final String[] ALGORITHMS = {"none", "minmax", "lossy", "countmin", "hyperloglog", "linearc"};

    public static void main(String[] args) throws Exception {

        if (!parseParameters(args)) {
            return;
        }
        configOperatorStatistics();

        final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        env.readTextFile(inputPath).flatMap(new StringToInt()).output(new DiscardingOutputFormat<Tuple1<Integer>>());

        JobExecutionResult result = env.execute("Operator Stats");

        OperatorStatistics globalStats = result.getAccumulatorResult(ACCUMULATOR_NAME);
        LOG.info("\nGlobal Stats: " + globalStats.toString());

        Map<String, Object> accResults = result.getAllAccumulatorResults();

        ArrayList<OperatorStatistics> localStats = new ArrayList<OperatorStatistics>();

        for (String accumulatorName : accResults.keySet()) {
            if (accumulatorName.contains(ACCUMULATOR_NAME + "-")) {
                localStats.add((OperatorStatistics) accResults.get(accumulatorName));
            }
        }

    }

    // *************************************************************************
    // USER FUNCTIONS
    // *************************************************************************

    public static class StringToInt extends RichFlatMapFunction<String, Tuple1<Integer>> {

        Accumulator<Object, Serializable> globalAccumulator;

        Accumulator<Object, Serializable> localAccumulator;

        @Override
        public void open(Configuration parameters) {

            globalAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME);
            if (globalAccumulator == null) {
                getRuntimeContext().addAccumulator(ACCUMULATOR_NAME, new OperatorStatisticsAccumulator(opStatsConfig));
                globalAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME);
            }

            int subTaskIndex = getRuntimeContext().getIndexOfThisSubtask();
            localAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME + "-" + subTaskIndex);
            if (localAccumulator == null) {
                getRuntimeContext().addAccumulator(ACCUMULATOR_NAME + "-" + subTaskIndex, new OperatorStatisticsAccumulator(opStatsConfig));
                localAccumulator = getRuntimeContext().getAccumulator(ACCUMULATOR_NAME + "-" + subTaskIndex);
            }
        }

        @Override
        public void flatMap(String value, Collector<Tuple1<Integer>> out) throws Exception {
            int intValue;
            try {
                intValue = Integer.parseInt(value);
                localAccumulator.add(intValue);
                out.collect(new Tuple1(intValue));
            } catch (NumberFormatException ex) {}
        }

        @Override
        public void close() {
            globalAccumulator.merge(localAccumulator);
        }
    }

    // *************************************************************************
    // UTIL METHODS
    // *************************************************************************

    private static String inputPath;

    private static String algorithm;

    private static OperatorStatisticsConfig opStatsConfig;

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
            System.err.println("Usage: OperatorStatistics <input path> <result path> <algorithm>");
            LOG.error("Usage: OperatorStatistics <input path> <result path> <algorithm>");
            System.err.println("Algorithm options: none, minmax, lossy, countmin, hyperloglog, linearc");
            LOG.error("Algorithm options: none, minmax, lossy, countmin, hyperloglog, linearc");
            return false;
        }
    }

    private static void configOperatorStatistics() {
        opStatsConfig = new OperatorStatisticsConfig(false);
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
    }
}
