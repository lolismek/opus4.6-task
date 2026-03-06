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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages authenticated sessions for the metrics transport layer.
 * Sessions have an expiry time and a shard assignment; during rebalancing
 * the shard may change, which triggers listener notifications.
 */
public class MetricsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(MetricsSessionManager.class);

    private final ReentrantLock sessionLock = new ReentrantLock();
    private final Condition sessionReady = sessionLock.newCondition();
    private final long sessionTtlMs;

    private volatile MetricsSession currentSession;
    private boolean rebalancingMode = false;

    private final List<MetricsSessionListener> listeners = new CopyOnWriteArrayList<>();

    public MetricsSessionManager(final long sessionTtlMs) {
        this.sessionTtlMs = sessionTtlMs;
        this.currentSession = new MetricsSession(
            "init-" + ThreadLocalRandom.current().nextInt(10000),
            0,
            System.currentTimeMillis() + sessionTtlMs
        );
    }

    public void registerListener(final MetricsSessionListener listener) {
        listeners.add(listener);
    }

    public void setRebalancingMode(final boolean rebalancing) {
        sessionLock.lock();
        try {
            this.rebalancingMode = rebalancing;
            log.debug("Metrics session manager rebalancing mode set to {}", rebalancing);
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Returns the current session. The returned reference is safe to read
     * without holding the lock due to the volatile field, but the session
     * may expire at any time.
     */
    public MetricsSession currentSession() {
        return currentSession;
    }

    /**
     * If the current session has expired, renew it. Called from the transport
     * maintenance path (Thread 2 in the normal flow).
     */
    public void refreshSessionIfExpired() {
        sessionLock.lock();
        try {
            if (currentSession != null && currentSession.expiryMs > System.currentTimeMillis()) {
                return; // still valid
            }
            renewSessionInternal();
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Force a session renewal. Called from the session renewal thread (Thread 3).
     */
    public void renewSession() {
        sessionLock.lock();
        try {
            renewSessionInternal();
        } finally {
            sessionLock.unlock();
        }
    }

    private void renewSessionInternal() {
        final MetricsSession oldSession = currentSession;
        final int newShardId;
        if (rebalancingMode) {
            // During rebalancing, the shard assignment may change
            newShardId = ThreadLocalRandom.current().nextInt(1024);
        } else {
            newShardId = oldSession != null ? oldSession.shardId : 0;
        }

        final MetricsSession newSession = new MetricsSession(
            "sess-" + ThreadLocalRandom.current().nextInt(100000),
            newShardId,
            System.currentTimeMillis() + sessionTtlMs
        );
        currentSession = newSession;
        log.debug("Renewed metrics session: old={}, new={}", oldSession, newSession);

        notifyListeners(oldSession, newSession);
        sessionReady.signalAll();
    }

    private void notifyListeners(final MetricsSession oldSession, final MetricsSession newSession) {
        for (final MetricsSessionListener listener : listeners) {
            try {
                listener.onSessionChanged(oldSession, newSession);
            } catch (final Exception e) {
                log.warn("Metrics session listener threw exception", e);
            }
        }
    }

    /**
     * Represents an authenticated metrics session with a token, shard
     * assignment, and expiry timestamp.
     */
    public static class MetricsSession {
        public final String token;
        public final int shardId;
        public final long expiryMs;

        public MetricsSession(final String token, final int shardId, final long expiryMs) {
            this.token = token;
            this.shardId = shardId;
            this.expiryMs = expiryMs;
        }

        @Override
        public String toString() {
            return "MetricsSession{token='" + token + "', shard=" + shardId + ", expiry=" + expiryMs + "}";
        }
    }
}
