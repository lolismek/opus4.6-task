#!/bin/bash
# Gold patch: Fix the 3-node deadlock cycle by moving validateSession()
# outside the write lock in StreamsMetricsConnector.performMaintenance().
#
# The deadlock cycle is:
#   Thread 1: synchronized(TopologyMetricsCache) -> writeLock(AbstractMetricsTransport)
#   Thread 2: writeLock(AbstractMetricsTransport) -> sessionLock(MetricsSessionManager)
#   Thread 3: sessionLock(MetricsSessionManager) -> synchronized(TopologyMetricsCache)
#
# Fix: Break edge B->C by calling validateSession() (which acquires sessionLock)
# after releasing the writeLock in performMaintenance(). Session validation
# does not depend on transport state, so it does not need the write lock.

cd /app

INTERNALS="streams/src/main/java/org/apache/kafka/streams/processor/internals"

cat > "$INTERNALS/StreamsMetricsConnector.java" << 'JAVA'
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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete metrics transport for Kafka Streams topologies. Fetches topology
 * metric snapshots from the metrics backend and manages the transport
 * connection lifecycle. Extends {@link AbstractMetricsTransport} for
 * read/write lock management around the transport.
 */
public class StreamsMetricsConnector extends AbstractMetricsTransport {

    private static final Logger log = LoggerFactory.getLogger(StreamsMetricsConnector.class);

    private final MetricsSessionManager sessionManager;
    private final ConcurrentHashMap<String, Long> topologyFetchTimestamps = new ConcurrentHashMap<>();

    private volatile boolean transportValid = true;

    public StreamsMetricsConnector(final MetricsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Fetches the latest metrics for the given topology. Under normal
     * conditions this acquires a shared (read) lock, which cannot deadlock.
     * However, when the transport is detected as stale, it falls through to
     * {@link #refreshTransport(String)}, which acquires the exclusive (write) lock.
     *
     * @param topologyName the name of the topology to fetch metrics for
     * @return a map of metric name to value
     */
    public Map<String, Double> fetchLatestMetrics(final String topologyName) {
        acquireShared();
        try {
            if (isTransportValid()) {
                // Fast path: transport is healthy, fetch under read lock
                topologyFetchTimestamps.computeIfAbsent(topologyName, k -> System.currentTimeMillis());
                return doFetch(topologyName);
            }
        } finally {
            releaseShared();
        }

        // Slow path: transport is stale, need exclusive access to refresh
        return refreshTransport(topologyName);
    }

    /**
     * Refreshes the transport connection and then fetches metrics.
     * Acquires the write lock (exclusive).
     */
    private Map<String, Double> refreshTransport(final String topologyName) {
        acquireExclusive();
        try {
            log.debug("Refreshing stale metrics transport before fetching {}", topologyName);
            doDisconnect();
            doConnect();
            lastConnectTimeMs = System.currentTimeMillis();
            transportValid = true;
            return doFetch(topologyName);
        } finally {
            releaseExclusive();
        }
    }

    /**
     * Performs periodic transport maintenance. This is called from the
     * state updater thread (Thread 2). Transport reconnection is done under
     * the write lock; session validation is done afterwards without holding
     * the write lock to avoid lock-ordering issues.
     */
    public void performMaintenance() {
        acquireExclusive();
        try {
            log.debug("Performing metrics transport maintenance");
            if (!isTransportValid()) {
                doDisconnect();
                doConnect();
                lastConnectTimeMs = System.currentTimeMillis();
                transportValid = true;
            }
        } finally {
            releaseExclusive();
        }
        // Validate session outside the write lock to avoid lock-ordering
        // issues: refreshSessionIfExpired() may call listeners that
        // acquire other locks (e.g. synchronized(TopologyMetricsCache)).
        validateSession();
    }

    private void validateSession() {
        final MetricsSessionManager.MetricsSession session = sessionManager.currentSession();
        if (session == null || session.expiryMs <= System.currentTimeMillis()) {
            log.debug("Current metrics session expired, requesting refresh");
            sessionManager.refreshSessionIfExpired();
        }
    }

    private Map<String, Double> doFetch(final String topologyName) {
        // In a real implementation this would query the metrics backend
        topologyFetchTimestamps.put(topologyName, System.currentTimeMillis());
        return Collections.emptyMap();
    }

    public void markTransportInvalid() {
        this.transportValid = false;
    }

    @Override
    protected void doConnect() {
        log.debug("Connecting metrics transport");
        transportValid = true;
    }

    @Override
    protected void doDisconnect() {
        log.debug("Disconnecting metrics transport");
    }

    @Override
    protected boolean isTransportValid() {
        return transportValid;
    }
}
JAVA
