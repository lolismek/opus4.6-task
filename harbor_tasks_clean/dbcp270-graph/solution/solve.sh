#!/bin/bash
# Gold patch: revert the injected DBCP-270 deadlock by removing the reverse
# lock edge (NamedCache -> ThreadCache callback chain).
#
# Strategy: instead of replacing the 2-arg delegating constructor with the
# original body, we delete the delegating constructor, then rename the 3-arg
# constructor to 2-arg and remove the extra parentCache assignment.

cd /app

NAMED_CACHE="streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java"
THREAD_CACHE="streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java"

# --- Fix NamedCache.java ---

# 1. Remove the parentCache field
sed -i '/private final ThreadCache parentCache;/d' "$NAMED_CACHE"

# 2. Delete the 2-arg delegating constructor (opening brace, delegation call, closing brace)
sed -i '/^    NamedCache(final String name, final StreamsMetricsImpl streamsMetrics) {$/,/^    }$/d' "$NAMED_CACHE"

# 3. Convert 3-arg constructor to 2-arg (remove extra parameter)
sed -i 's/NamedCache(final String name, final StreamsMetricsImpl streamsMetrics, final ThreadCache parentCache)/NamedCache(final String name, final StreamsMetricsImpl streamsMetrics)/' "$NAMED_CACHE"

# 4. Remove parentCache assignment from constructor body
sed -i '/this\.parentCache = parentCache;/d' "$NAMED_CACHE"

# 5. Delete validateNamespaceActive method
sed -i '/^    private void validateNamespaceActive() {$/,/^    }$/d' "$NAMED_CACHE"

# 6. Delete checkNamespaceRegistration method
sed -i '/^    private boolean checkNamespaceRegistration() {$/,/^    }$/d' "$NAMED_CACHE"

# 7. Remove the validateNamespaceActive() call in flush(LRUNode)
sed -i '/^        validateNamespaceActive();$/d' "$NAMED_CACHE"

# --- Fix ThreadCache.java ---

# 8. Revert getOrCreateCache to use 2-arg NamedCache constructor
sed -i 's/cache = new NamedCache(name, this\.metrics, this);/cache = new NamedCache(name, this.metrics);/' "$THREAD_CACHE"

# 9. Delete isNamespaceRegistered bridge method
sed -i '/^    boolean isNamespaceRegistered(final String namespace) {$/,/^    }$/d' "$THREAD_CACHE"

echo "Gold patch applied: removed DBCP-270 deadlock injection"
