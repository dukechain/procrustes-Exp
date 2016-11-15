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
import java.util.Collection;

import org.apache.flink.api.common.functions.CrossFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFields;
import org.apache.flink.api.java.operators.IterativeDataSet;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.configuration.Configuration;

/**
 * This example implements a basic K-Means clustering algorithm.
 * 
 * <p>
 * K-Means is an iterative clustering algorithm and works as follows:<br>
 * K-Means is given a set of data points to be clustered and an initial set of <i>K</i> cluster
 * centers. In each iteration, the algorithm computes the distance of each data point to each
 * cluster center. Each point is assigned to the cluster center which is closest to it.
 * Subsequently, each cluster center is moved to the center (<i>mean</i>) of all points that have
 * been assigned to it. The moved cluster centers are fed into the next iteration. The algorithm
 * terminates after a fixed number of iterations (as in this implementation) or if cluster centers
 * do not (significantly) move in an iteration.<br>
 * This is the Wikipedia entry for the <a
 * href="http://en.wikipedia.org/wiki/K-means_clustering">K-Means Clustering algorithm</a>.
 * 
 * <p>
 * This implementation works on two-dimensional data points. <br>
 * It computes an assignment of data points to cluster centers, i.e., each data point is annotated
 * with the id of the final cluster (center) it belongs to.
 * 
 * <p>
 * Input files are plain text files and must be formatted as follows:
 * <ul>
 * <li>Data points are represented as two double values separated by a blank character. Data points
 * are separated by newline characters.<br>
 * For example <code>"1.2 2.3\n5.3 7.2\n"</code> gives two data points (x=1.2, y=2.3) and (x=5.3,
 * y=7.2).
 * <li>Cluster centers are represented by an integer id and a point value.<br>
 * For example <code>"1 6.2 3.2\n2 2.9 5.7\n"</code> gives two centers (id=1, x=6.2, y=3.2) and
 * (id=2, x=2.9, y=5.7).
 * </ul>
 * 
 * <p>
 * Usage:
 * <code>KMeans &lt;points path&gt; &lt;centers path&gt; &lt;result path&gt; &lt;num iterations&gt;</code>
 * <br>
 * If no parameters are provided, the program is run with default data from {@link KMeansData} and
 * 10 iterations.
 * 
 * <p>
 * This example shows how to use:
 * <ul>
 * <li>Bulk iterations
 * <li>Broadcast variables in bulk iterations
 * <li>Custom Java objects (PoJos)
 * </ul>
 */
@SuppressWarnings("serial")
public class KMeansWithoutBroadcastCrossHint {

    // *************************************************************************
    // PROGRAM
    // *************************************************************************

    public static void main(String[] args) throws Exception {

        if (!parseParameters(args)) {
            return;
        }

        // set up execution environment
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        // get input data
        DataSet<ClusterPoint> points = getPointDataSet(env);
        DataSet<ClusterPoint> centroids = getCentroidDataSet(env);

        // set number of bulk iterations for KMeans algorithm
        IterativeDataSet<ClusterPoint> loop = centroids.iterate(numIterations);

        if (checkpointInterval > 0) {
            loop.setCheckpointInterval(checkpointInterval);
        }

        DataSet<ClusterPoint> newCentroids =
        // <datapointId, Point, distance, clusterId>
                loop.crossWithHuge(points).with(new PointCenterCrossFunction())
                // group by the id of data point
                    .groupBy(0)
                    // choose the nearest centroid
                    .min(2)
                    // only <clusterId, Point>
                    .map(new ProjectionMap())
                    // <clusterId, Point, count>
                    .map(new CountAppender())
                    // group by the clusterId
                    .groupBy(0)
                    // <clusterId, Point+, count+>
                    .reduce(new CentroidAccumulator())
                    // compute new centroids from point counts and
                    // coordinate sums
                    .map(new CentroidAverager());

        // feed new centroids back into next iteration
        DataSet<ClusterPoint> finalCentroids = loop.closeWith(newCentroids);

        /*
         * DataSet<Tuple2<Integer, Point>> clusteredPoints = points // assign points to final
         * clusters .map(new SelectNearestCenter()).withBroadcastSet(finalCentroids, "centroids");
         */

        DataSet<Tuple2<Integer, Point>> clusteredPoints =
                finalCentroids.cross(points).with(new PointCenterCrossFunction()).groupBy(0).min(2).map(new ProjectionMap());


        // emit result
        if (fileOutput) {
            clusteredPoints.writeAsCsv(outputPath, "\n", " ");
        } else {
            clusteredPoints.print();
        }

        // execute program
        env.execute("KMeans Example");

    }

    // *************************************************************************
    // DATA TYPES
    // *************************************************************************

    /**
     * A simple three-dimensional point. (vector)
     */
    public static class Point implements Serializable {

        public double x, y, z;

        public Point() {}

        public Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Point add(Point other) {
            x += other.x;
            y += other.y;
            z += other.z;
            return this;
        }

        public Point div(long val) {
            x /= val;
            y /= val;
            z /= val;
            return this;
        }

