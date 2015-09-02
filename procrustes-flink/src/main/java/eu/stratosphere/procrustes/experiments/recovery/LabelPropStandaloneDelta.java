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

package eu.stratosphere.procrustes.experiments.recovery;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichCoGroupFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.operators.CoGroupOperator;
import org.apache.flink.api.java.operators.DeltaIteration;
import org.apache.flink.api.java.operators.IterativeDataSet;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.types.NullValue;
import org.apache.flink.util.Collector;

/**
 * This example uses the label propagation algorithm to detect communities by propagating labels.
 * Initially, each vertex is assigned its id as its label. The vertices iteratively propagate their
 * labels to their neighbors and adopt the most frequent label among their neighbors. The algorithm
 * converges when no vertex changes value or the maximum number of iterations have been reached.
 * 
 * The edges input file is expected to contain one edge per line, with long IDs in the following
 * format:"<sourceVertexID>\t<targetVertexID>".
 * 
 * The vertices input file is expected to contain one vertex per line, with long IDs and long vertex
 * values, in the following format:"<vertexID>\t<Long>".
 * 
 * If no arguments are provided, the example runs with a random graph of 100 vertices.
 */
public class LabelPropStandaloneDelta implements ProgramDescription {

    public static void main(String[] args) throws Exception {

        if (!parseParameters(args)) {
            return;
        }

        // Set up the execution environment
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().disableObjectReuse();

        // Set up the graph
        DataSet<Vertex<Long, Long>> vertices = getVertexDataSet(env);
        DataSet<Vertex<Long, Long>> vertices2 = getVertexDataSet(env);
        DataSet<Edge<Long, NullValue>> edges = getEdgeDataSet(env);

        DeltaIteration<Vertex<Long, Long>, Vertex<Long, Long>> iteration = vertices.iterateDelta(vertices2, maxIterations, new int[] {0});

        if (checkpointInterval > 0) {
            iteration.setCheckpointInterval(checkpointInterval);
        }

        // iteration.setCheckpointInterval(4);

        // build the messaging function (co group)
        CoGroupOperator<Edge<Long, NullValue>, Vertex<Long, Long>, Tuple2<Long, Long>> messages;
        MessagingUdfWithEdgeValues messenger = new MessagingUdfWithEdgeValues();
        messages = edges.coGroup(iteration.getWorkset()).where(0).equalTo(0).with(messenger);

        VertexUpdateUdf updateUdf = new VertexUpdateUdf();

        // build the update function (co group)
        CoGroupOperator<?, ?, Vertex<Long, Long>> updates = messages.coGroup(iteration.getSolutionSet()).where(0).equalTo(0).with(updateUdf);

        // configure coGroup update function with name and broadcast variables
        updates = updates.name("Vertex State Updates");

        iteration.closeWith(updates, updates).writeAsCsv(outputPath, "\n", ",");

        // Execute the program
        env.execute("Label Propagation Example");
    }

    // *************************************************************************
    // UTIL METHODS
    // *************************************************************************

    private static String vertexInputPath = null;

    private static String edgeInputPath = "file:/c:/workspace/dblp.txt";;

    private static String outputPath = "file:/c:/workspace/result.txt";

    private static int maxIterations = 10;

    private static int checkpointInterval = 0;

    private static boolean parseParameters(String[] args) {

        if (args.length > 0) {
            edgeInputPath = args[0];
            outputPath = args[1];
            maxIterations = Integer.parseInt(args[2]);
            vertexInputPath = args[0];
            checkpointInterval = Integer.parseInt(args[3]);
        } else {
            System.out.println("Executing LabelPropagation example with default parameters and built-in default data.");
            System.out.println("  Provide parameters to read input data from files.");
            System.out.println("  See the documentation for the correct format of input files.");
            System.out.println("  Usage: LabelPropagation <input path> <output path> <num iterations>");
        }
        return true;
    }


    private static DataSet<Vertex<Long, Long>> getVertexDataSet(ExecutionEnvironment env) {

        return env.readCsvFile(vertexInputPath)
                  .fieldDelimiter("\t")
                  .ignoreComments("#")
                  .includeFields(true, false)
                  .types(Long.class)
                  .union(env.readCsvFile(vertexInputPath).fieldDelimiter("\t").ignoreComments("#").includeFields(false, true).types(Long.class))
                  .distinct(0)
                  .map(new MapFunction<Tuple1<Long>, Vertex<Long, Long>>() {

                      public Vertex<Long, Long> map(Tuple1<Long> value) {
                          return new Vertex<Long, Long>(value.f0, value.f0);
                      }
                  });
    }

    @SuppressWarnings("serial")
    private static DataSet<Edge<Long, NullValue>> getEdgeDataSet(ExecutionEnvironment env) {

        return env.readCsvFile(edgeInputPath)
                  .fieldDelimiter("\t")
                  .ignoreComments("#")
                  .types(Long.class, Long.class)
                  .map(new MapFunction<Tuple2<Long, Long>, Edge<Long, NullValue>>() {

                      @Override
                      public Edge<Long, NullValue> map(Tuple2<Long, Long> value) throws Exception {
                          return new Edge<Long, NullValue>(value.f0, value.f1, NullValue.getInstance());
                      }
                  });
    }

