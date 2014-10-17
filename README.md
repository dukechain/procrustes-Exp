# Emma experiments

Basic instructions for configuring and running the set of experiments with Peel.

## Setup

You need to configura a *peel* distribution. Check out the *peel* sources and do:

```bash
# create binary package
mvn package -DskipTests
# move binary package into a separate folder
cp peel-dist/target/peel-dist-1.0-SNAPSHOT-bin $PEEL_INSTALL_DIR
```

To minimize the time for re-building, you can create soft links to the different artifacts which otherwise need to be manually copied into the *peel* distribution package:

```
config                                       -> /path/to/emma-experiments-peel/src/main/resources/peel/config
datagens
 \-- peel-datagen-1.0-SNAPSHOT.jar           -> /path/to/target/peel-datagen-1.0-SNAPSHOT.jar
datasets
downloads                                    -> /path/to/Downloads/systems
jobs
 \-- emma-experiments-spark-1.0-SNAPSHOT.jar -> /path/to/target/emma-experiments-spark-1.0-SNAPSHOT.jar
lib                                          -> /path/to/target/peel-dist-1.0-SNAPSHOT-bin/lib
log
peel
results
systems
util                                          -> /path/to/emma-experiments-peel/src/main/resources/peel/util
```

## Configuration

Lookup the `$HOSTNAME` of your developer machine and create a corresponding folder. Use the files from `alexander-t540p` as a reference and adapt the entries to configure the experiments package for your environment:

```bash
mkdir config/$HOSTNAME
cp -R config/alexander-t540p/* config/$HOSTNAME
```

If you want to setup a configuration for a different environment, create a folder with the `$HOSTNAME` of the master, e.g. `wally100`.

## Deployment

The `util/sync` folder contains some bash scripts that use rsync for automated deployment and syncronization of the data between the developer machine and the distributed environment master.

Tto use them, you first need to configure the remote host values in `util/sync/${host}.config` file. After that, you can do:

```bash
util/sync/fetch_all.sh $host_name # Pushes peel package to $host
util/sync/fetch_log.sh $host_name # Pushes peel package to $host
util/sync/push_all.sh  $host_name # Pushes peel package to $host
```

## Running Peel

You can use the following Peel commands (and hopefully save some time).

### Setup or Tear Down a System Bean

```bash
# HDFS 1
./peel sys:setup hdfs-1 # starts HDFS-1
./peel sys:teardown hdfs-1 # stops HDFS-1

# HDFS 2
./peel sys:setup hdfs-2 # starts HDFS-2
./peel sys:teardown hdfs-2 # stops HDFS-2

# Zookeeper
./peel sys:setup zookeeper # starts Zookeeper
./peel sys:teardown zookeeper # stops Zookeeper

# Aura
./peel sys:setup aura # starts Aura and (transitively) Zookeeper
./peel sys:teardown aura # stops Aura and (transitively) Zookeeper

# Flink
./peel sys:setup flink # starts Flink
./peel sys:teardown flink # stops Flink

# Spark
./peel sys:setup spark # starts Spark
./peel sys:teardown spark # stops Spark
```

### Run a Single Experiment

```bash
./peel exp:setup wc.default wc.single-run --fixtures ./config/fixtures.wordcount.xml
./peel exp:run wc.default wc.single-run --fixtures ./config/fixtures.wordcount.xml --run 1 --just 
./peel exp:teardown wc.default wc.single-run --fixtures ./config/fixtures.wordcount.xml
```

### Run a Full Experiment Suites

Run the experiments to collect the data

```bash
cd $PEEL_INSTALL_DIR
./peel suite:run $SUITTE_NAME --fixtures=config/$FIXTURES_FILE.xml
```
