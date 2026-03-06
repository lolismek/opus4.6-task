#!/bin/bash
# Gold patch: remove the injected rebalanceIfNeeded() method from ThreadCache.java
# and remove the ensureCacheCapacity() call and method from CachingKeyValueStore.java.
# This eliminates the lock-ordering cycle between ThreadCache and NamedCache.

cd /app

THREAD_CACHE="streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java"
CACHING_KV="streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java"

# 1. Remove rebalanceIfNeeded() method from ThreadCache.java
sed -i '/^    void rebalanceIfNeeded() {$/,/^    }$/d' "$THREAD_CACHE"

# 2. Remove ensureCacheCapacity(context); call from putAndMaybeForward() in CachingKeyValueStore.java
sed -i '/ensureCacheCapacity(context);/d' "$CACHING_KV"

# 3. Remove ensureCacheCapacity() method definition from CachingKeyValueStore.java
sed -i '/^    private void ensureCacheCapacity(final InternalProcessorContext<?, ?> context) {$/,/^    }$/d' "$CACHING_KV"
