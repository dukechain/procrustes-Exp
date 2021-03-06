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

import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.operators.base.JoinOperatorBase.JoinHint;
import org.apache.flink.api.java.aggregation.Aggregations;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFields;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFieldsFirst;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFieldsSecond;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.Collector;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.operators.IterativeDataSet;
import org.apache.flink.api.java.ExecutionEnvironment;

/**
 * An implementation of the connected components algorithm, using a delta iteration.
 * 
 * <p>
 * Initially, the algorithm assigns each vertex an unique ID. In each step, a vertex picks the
 * minimum of its own ID and its neighbors' IDs, as its new ID and tells its neighbors about its new
 * ID. After the algorithm has completed, all vertices in the same component will have the same ID.
 * 
 * <p>
 * A vertex whose component ID did not change needs not propagate its information in the next step.
 * Because of that, the algorithm is easily expressible via a delta iteration. We here model the
 * solution set as the vertices with their current component ids, and the workset as the changed
 * vertices. Because we see all vertices initially as changed, the initial workset and the initial
 * solution set are identical. Also, the delta to the solution set is consequently also the next
 * workset.<br>
 * 
 * <p>
 * Input files are plain text files and must be formatted as follows:
 * <ul>
 * <li>Vertices represented as IDs and separated by new-line characters.<br>
 * For example <code>"1\n2\n12\n42\n63\n"</code> gives five vertices (1), (2), (12), (42), and (63).
 * <li>Edges are represented as pairs for vertex IDs which are separated by space characters. Edges
 * are separated by new-line characters.<br>
 * For example <code>"1 2\n2 12\n1 12\n42 63\n"</code> gives four (undirected) edges (1)-(2),
 * (2)-(12), (1)-(12), and (42)-(63).
 * </ul>
 * 
 * <p>
 * Usage:
 * <code>ConnectedComponents &lt;vertices path&gt; &lt;edges path&gt; &lt;result path&gt; &lt;max number of iterations&gt;</code>
 * <br>
 * If no parameters are provided, the program is run with default data from
 * {@link ConnectedComponentsData} and 10 iterations.
 * 
 * <p>
 * This example shows how to use:
 * <ul>
 * <li>Delta Iterations
 * <li>Generic-typed Functions
 * </ul>
 */
@SuppressWarnings("serial")
public class FastTest implements ProgramDescription {

    // *************************************************************************
    // PROGRAM
    // *************************************************************************

    public static void main(String... args) throws Exception {

        if (!parseParameters(args)) {
            return;
        }

        // set up execution environment
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        // read vertex and edge data
        DataSet<Long> vertices = getVertexDataSet(env);
        DataSet<Tuple2<Long, Long>> edges = getEdgeDataSet(env).flatMap(new UndirectEdge());

        DataSet<Tuple3<Long, Long, Long>> edgesWithInitialId = edges.map(new MapFunction<Tuple2<Long, Long>, Tuple3<Long, Long, Long>>() {

            @Override
            public Tuple3<Long, Long, Long> map(Tuple2<Long, Long> value) throws Exception {
                return new Tuple3<Long, Long, Long>(value.f0, value.f1, value.f0);
            }
        });

        IterativeDataSet<Tuple3<Long, Long, Long>> iteration = edgesWithInitialId.iterate(maxIterations);
        DataSet<Tuple3<Long, Long, Long>> changes = iteration.map(new MapFunction<Tuple3<Long, Long, Long>, Tuple3<Long, Long, Long>>() {

            @Override
            public Tuple3<Long, Long, Long> map(Tuple3<Long, Long, Long> value) throws Exception {
                value.f2 += 1;
                return value;
            }
        });

        if (checkpointInterval > 0) {
            iteration.setCheckpointInterval(checkpointInterval);
        }

        // close the delta iteration (delta and new workset are identical)
        DataSet<Tuple3<Long, Long, Long>> result = iteration.closeWith(changes);

        // emit result
        if (fileOutput) {
            result.writeAsCsv(outputPath, "\n", " ");
        } else {
            result.print();
        }

        // execute program
        env.execute("Connected Components Example");
    }

    // *************************************************************************
    // USER FUNCTIONS
    // *************************************************************************

