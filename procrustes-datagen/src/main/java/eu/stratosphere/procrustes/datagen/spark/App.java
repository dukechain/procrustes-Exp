package eu.stratosphere.procrustes.datagen.spark;

import eu.stratosphere.procrustes.datagen.AppRunner;

public class App {

    public static void main(String[] args) {
        AppRunner runner = new AppRunner("eu.stratosphere.procrustes.datagen.spark", "procrustes-datagen-spark", "Spark");
        runner.run(args);
    }
}
