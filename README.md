# Efficient Read-less Increments in HBase

Many times when [Apache HBase](hbase.apache.org) is used for counting the result of Increment operation performed is ignored. Yet, the HBase does all the necessary job to compute and return the result value which reduces efficiency by performing redundant read operations.

[Cask Data Application Platform](cdap.io) (CDAP) provides a special read-less [incrementWrite operation on a Table dataset]() that stores data in HBase. It helps to improve performance and in many use-cases that depend heavily on counting, such as [OLAP Cube]().

This tiny project provides an example of how you can use a read-less feature implemented in CDAP in any application that deals with HBase directly. In that case you will miss a very cool support for [transactions] that comes with CDAP datasets, which may or may not be fine in your use-case.

# How-to
