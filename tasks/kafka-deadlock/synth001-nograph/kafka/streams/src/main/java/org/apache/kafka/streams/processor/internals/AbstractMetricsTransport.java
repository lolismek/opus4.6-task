/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.processor.internals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Base class for metrics transport connections. Provides read/write locking
 * around transport lifecycle operations. Read (shared) access is used for
 * normal metric fetches; write (exclusive) access is required for reconnects
 * and maintenance operations.
 */
public abstract class AbstractMetricsTransport {

    private static final Logger log = LoggerFactory.getLogger(AbstractMetricsTransport.class);

    private final ReentrantReadWriteLock transportLock = new ReentrantReadWriteLock();

    // Timestamp of last successful connect — read under lock for staleness checks
    volatile long lastConnectTimeMs = -1L;

    protected void acquireExclusive() {
        transportLock.writeLock().lock();
    }

    protected void releaseExclusive() {
        transportLock.writeLock().unlock();
    }

    protected void acquireShared() {
        transportLock.readLock().lock();
    }

    protected void releaseShared() {
        transportLock.readLock().unlock();
    }

    /**
     * Establish or re-establish the underlying transport connection.
     */
    protected abstract void doConnect();

    /**
     * Tear down the underlying transport connection.
     */
    protected abstract void doDisconnect();

    /**
     * @return {@code true} if the current transport connection is still usable
     */
    protected abstract boolean isTransportValid();

    /**
     * Performs a full reconnect cycle under exclusive lock. Subclasses should
     * call this when they detect a stale or broken transport.
     */
    protected void reconnect() {
        acquireExclusive();
        try {
            log.debug("Reconnecting metrics transport");
            doDisconnect();
            doConnect();
            lastConnectTimeMs = System.currentTimeMillis();
        } finally {
            releaseExclusive();
        }
    }
}