    @Override
    public String getDescription() {
        return "Label Propagation Example";
    }


    // classes
    private static final class MessagingUdfWithEdgeValues extends RichCoGroupFunction<Edge<Long, NullValue>, Vertex<Long, Long>, Tuple2<Long, Long>> {

        private static final long serialVersionUID = 1L;

        @Override
        public void coGroup(Iterable<Edge<Long, NullValue>> edges, Iterable<Vertex<Long, Long>> state, Collector<Tuple2<Long, Long>> out) throws Exception {
            final Iterator<Vertex<Long, Long>> stateIter = state.iterator();
            final Iterator<Edge<Long, NullValue>> edgeIter = edges.iterator();

            if (stateIter.hasNext()) {
                Vertex<Long, Long> newVertexState = stateIter.next();

                while (edgeIter.hasNext()) {
                    Edge<Long, NullValue> next = (Edge<Long, NullValue>) edgeIter.next();
                    Tuple2<Long, Long> message = new Tuple2<Long, Long>();
                    Long k = next.getTarget();
                    message.setField(k, 0);
                    message.setField(newVertexState.f1, 1);
                    out.collect(message);
                }
            }
        }
    }

    private static final class VertexUpdateUdf extends RichCoGroupFunction<Tuple2<Long, Long>, Vertex<Long, Long>, Vertex<Long, Long>> {

        private static final long serialVersionUID = 1L;


        @Override
        public void coGroup(Iterable<Tuple2<Long, Long>> messages, Iterable<Vertex<Long, Long>> vertex, Collector<Vertex<Long, Long>> out) throws Exception {
            final Iterator<Vertex<Long, Long>> vertexIter = vertex.iterator();

            if (vertexIter.hasNext()) {
                Vertex<Long, Long> vertexState = vertexIter.next();

                // System.out.println("VU " + vertexState);

                Iterator<Tuple2<Long, Long>> messagesIter = messages.iterator();

                Map<Long, Long> labelsWithFrequencies = new HashMap<Long, Long>();

                long maxFrequency = 1;
                long mostFrequentLabel = vertexState.f1;

                // store the labels with their frequencies
                while (messagesIter.hasNext()) {
                    Tuple2<Long, Long> msg = messagesIter.next();
                    if (labelsWithFrequencies.containsKey(msg.f1)) {
                        long currentFreq = labelsWithFrequencies.get(msg.f1);
                        labelsWithFrequencies.put(msg.f1, currentFreq + 1);
                    } else {
                        labelsWithFrequencies.put(msg.f1, 1L);
                    }
                }
                // select the most frequent label: if two or more labels have the
                // same frequency,
                // the node adopts the label with the highest value
                for (Entry<Long, Long> entry : labelsWithFrequencies.entrySet()) {
                    if (entry.getValue() == maxFrequency) {
                        // check the label value to break ties
                        if (entry.getKey() < mostFrequentLabel) {
                            mostFrequentLabel = entry.getKey();
                        }
                    } else if (entry.getValue() > maxFrequency) {
                        maxFrequency = entry.getValue();
                        mostFrequentLabel = entry.getKey();
                    }
                }

                vertexState.f1 = mostFrequentLabel;
                out.collect(vertexState);
            } else {
                final Iterator<Tuple2<Long, Long>> messageIter = messages.iterator();
                if (messageIter.hasNext()) {
                    String message = "Target vertex does not exist!.";
                    try {
                        Tuple2<Long, Long> next = messageIter.next();
                        message = "Target vertex '" + next.f0 + "' does not exist!.";
                    } catch (Throwable t) {}
                    // throw new Exception(message);
                    System.out.println(message);
                } else {
                    throw new Exception();
                }
            }
        }
    }

    public static class Edge<K extends Comparable<K> & Serializable, V extends Serializable> extends Tuple3<K, K, V> {

        private static final long serialVersionUID = 1L;

        public Edge() {}

        public Edge(K src, K trg, V val) {
            this.f0 = src;
            this.f1 = trg;
            this.f2 = val;
        }

        /**
         * Reverses the direction of this Edge.
         * 
         * @return a new Edge, where the source is the original Edge's target and the target is the
         *         original Edge's source.
         */
        public Edge<K, V> reverse() {
            return new Edge<K, V>(this.f1, this.f0, this.f2);
        }

        public void setSource(K src) {
            this.f0 = src;
        }

        public K getSource() {
            return this.f0;
        }

        public void setTarget(K target) {
            this.f1 = target;
        }

        public K getTarget() {
            return f1;
        }

        public void setValue(V value) {
            this.f2 = value;
        }

        public V getValue() {
            return f2;
        }
    }

    public static class Vertex<K extends Comparable<K> & Serializable, V extends Serializable> extends Tuple2<K, V> {

        private static final long serialVersionUID = 1L;

        public Vertex() {}

        public Vertex(K k, V val) {
            this.f0 = k;
            this.f1 = val;
        }

        public K getId() {
            return this.f0;
        }

        public V getValue() {
            return this.f1;
        }

        public void setId(K id) {
            this.f0 = id;
        }

        public void setValue(V val) {
            this.f1 = val;
        }
    }
}
