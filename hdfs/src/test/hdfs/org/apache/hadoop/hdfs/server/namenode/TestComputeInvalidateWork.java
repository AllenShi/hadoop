/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.namenode;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.server.blockmanagement.DatanodeDescriptor;
import org.apache.hadoop.hdfs.server.common.GenerationStamp;

/**
 * Test if FSNamesystem handles heartbeat right
 */
public class TestComputeInvalidateWork extends TestCase {
  /**
   * Test if {@link FSNamesystem#computeInvalidateWork(int)}
   * can schedule invalidate work correctly 
   */
  public void testCompInvalidate() throws Exception {
    final Configuration conf = new HdfsConfiguration();
    final int NUM_OF_DATANODES = 3;
    final MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(NUM_OF_DATANODES).build();
    try {
      cluster.waitActive();
      final FSNamesystem namesystem = cluster.getNamesystem();
      DatanodeDescriptor[] nodes =
        namesystem.heartbeats.toArray(new DatanodeDescriptor[NUM_OF_DATANODES]);
      assertEquals(nodes.length, NUM_OF_DATANODES);
      
      namesystem.writeLock();
      try {
        for (int i=0; i<nodes.length; i++) {
          for(int j=0; j<3*namesystem.blockInvalidateLimit+1; j++) {
            Block block = new Block(i*(namesystem.blockInvalidateLimit+1)+j, 0, 
                GenerationStamp.FIRST_VALID_STAMP);
            namesystem.getBlockManager().addToInvalidates(block, nodes[i]);
          }
        }
        
        assertEquals(namesystem.blockInvalidateLimit*NUM_OF_DATANODES, 
            namesystem.getBlockManager().computeInvalidateWork(NUM_OF_DATANODES+1));
        assertEquals(namesystem.blockInvalidateLimit*NUM_OF_DATANODES, 
            namesystem.getBlockManager().computeInvalidateWork(NUM_OF_DATANODES));
        assertEquals(namesystem.blockInvalidateLimit*(NUM_OF_DATANODES-1), 
            namesystem.getBlockManager().computeInvalidateWork(NUM_OF_DATANODES-1));
        int workCount = namesystem.getBlockManager().computeInvalidateWork(1);
        if (workCount == 1) {
          assertEquals(namesystem.blockInvalidateLimit+1, 
              namesystem.getBlockManager().computeInvalidateWork(2));
        } else {
          assertEquals(workCount, namesystem.blockInvalidateLimit);
          assertEquals(2, namesystem.getBlockManager().computeInvalidateWork(2));
        }
      } finally {
        namesystem.writeUnlock();
      }
    } finally {
      cluster.shutdown();
    }
  }
}
