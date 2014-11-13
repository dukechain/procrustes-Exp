package eu.stratosphere.procrustes.experiments.spark;

import eu.stratosphere.procrustes.experiments.core.AppRunner;

public class App {

    public static void main(String[] args) {
        AppRunner runner = new AppRunner("eu.stratosphere.procrustes.experiments.spark", "spark", "Spark");
        runner.run(args);
    }
}
