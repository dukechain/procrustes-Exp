# Procrustes

Basic instructions for configuring and running the set of experiments with Peel.

| System       | KMeans  | Grouping | Conn. Comp. |
|:-------------|:-------:|:--------:|:-----------:|
| Spark        |    ✓    |    ✓     |     ❌       |
| Flink        |    ✓    |    ✓     |     ❌       |

## Dependencies

Checkout and install the [Peel](https://github.com/citlab/peel) package in your local maven repository:

```bash
# clone Peel project
git clone git@github.com:citlab/peel.git
# install Peel modules locally
mvn install -DskipTests
```

## Setup

You need to configure a *Procrustes* distribution. Check out the *Procrustes* sources and do:

```bash
# create binary package
mvn package -DskipTests
# move binary package into a separate folder
cp procrustes-dist/target/procrustes-dist-1.0-SNAPSHOT-bin $PROCRUSTES_INSTALL_DIR
```

The distribution package has the following structure:

```
config                                 # env. configuration and experiment fixtures
datagens                               # data generators
datasets                               # data sets
downloads                              # system downloads (empty)
jobs                                   # experiment jobs
 \-- procrustes-flink-${VERSION}.jar   # Flink experiment jobs
 \-- procrustes-spark-${VERSION}.jar   # Spark experiment jobs
lib                                    # Peel libraries
log                                    # Peel log
peel                                   # Peel CLI tool
results                                # experiment results
systems                                # bash utils
```

## Configuration

You need to create a configuration folder for each host where you plan to run *Procrustes* experiments.

### Local Development

Lookup the `$HOSTNAME` of your developer machine and create a corresponding folder under `config`.
Use the `localhost-sample` configuration as a starting point and adapt their values to something that better suits your environment:

```bash
cd config
mkdir $HOSTNAME
cp -R localhost-sample/* $HOSTNAME
```

### Supported Distributed Environments

For usage on the `wally` cluster, you can just create a soft-link to `wally`:

```bash
cd config
ln -s wally $HOSTNAME
```

### Other Distributed Environments

If you want to setup a configuration for a different distributed environment, create a folder with the `$HOSTNAME` of the environment master.
Use the `wally` configuration as a starting point:

```bash
cd config
mkdir $HOSTNAME
cp -R localhost-sample/* $HOSTNAME
```

## Deployment

The `util/sync` folder contains some bash scripts that use rsync for automated deployment and synchronization of the data between the developer machine and the distributed environment master.

To use them, you first need to configure the remote host values in `util/sync/${host}.config` file. After that, you can do:

```bash
util/sync/fetch_all.sh $host_name # Pushes Procrustes package to $host
util/sync/fetch_log.sh $host_name # Pushes Procrustes package to $host
util/sync/push_all.sh  $host_name # Pushes Procrustes package to $host
```

## Running Procrustes Experiments

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

# Flink
./peel sys:setup flink # starts Flink
./peel sys:teardown flink # stops Flink

# Spark
./peel sys:setup spark # starts Spark
./peel sys:teardown spark # stops Spark
```

### Run a Single Experiment

You can setup the systems, run the experiment, and teardown the systems using three different commands:

```bash
# setup all systems upon which this experiment depends
./peel exp:setup kmeans.default kmeans.single-run
# execute run no. 1 of the experiment
./peel exp:run kmeans.default kmeans.single-run --just --run 1
# teardown all systems upon which this experiment depends
./peel exp:teardown kmeans.default kmeans.single-run
```

Alternatively, you can do all in one step:

```bash
# setup, execute run no. 1, and teardown in one step
./peel exp:run kmeans.default kmeans.single-run  --run 1
```

### Run all Experiments in a Suite

Logically connected experiments are organized in suites. To run all experiments in a suite, use the `suite:run` command:

```bash
./peel suite:run kmeans.default
```
