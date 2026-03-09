#!/bin/bash
# Gold patch: revert the SYNTH-001 deadlock injection.
# Breaks the 3-node cycle A→B→C→A by removing all injected code.
# Strategy: remove the injected edges and the new AbstractStateUpdater superclass.

cd /app

STREAMS_SRC="streams/src/main/java/org/apache/kafka/streams"
INTERNALS="$STREAMS_SRC/processor/internals"

# ============================================================
# 1. KafkaStreams.java — Remove 4 injections
# ============================================================
KAFKA_STREAMS="$STREAMS_SRC/KafkaStreams.java"

# 1a. Remove TopologyChangeHandler inner class + refreshRestorationState method (lines 683-694)
sed -i '/^    private final class TopologyChangeHandler implements TopologyMetadata.TopologyLifecycleObserver {$/,/^    }$/d' "$KAFKA_STREAMS"
sed -i '/^    private synchronized void refreshRestorationState() {$/,/^    }$/d' "$KAFKA_STREAMS"

# 1b. Remove observer registration in constructor
sed -i '/topologyMetadata\.registerLifecycleObserver(new TopologyChangeHandler());/d' "$KAFKA_STREAMS"

# 1c. Remove snapshot invalidation in clientInstanceIds()
sed -i '/\/\/ Refresh restoration snapshots for consistent telemetry/d' "$KAFKA_STREAMS"
sed -i '/processStreamThread(StreamThread::invalidateRestorationSnapshot);/d' "$KAFKA_STREAMS"

# ============================================================
# 2. DefaultStateUpdater.java — Remove 3 injections
# ============================================================
DSU="$INTERNALS/DefaultStateUpdater.java"

# 2a. Revert class declaration: extends AbstractStateUpdater → implements StateUpdater
sed -i 's/public class DefaultStateUpdater extends AbstractStateUpdater {/public class DefaultStateUpdater implements StateUpdater {/' "$DSU"

# 2b. Remove maybeRefreshRestorationSnapshot() call in runOnce()
sed -i '/maybeRefreshRestorationSnapshot();/d' "$DSU"

# 2c. Remove maybeRefreshRestorationSnapshot() method definition
sed -i '/^        private void maybeRefreshRestorationSnapshot() {$/,/^        }$/d' "$DSU"

# 2d. Remove invalidateRestorationSnapshot() public method
sed -i '/^    public void invalidateRestorationSnapshot() {$/,/^    }$/d' "$DSU"

# ============================================================
# 3. TopologyMetadata.java — Remove 6 injections
# ============================================================
TM="$INTERNALS/TopologyMetadata.java"

# 3a. Remove lifecycleObservers field
sed -i '/private final List<TopologyLifecycleObserver> lifecycleObservers = new ArrayList<>();/d' "$TM"

# 3b. Remove TopologyLifecycleObserver interface
sed -i '/^    public interface TopologyLifecycleObserver {$/,/^    }$/d' "$TM"

# 3c. Remove registerLifecycleObserver() method
sed -i '/^    public void registerLifecycleObserver(final TopologyLifecycleObserver observer) {$/,/^    }$/d' "$TM"

# 3d. Remove notifyLifecycleObservers() method
sed -i '/^    private void notifyLifecycleObservers() {$/,/^    }$/d' "$TM"

# 3e. Remove notifyLifecycleObservers() calls in registerAndBuildNewTopology and unregisterTopology
sed -i '/notifyLifecycleObservers();/d' "$TM"

# ============================================================
# 4. StreamThread.java — Remove 1 injection
# ============================================================
ST="$INTERNALS/StreamThread.java"

# Remove invalidateRestorationSnapshot() method
sed -i '/^    public void invalidateRestorationSnapshot() {$/,/^    }$/d' "$ST"

# ============================================================
# 5. Delete AbstractStateUpdater.java (new file, not part of original)
# ============================================================
rm -f "$INTERNALS/AbstractStateUpdater.java"

echo "Gold patch applied: SYNTH-001 deadlock injection reverted."
