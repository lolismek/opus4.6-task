#!/bin/bash
# Gold patch: revert the injected deadlock in CachingWindowStore and NamedCache.
#
# CachingWindowStore.java:
#   - Remove the recordFlushCompletion() call at the end of putAndMaybeForward()
#   - Remove 3 helper methods: recordFlushCompletion, verifyPersistedWindow, fetchPersistedRange
#
# NamedCache.java:
#   - Remove the flush-before-close block in close() (red herring)

cd /app

CACHING_STORE="streams/src/main/java/org/apache/kafka/streams/state/internals/CachingWindowStore.java"
NAMED_CACHE="streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java"

# --- Fix CachingWindowStore.java ---

# 1. Remove the recordFlushCompletion call at end of putAndMaybeForward
sed -i '/recordFlushCompletion(binaryKey, windowStartTimestamp);/d' "$CACHING_STORE"

# 2. Remove the 3 injected helper methods
#    Each method starts at 4-space indent and ends with "    }" (4 spaces + brace)
#    sed range /start/,/end/ deletes from first match of start to first match of end after it
sed -i '/^    private void recordFlushCompletion(/,/^    }$/d' "$CACHING_STORE"
sed -i '/^    private void verifyPersistedWindow(/,/^    }$/d' "$CACHING_STORE"
sed -i '/^    private WindowStoreIterator<byte\[\]> fetchPersistedRange(/,/^    }$/d' "$CACHING_STORE"

# 3. Clean up any leftover blank lines (3+ consecutive blank lines -> 2)
sed -i '/^$/N;/^\n$/N;/^\n\n$/d' "$CACHING_STORE"

# --- Fix NamedCache.java ---

# Remove the flush-before-close block in close() method (comment + if/try/catch block)
# The block starts with the comment and ends with "        }" (8-space closing brace of the if)
sed -i '/Flush remaining dirty entries before teardown/,/^        }$/d' "$NAMED_CACHE"
