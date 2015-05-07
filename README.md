# Efficient Read-less Increments in HBase

Many times when [Apache HBase](http://hbase.apache.org) is used for storing counters the result of an Increment operation that is performed to update them is ignored. Yet, the HBase does all the necessary job to compute and return the result value which reduces efficiency by performing redundant read operations.

[Cask Data Application Platform](http://cdap.io) (CDAP) provides a special read-less [incrementWrite operation on a Table dataset](http://docs.cask.co/cdap/3.0.0/en/developers-manual/building-blocks/datasets/table.html#increment) that stores data in HBase. It helps to improve performance and in many use-cases that depend heavily on counting, such as [OLAP Cube](http://docs.cask.co/cdap/3.0.0/en/developers-manual/building-blocks/datasets/cube.html).

This tiny project provides an example of how you can **use the read-less increments in any HBase application** (no CDAP is required). In that case you will miss a very cool support for [transactions](http://docs.cask.co/cdap/3.0.0/en/developers-manual/building-blocks/transaction-system.html?highlight=transactions) that comes with CDAP datasets, which may or may not be fine in your use-case.

Currently the project is configured to use HBase 0.98+, but you can configure it to use 0.96 as well. In case of any questions or issues, please start discussion in [this mailing list](https://groups.google.com/forum/#!forum/cdap-user).

# How-to

## Configure the HBase table

Read-less increments require an [HBase Coprocessor](https://hbase.apache.org/book.html#cp) to be set for a table. First, we need to build a Coprocessor jar:

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

Note that we use special property to tell the Coprocessor to turn off transactions support, which you want to do unless you use [Tephra](http://tephra.io) (transactions for HBase). Also, readless increments require setting versions number to keep to "unlimited". While the Coprocessor we configured takes care of merging puts for readless increments, keeping unlimited versions for any other cases may affect other operations performance and housekeeping. Be sure to set the configuration only on the column families you use to keep counters updated with read-less incremetns.

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

**HTable Increments**

(on table with default configuration created with ``create 'mytable2', {NAME => 'd'}``)

```
$ java -cp `hbase classpath`:cdap-hbase-increments-1.0.0-SNAPSHOT.jar co.cask.cdap.hbase.increments.ExampleClient mytable2 row1 d mycounter2 10000 INCREMENT

Starting 10000 INCREMENTs...
Completed 1000 operations in 2575 ms.
Completed 2000 operations in 4702 ms.
Completed 3000 operations in 6619 ms.
Completed 4000 operations in 7876 ms.
Completed 5000 operations in 9299 ms.
Completed 6000 operations in 10783 ms.
Completed 7000 operations in 12205 ms.
Completed 8000 operations in 13627 ms.
Completed 9000 operations in 15119 ms.
Completed 10000 operations in 16771 ms.
Current value: 10000, retrieved in: 5 ms.
```

**HTable Puts** (just for comparison)

```
$ java -cp `hbase classpath`:cdap-hbase-increments-1.0.0-SNAPSHOT.jar co.cask.cdap.hbase.increments.ExampleClient mytable2 row1 d mycounter3 10000 PUT

Starting 10000 PUTs...
Completed 1000 operations in 43 ms.
Completed 2000 operations in 73 ms.
Completed 3000 operations in 97 ms.
Completed 4000 operations in 118 ms.
Completed 5000 operations in 601 ms.
Completed 6000 operations in 630 ms.
Completed 7000 operations in 648 ms.
Completed 8000 operations in 666 ms.
Completed 9000 operations in 684 ms.
Completed 10000 operations in 1779 ms.
Current value: 1, retrieved in: 5 ms.
```

Again, you should not take numbers for granted, but you cannot not notice couple things:
* readless increments are much faster than normal increments and 
* on par with simple puts (which they almost are)
* reading is slower for read-less increments case

The last observation is expected, since at read the Coprocessor needs to merge many appended Puts. But it won't go out of the limits: merges are performed and stored automatically on memstore flushes and compactions.
