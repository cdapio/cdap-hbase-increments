# Efficient Read-less Increments in HBase

Many times when [Apache HBase](hbase.apache.org) is used for counting the result of Increment operation performed is ignored. Yet, the HBase does all the necessary job to compute and return the result value which reduces efficiency by performing redundant read operations.

[Cask Data Application Platform](cdap.io) (CDAP) provides a special read-less [incrementWrite operation on a Table dataset]() that stores data in HBase. It helps to improve performance and in many use-cases that depend heavily on counting, such as [OLAP Cube]().

This tiny project provides an example of how you can use a read-less feature implemented in CDAP in any application that deals with HBase (0.98+) directly. In that case you will miss a very cool support for [transactions] that comes with CDAP datasets, which may or may not be fine in your use-case.

# How-to

## Configure the HBase table

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

## Read-less increments using HTable

To perform a readless-increment you need to issue a Put with special attribute that is recognized by the configured Coprocessor:

``` java
  Put p = new Put(row);
  p.add(family, qualifier, Bytes.toBytes(1L));
  p.setAttribute(HBaseTable.DELTA_WRITE, EMPTY_BYTES);
  hTable.put(p);
```

That's all you need to do in your code. Reading the counter value back can be done with ordinary Get or Scan.

# Run it!

This tiny project comes with an example client that can perform readless increment, normal increment, or put operations. The ``main()`` method of ``ExampleClient`` accepts the following params:
```
<table-name> <row> <column-family-name> <column-name> <increments-count> <INCREMENT|INCREMENT_WRITE|PUT>
```

Blow are examples of running different operations. Please do not rely on absolute numbers wrt latencies as it was run on small cluster and only few operations performed, even though the difference is good looking even here. Note: the program was run on one of the nodes of HBase cluster, so hbase-site.xml and all HBase jars needed for client are added by inclding HBase classpath (you configuration may vary).

**Read-less increments**

```
$ java -cp `hbase classpath`:cdap-hbase-increments-1.0.0-SNAPSHOT.jar co.cask.cdap.hbase.increments.ExampleClient mytable row1 d mycounter 10000 INCREMENT_WRITE

Starting 10000 INCREMENT_WRITEs...
Completed 1000 operations in 52 ms.
Completed 2000 operations in 89 ms.
Completed 3000 operations in 122 ms.
Completed 4000 operations in 617 ms.
Completed 5000 operations in 656 ms.
Completed 6000 operations in 682 ms.
Completed 7000 operations in 1508 ms.
Completed 8000 operations in 1537 ms.
Completed 9000 operations in 1566 ms.
Completed 10000 operations in 1993 ms.
Current value: 10000, retrieved in: 19 ms.
```

**Native increments**

```
$ java -cp `hbase classpath`:cdap-hbase-increments-1.0.0-SNAPSHOT.jar co.cask.cdap.hbase.increments.ExampleClient mytable row1 d mycounter2 10000 INCREMENT

Starting 10000 INCREMENTs...
Completed 1000 operations in 2543 ms.
Completed 2000 operations in 4665 ms.
Completed 3000 operations in 6576 ms.
Completed 4000 operations in 8307 ms.
Completed 5000 operations in 9920 ms.
Completed 6000 operations in 11548 ms.
Completed 7000 operations in 12994 ms.
Completed 8000 operations in 14476 ms.
Completed 9000 operations in 16028 ms.
Completed 10000 operations in 17504 ms.
Current value: 10000, retrieved in: 320 ms.
```

**Puts** (just for comparison)

```
$ java -cp `hbase classpath`:cdap-hbase-increments-1.0.0-SNAPSHOT.jar co.cask.cdap.hbase.increments.ExampleClient mytable row1 d mycounter3 10000 PUT

Starting 10000 PUTs...
Completed 1000 operations in 42 ms.
Completed 2000 operations in 73 ms.
Completed 3000 operations in 101 ms.
Completed 4000 operations in 125 ms.
Completed 5000 operations in 460 ms.
Completed 6000 operations in 476 ms.
Completed 7000 operations in 486 ms.
Completed 8000 operations in 496 ms.
Completed 9000 operations in 506 ms.
Completed 10000 operations in 1302 ms.
Current value: 1, retrieved in: 242 ms.
```

Again, you should not take numbers for granted, but you cannot not notice that readless increments are much faster than normal increments and on par with simple puts (which they almost are).