        public double euclideanDistance(Point other) {
            return Math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z));
        }

        public void clear() {
            x = y = z = 0.0;
        }

        @Override
        public String toString() {
            return x + " " + y + " " + z;
        }
    }

    /**
     * A simple three-dimensional data point, basically a point with an ID.
     */
    public static class ClusterPoint extends Point {

        public int id;

        public ClusterPoint() {}

        public ClusterPoint(int id, double x, double y, double z) {
            super(x, y, z);
            this.id = id;
        }

        public ClusterPoint(int id, Point p) {
            super(p.x, p.y, p.z);
            this.id = id;
        }

        public double euclideanDistance(ClusterPoint centroid) {
            return super.euclideanDistance(new Point(centroid.x, centroid.y, centroid.z));
        }

        @Override
        public String toString() {
            return id + " " + super.toString();
        }
    }

    // *************************************************************************
    // USER FUNCTIONS
    // *************************************************************************

    @ForwardedFields("0->id; 1->x; 2->y; 3->z")
    public static final class TupleClusterPointConverter implements MapFunction<Tuple4<Integer, Double, Double, Double>, ClusterPoint> {

        @Override
        public ClusterPoint map(Tuple4<Integer, Double, Double, Double> t) throws Exception {
            return new ClusterPoint(t.f0, t.f1, t.f2, t.f3);
        }
    }

    /** Compute the distance between point and center. */
    @ForwardedFields("f0;f1")
    public static final class PointCenterCrossFunction implements CrossFunction<ClusterPoint, ClusterPoint, Tuple4<Integer, Point, Double, Integer>> {

        @Override
        public Tuple4<Integer, Point, Double, Integer> cross(ClusterPoint centroid, ClusterPoint datapoint) {

            double distance = datapoint.euclideanDistance(centroid);

            return new Tuple4<Integer, Point, Double, Integer>(datapoint.id, datapoint, distance, centroid.id);
        }
    }

    /** remove the distance. */
    @ForwardedFields("f0;f1")
    public static final class ProjectionMap implements MapFunction<Tuple4<Integer, Point, Double, Integer>, Tuple2<Integer, Point>> {

        @Override
        public Tuple2<Integer, Point> map(Tuple4<Integer, Point, Double, Integer> t) {
            return new Tuple2<Integer, Point>(t.f3, t.f1);
        }
    }

    /** Appends a count variable to the tuple. */
    @ForwardedFields("f0;f1")
    public static final class CountAppender implements MapFunction<Tuple2<Integer, Point>, Tuple3<Integer, Point, Long>> {

        @Override
        public Tuple3<Integer, Point, Long> map(Tuple2<Integer, Point> t) {
            return new Tuple3<Integer, Point, Long>(t.f0, t.f1, 1L);
        }
    }

    /** Sums and counts point coordinates. */
    @ForwardedFields("0")
    public static final class CentroidAccumulator implements ReduceFunction<Tuple3<Integer, Point, Long>> {

        @Override
        public Tuple3<Integer, Point, Long> reduce(Tuple3<Integer, Point, Long> val1, Tuple3<Integer, Point, Long> val2) {
            return new Tuple3<Integer, Point, Long>(val1.f0, val1.f1.add(val2.f1), val1.f2 + val2.f2);
        }
    }

    /** Computes new centroid from coordinate sum and count of points. */
    @ForwardedFields("0->id")
    public static final class CentroidAverager implements MapFunction<Tuple3<Integer, Point, Long>, ClusterPoint> {

        @Override
        public ClusterPoint map(Tuple3<Integer, Point, Long> value) {
            return new ClusterPoint(value.f0, value.f1.div(value.f2));
        }
    }

    // *************************************************************************
    // UTIL METHODS
    // *************************************************************************

    private static boolean fileOutput = false;

    private static String pointsPath = null;

    private static String centersPath = null;

    private static String outputPath = null;

    private static int numIterations = 10;

    private static int checkpointInterval = 0;

    private static boolean parseParameters(String[] programArguments) {

        if (programArguments.length > 0) {
            // parse input arguments
            fileOutput = true;
            if (programArguments.length == 5) {
                pointsPath = programArguments[0];
                centersPath = programArguments[1];
                outputPath = programArguments[2];
                numIterations = Integer.parseInt(programArguments[3]);
                checkpointInterval = Integer.parseInt(programArguments[4]);
            } else {
                System.err.println("Usage: KMeans <points path> <centers path> <result path> <num iterations>");
                return false;
            }
        } else {
            System.out.println("Executing K-Means example with default parameters and built-in default data.");
            System.out.println("  Provide parameters to read input data from files.");
            System.out.println("  See the documentation for the correct format of input files.");
            System.out.println("  We provide a data generator to create synthetic input files for this program.");
            System.out.println("  Usage: KMeans <points path> <centers path> <result path> <num iterations>");
        }
        return true;
    }

    private static DataSet<ClusterPoint> getPointDataSet(ExecutionEnvironment env) {
        // read points from CSV file
        return env.readCsvFile(pointsPath)
                  .fieldDelimiter(",")
                  .includeFields(true, false, true, true, true)
                  .types(Integer.class, Double.class, Double.class, Double.class)
                  .map(new TupleClusterPointConverter());
    }

    private static DataSet<ClusterPoint> getCentroidDataSet(ExecutionEnvironment env) {
        return env.readCsvFile(centersPath)
                  .fieldDelimiter(",")
                  .includeFields(true, false, true, true, true)
                  .types(Integer.class, Double.class, Double.class, Double.class)
                  .map(new TupleClusterPointConverter());
    }
}
