# Efficient Read-less Increments in HBase

Many times when [Apache HBase](hbase.apache.org) is used for counting the result of Increment operation performed is ignored. Yet, the HBase does all the necessary job to compute and return the result value which reduces efficiency by performing redundant read operations.

[Cask Data Application Platform](cdap.io) (CDAP) provides a special read-less [incrementWrite operation on a Table dataset]() that stores data in HBase. It helps to improve performance and in many use-cases that depend heavily on counting, such as [OLAP Cube]().

This tiny project provides an example of how you can use a read-less feature implemented in CDAP in any application that deals with HBase (0.98+) directly. In that case you will miss a very cool support for [transactions] that comes with CDAP datasets, which may or may not be fine in your use-case.

# How-to

Read-less increments require a [RegionServer Coprocessor]() to be set for a table. First, we need to build a Coprocessor jar:

```
$ mvn clean package
```

Then, you need to place the jar in HDFS to be accessible by HBase (in /tmp directory in this case):

```
$ hdfs dfs -put target/cdap-hbase-increments-1.0.0-SNAPSHOT.jar /tmp/
```

The following is an example of creating and configuring an HBase table with the required Coprocessor:

```
$ hbase shell
> create 'mytable', {NAME => 'd', VERSIONS => '2147483647', METADATA => {'dataset.table.readless.increment.transactional' => 'false'}}
> alter 'mytable', 'coprocessor' => 'hdfs:///tmp/cdap-hbase-increments-1.0.0-SNAPSHOT.jar |co.cask.cdap.data2.increment.hbase98.IncrementHandler|'
```

Note that we use special property to tell the Coprocessor to turn off transactions support, which you want to do unless you use [Tephra]() (transactions for HBase).


