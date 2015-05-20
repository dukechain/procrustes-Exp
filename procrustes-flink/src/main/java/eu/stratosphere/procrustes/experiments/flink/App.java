package eu.stratosphere.procrustes.experiments.flink;

import eu.stratosphere.procrustes.experiments.core.AppRunner;

public class App {

    public static void main(String[] args) {
        AppRunner runner = new AppRunner("eu.stratosphere.procrustes.experiments.flink", "flink", "Flink");
        runner.run(args);
    }
}
