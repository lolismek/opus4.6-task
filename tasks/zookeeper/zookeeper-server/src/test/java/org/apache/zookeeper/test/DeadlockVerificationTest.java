/*
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

package org.apache.zookeeper.test;

import static org.apache.zookeeper.test.ClientBase.CONNECTION_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.quorum.Leader;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test to verify deadlock between Leader and LearnerHandler.
 *
 * Reproduces an ABBA lock cycle:
 *   Thread 1: Leader.tryToCommit() [holds Leader.this]
 *             -> inform() -> sendObserverPacket() -> f.getLastZxid() [tries LearnerHandler.this]
 *   Thread 2: getObservingLearnersInfo() -> getLearnerHandlerInfo() [holds LearnerHandler.this]
 *             -> getProposalLag() -> getLastProposed() [tries Leader.this]
 */
public class DeadlockVerificationTest extends ObserverMasterTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(DeadlockVerificationTest.class);

    @Test
    public void testLeaderObserverHandlerDeadlock() throws Exception {
        // Set up a 2-participant + 1-observer ensemble
        latch = new CountDownLatch(2);
        setUp(-1, false);
        q3.start();
        assertTrue(ClientBase.waitForServerUp("127.0.0.1:" + CLIENT_PORT_OBS, CONNECTION_TIMEOUT),
                "waiting for observer to come up");

        // Find the leader
        QuorumPeer leaderPeer;
        if (q1.getQuorumPeer().leader != null) {
            leaderPeer = q1.getQuorumPeer();
        } else {
            leaderPeer = q2.getQuorumPeer();
        }
        Leader leader = leaderPeer.leader;
        assertTrue(leader != null, "leader should be elected");

        // Connect a client to the observer so writes get proxied through leader
        zk = new ZooKeeper("127.0.0.1:" + CLIENT_PORT_OBS, CONNECTION_TIMEOUT, this);
        zk.create("/deadlock-test", "init".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        AtomicBoolean deadlockDetected = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger queryCount = new AtomicInteger(0);

        // Thread 1: continuously write data (triggers tryToCommit -> inform -> sendObserverPacket)
        Thread writerThread = new Thread(() -> {
            try {
                ZooKeeper writerZk = new ZooKeeper("127.0.0.1:" + CLIENT_PORT_OBS, CONNECTION_TIMEOUT, event -> {});
                int i = 0;
                while (!stop.get() && !deadlockDetected.get()) {
                    try {
                        writerZk.setData("/deadlock-test", ("v" + i).getBytes(), -1);
                        writeCount.incrementAndGet();
                        i++;
                    } catch (Exception e) {
                        // Ignore transient errors
                    }
                }
                writerZk.close();
            } catch (Exception e) {
                LOG.error("Writer thread error", e);
            }
        }, "DeadlockTest-Writer");

        // Thread 2: continuously query observer handler info
        // (triggers getObservingLearnersInfo -> getLearnerHandlerInfo)
        Thread queryThread = new Thread(() -> {
            while (!stop.get() && !deadlockDetected.get()) {
                try {
                    leader.getObservingLearnersInfo();
                    queryCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }, "DeadlockTest-Query");

        // Deadlock detector thread
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Thread detectorThread = new Thread(() -> {
            while (!stop.get() && !deadlockDetected.get()) {
                long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
                if (deadlockedThreads != null) {
                    deadlockDetected.set(true);
                    LOG.error("DEADLOCK DETECTED! Thread IDs: ");
                    ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(deadlockedThreads, true, true);
                    for (ThreadInfo ti : threadInfos) {
                        LOG.error(ti.toString());
                    }
                    return;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "DeadlockTest-Detector");

        // Run the stress test
        writerThread.start();
        queryThread.start();
        detectorThread.start();

        // Run for up to 30 seconds
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && !deadlockDetected.get()) {
            Thread.sleep(100);
            if (writeCount.get() % 100 == 0 && writeCount.get() > 0) {
                LOG.info("Progress: writes={}, queries={}", writeCount.get(), queryCount.get());
            }
        }

        stop.set(true);

        // If deadlocked, the threads won't join. Give them a short timeout.
        writerThread.join(5000);
        queryThread.join(5000);
        detectorThread.join(2000);

        LOG.info("Test complete: writes={}, queries={}, deadlockDetected={}",
                writeCount.get(), queryCount.get(), deadlockDetected.get());

        if (deadlockDetected.get()) {
            LOG.error("SUCCESS: Deadlock was reproduced!");
        } else {
            LOG.warn("Deadlock was NOT reproduced in this run (expected - it's non-deterministic)");
        }

        // Clean up - if deadlocked, force interrupt
        if (writerThread.isAlive()) writerThread.interrupt();
        if (queryThread.isAlive()) queryThread.interrupt();

        // Force-shutdown on a daemon thread to avoid hanging on deadlocked threads.
        // QuorumPeer.shutdown() internally waits for threads that may be deadlocked,
        // so we can't call it on the main thread.
        Thread shutdownThread = new Thread(() -> {
            try { zk.close(); } catch (Exception e) { /* ignore */ }
            try { q1.getQuorumPeer().shutdown(); } catch (Exception e) { /* ignore */ }
            try { q2.getQuorumPeer().shutdown(); } catch (Exception e) { /* ignore */ }
            try { q3.getQuorumPeer().shutdown(); } catch (Exception e) { /* ignore */ }
        }, "DeadlockTest-Shutdown");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
        shutdownThread.join(5000);

        assertTrue(deadlockDetected.get(),
                "Deadlock should have been detected between Leader.this and LearnerHandler.this");
    }
}
