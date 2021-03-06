/*
 * Copyright (C) 2017 The Async BigTable Authors.  All rights reserved.
 * This file is part of Async BigTable.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the StumbleUpon nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */package org.hbase.async;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.apache.commons.lang.ArrayUtils;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.shaded.org.junit.AfterClass;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.cloud.bigtable.hbase.BigtableConfiguration;

@RunWith(JUnit4.class)
public class HBaseClientIT {

  private static TableName TABLE_NAME =
      TableName.valueOf("test_table-" + UUID.randomUUID().toString());
  private static byte[] FAMILY = Bytes.toBytes("cf");
  private static final DataGenerationHelper dataHelper = new DataGenerationHelper();

  @BeforeClass
  public static void createTable() throws IOException {
    String projectId = System.getProperty( "google.bigtable.project.id");
    String instanceId = System.getProperty( "google.bigtable.instance.id");
    client = new HBaseClient(BigtableConfiguration.configure(projectId, instanceId),
        Executors.newCachedThreadPool());
    Admin admin = client.getBigtableConnection().getAdmin();
    try {
      admin.createTable(
        new HTableDescriptor(TABLE_NAME)
          .addFamily(new HColumnDescriptor(FAMILY)));
    } finally {
      admin.close();
    }
  }

  @AfterClass
  public static void deleteTable() throws IOException {
    Admin admin = client.getBigtableConnection().getAdmin();
    try {
      admin.deleteTable(TABLE_NAME);
    } finally {
      admin.close();
    }
  }

  private static HBaseClient client;


  /**
   * Really basic test to make sure that put, get and delete work.
   */
  @Test
  public void testBasics() throws Exception {
    byte[] rowKey = dataHelper.randomData("putKey-");
    byte[] qualifier = Bytes.toBytes("qual");
    byte[] value = dataHelper.randomData("value-");

    // Write the value, and make sure it's written
    client.put(new PutRequest(TABLE_NAME.getName(), rowKey, FAMILY, qualifier, value));
    client.flush().join();

    // Make sure that the value is as expected
    assertGetEquals(rowKey, qualifier, value);

    // Delete the value
    client.delete(new DeleteRequest(TABLE_NAME.getName(), rowKey)).join();

    // Make sure that the value is deleted
    Assert.assertEquals(0, get(rowKey).size());
  }

  @Test
  public void testAppend() throws Exception {
    byte[] rowKey = dataHelper.randomData("appendKey-");
    byte[] qualifier = dataHelper.randomData("qualifier-");
    byte[] value1 = dataHelper.randomData("value1-");
    byte[] value2 = dataHelper.randomData("value1-");
    byte[] value1And2 = ArrayUtils.addAll(value1, value2);

    // Write the value, and make sure it's written
    client.put(new PutRequest(TABLE_NAME.getName(), rowKey, FAMILY, qualifier, value1));
    client.flush().join();

    client
        .append(
          new AppendRequest(TABLE_NAME.getName(), new KeyValue(rowKey, FAMILY, qualifier, value2)))
        .join();

    ArrayList<KeyValue> response = get(rowKey);
    Assert.assertEquals(1, response.size());
    Assert.assertTrue(Bytes.equals(value1And2, response.get(0).value()));
  }

  private void assertGetEquals(byte[] key, byte[] qual, byte[] val)
      throws Exception {
    ArrayList<KeyValue> response = get(key);
    Assert.assertEquals(1, response.size());
    KeyValue result = response.get(0);

    Assert.assertTrue(Bytes.equals(FAMILY, result.family()));
    Assert.assertTrue(Bytes.equals(qual, result.qualifier()));
    Assert.assertTrue(Bytes.equals(val, result.value()));
  }

  private ArrayList<KeyValue> get(byte[] key) throws Exception {
    return client.get(new GetRequest(TABLE_NAME.getName(), key)).join();
  }
}
