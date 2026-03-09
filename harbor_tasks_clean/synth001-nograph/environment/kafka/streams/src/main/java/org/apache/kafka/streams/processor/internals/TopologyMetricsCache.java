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

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe cache for per-topology metric snapshots. All public methods
 * are synchronized on this instance's intrinsic monitor. Cache entries
 * are evicted when they exceed the configured TTL, or when explicitly
 * invalidated (e.g. after a session shard change during rebalancing).
 */
public class TopologyMetricsCache {

    private static final Logger log = LoggerFactory.getLogger(TopologyMetricsCache.class);

    private final Map<String, CachedMetricsSnapshot> cache = new HashMap<>();
    private final StreamsMetricsConnector metricsConnector;
    private final long cacheTtlMs;

    public TopologyMetricsCache(final StreamsMetricsConnector metricsConnector, final long cacheTtlMs) {
        this.metricsConnector = metricsConnector;
        this.cacheTtlMs = cacheTtlMs;
    }

    /**
     * Returns a cached metrics snapshot for the given topology, refreshing
     * from the backend if the cache entry is missing or stale.
     * Returns null if the topology is unknown.
     */
    public synchronized CachedMetricsSnapshot getSnapshot(final String topologyName) {
        final CachedMetricsSnapshot cached = cache.get(topologyName);
        if (cached != null && !cached.isExpired(cacheTtlMs)) {
            return cached;
        }
        return refreshFromBackend(topologyName);
    }

    private CachedMetricsSnapshot refreshFromBackend(final String topologyName) {
        final Map<String, Double> metrics = metricsConnector.fetchLatestMetrics(topologyName);
        final CachedMetricsSnapshot snapshot = new CachedMetricsSnapshot(topologyName, metrics, System.currentTimeMillis());
        cache.put(topologyName, snapshot);
        return snapshot;
    }

    /**
     * Invalidates all cached entries. Called when the metrics session's shard
     * assignment changes, since metrics may now come from a different shard.
     */
    public synchronized void invalidateAll() {
        log.debug("Invalidating all {} cached topology metrics snapshots", cache.size());
        cache.clear();
    }

    /**
     * Invalidates a single topology's cached entry.
     */
    public synchronized void invalidateTopology(final String topologyName) {
        cache.remove(topologyName);
    }

    /**
     * @return the number of currently cached snapshots
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Creates a {@link MetricsSessionListener} that invalidates this cache
     * when the session's shard assignment changes. The listener is a no-op
     * if the shard stays the same (which is the common case outside of
     * rebalancing).
     */
    public MetricsSessionListener createInvalidationListener() {
        return new TopologyMetricsCacheInvalidator();
    }

    /**
     * Inner listener that bridges session-change events to cache invalidation.
     * The conditional shard check means this only fires during rebalancing
     * when the shard actually changes.
     */
    private class TopologyMetricsCacheInvalidator implements MetricsSessionListener {
        @Override
        public void onSessionChanged(final MetricsSessionManager.MetricsSession oldSession,
                                     final MetricsSessionManager.MetricsSession newSession) {
            if (oldSession != null && newSession != null && oldSession.shardId != newSession.shardId) {
                log.debug("Shard assignment changed from {} to {}, invalidating metrics cache",
                    oldSession.shardId, newSession.shardId);
                invalidateAll();
            } else {
                log.trace("Session renewed without shard change, cache remains valid");
            }
        }
    }

    /**
     * Immutable snapshot of metrics for a single topology at a point in time.
     */
    public static class CachedMetricsSnapshot {
        private final String topologyName;
        private final Map<String, Double> metrics;
        private final long fetchedAtMs;

        public CachedMetricsSnapshot(final String topologyName, final Map<String, Double> metrics, final long fetchedAtMs) {
            this.topologyName = topologyName;
            this.metrics = metrics;
            this.fetchedAtMs = fetchedAtMs;
        }

        public String topologyName() {
            return topologyName;
        }

        public Map<String, Double> metrics() {
            return metrics;
        }

        public long fetchedAtMs() {
            return fetchedAtMs;
        }

        public boolean isExpired(final long ttlMs) {
            return System.currentTimeMillis() - fetchedAtMs > ttlMs;
        }
    }
}