    /**
     * Function that turns a value into a 2-tuple where both fields are that value.
     */
    @ForwardedFields("*->f0")
    public static final class DuplicateValue<T> implements MapFunction<T, Tuple2<T, T>> {

        @Override
        public Tuple2<T, T> map(T vertex) {
            return new Tuple2<T, T>(vertex, vertex);
        }
    }

    /**
     * Undirected edges by emitting for each input edge the input edges itself and an inverted
     * version.
     */
    public static final class UndirectEdge implements FlatMapFunction<Tuple2<Long, Long>, Tuple2<Long, Long>> {

        Tuple2<Long, Long> invertedEdge = new Tuple2<Long, Long>();

        @Override
        public void flatMap(Tuple2<Long, Long> edge, Collector<Tuple2<Long, Long>> out) {
            invertedEdge.f0 = edge.f1;
            invertedEdge.f1 = edge.f0;
            out.collect(edge);
            out.collect(invertedEdge);
        }
    }

    /**
     * UDF that joins a (Vertex-ID, Component-ID) pair that represents the current component that a
     * vertex is associated with, with a (Source-Vertex-ID, Target-VertexID) edge. The function
     * produces a (Target-vertex-ID, Component-ID) pair.
     */
    @ForwardedFieldsFirst("f1->f1")
    @ForwardedFieldsSecond("f1->f0")
    public static final class NeighborWithComponentIDJoin implements JoinFunction<Tuple2<Long, Long>, Tuple2<Long, Long>, Tuple2<Long, Long>> {

        @Override
        public Tuple2<Long, Long> join(Tuple2<Long, Long> vertexWithComponent, Tuple2<Long, Long> edge) {
            return new Tuple2<Long, Long>(edge.f1, vertexWithComponent.f1);
        }
    }

    @Override
    public String getDescription() {
        return "Parameters: <vertices-path> <edges-path> <result-path> <max-number-of-iterations>";
    }

    // *************************************************************************
    // UTIL METHODS
    // *************************************************************************

    private static boolean fileOutput = false;

    private static String verticesPath = null;

    private static String edgesPath = null;

    private static String outputPath = null;

    private static int maxIterations = 10;

    private static int checkpointInterval = 0;

    private static boolean parseParameters(String[] programArguments) {

        if (programArguments.length > 0) {
            // parse input arguments
            fileOutput = true;
            if (programArguments.length == 4) {
                verticesPath = programArguments[0];
                edgesPath = programArguments[0];
                outputPath = programArguments[1];
                maxIterations = Integer.parseInt(programArguments[2]);
                checkpointInterval = Integer.parseInt(programArguments[3]);
            } else {
                System.err.println("Usage: ConnectedComponents <input path> <result path> <max number of iterations>");
                return false;
            }
        } else {
            System.out.println("Executing Connected Components example with default parameters and built-in default data.");
            System.out.println("  Provide parameters to read input data from files.");
            System.out.println("  See the documentation for the correct format of input files.");
            System.out.println("  Usage: ConnectedComponents <vertices path> <edges path> <result path> <max number of iterations>");
        }
        return true;
    }

    private static DataSet<Long> getVertexDataSet(ExecutionEnvironment env) {

        return env.readCsvFile(verticesPath)
                  .fieldDelimiter("\t")
                  .ignoreComments("#")
                  .ignoreInvalidLines()
                  .includeFields(true, false)
                  .types(Long.class)
                  .union(env.readCsvFile(verticesPath)
                            .fieldDelimiter("\t")
                            .ignoreComments("#")
                            .ignoreInvalidLines()
                            .includeFields(false, true)
                            .types(Long.class))
                  .distinct(0)
                  .map(new MapFunction<Tuple1<Long>, Long>() {

                      public Long map(Tuple1<Long> value) {
                          return value.f0;
                      }
                  });
    }

    private static DataSet<Tuple2<Long, Long>> getEdgeDataSet(ExecutionEnvironment env) {

        return env.readCsvFile(edgesPath).fieldDelimiter("\t").ignoreComments("#").ignoreInvalidLines().types(Long.class, Long.class);
    }


}
