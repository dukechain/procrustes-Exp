package eu.stratosphere.emma.experiments.spark;

import eu.stratosphere.emma.experiments.core.AppRunner;

public class App {

    public static void main(String[] args) {
        AppRunner runner = new AppRunner("eu.stratosphere.emma.experiments.spark", "emma-experiments-spark", "Spark");
        runner.run(args);
    }
}
