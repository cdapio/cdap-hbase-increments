/*
 * Copyright 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.hbase.increments;

import co.cask.cdap.data2.dataset2.lib.table.hbase.HBaseTable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;

/**
 * An example of using read-less increments.
 */
public class ExampleClient {
  private static final byte[] EMPTY_BYTES = new byte[0];
  private static final String USAGE =
    "\nUsage: ExampleClient <table-name> <row> <column-family-name> <column-name> <increments-count> " +
      "<INCREMENT|INCREMENT_WRITE|PUT>\n\n";

  public static void main(String[] args) throws IOException {
    if (args.length != 6) {
      System.out.printf(USAGE);
      System.exit(1);
    }
    int index = 0;
    String tableName = args[index++];
    byte[] row = Bytes.toBytes(args[index++]);
    byte[] colfam = Bytes.toBytes(args[index++]);
    byte[] qualifier = Bytes.toBytes(args[index++]);
    int increments = Integer.valueOf(args[index++]);
    OpType opType = OpType.valueOf(args[index]);

    Configuration conf = HBaseConfiguration.create();
    HTable table = new HTable(conf, tableName);
    table.setAutoFlush(false, false);

    System.out.println("Starting " + increments + " " + opType + "s...");
    long start = System.currentTimeMillis();
    for (int i = 0; i < increments; i++) {
      performOperation(table, opType, row, colfam, qualifier);
      if (i % 1000 == 0 && i > 0) {
        printProgress(i, start);
      }
    }
    table.flushCommits();
    printProgress(increments, start);

    start = System.currentTimeMillis();
    Result result = table.get(new Get(row).addColumn(colfam, qualifier));
    long value = Bytes.toLong(result.getValue(colfam, qualifier));
    long getTime = System.currentTimeMillis() - start;
    System.out.println("Current value: " + value + ", retrieved in: " + getTime + " ms.");
  }

  private static void performOperation(HTable table, OpType opType,
                                       byte[] row, byte[] colfam, byte[] qualifier) throws IOException {
    switch (opType) {
      case INCREMENT:
        table.increment(new Increment(row).addColumn(colfam, qualifier, 1));
        break;
      case INCREMENT_WRITE:
        table.put(newIncrement(row, colfam, qualifier, 1));
        break;
      case PUT:
        table.put(new Put(row).add(colfam, qualifier, Bytes.toBytes(1L)));
        break;
      default:
        throw new IllegalArgumentException("Unsupported operation: " + opType);
    }
  }

  public static Put newIncrement(byte[] row, byte[] family, byte[] qualifier, long value) {
    Put p = new Put(row);
    p.add(family, qualifier, Bytes.toBytes(value));
    p.setAttribute(HBaseTable.DELTA_WRITE, EMPTY_BYTES);
    return p;
  }

  private static void printProgress(int increments, long start) {
    long timeSpent = System.currentTimeMillis() - start;
    System.out.println("Completed " + increments + " operations in " + timeSpent + " ms.");
  }

  private enum OpType {
    INCREMENT,
    INCREMENT_WRITE,
    PUT
  }
}
