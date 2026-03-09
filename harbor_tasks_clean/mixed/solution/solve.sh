#!/bin/bash
# Gold patch: revert all 4 injected concurrency bugs in Kafka Streams.

cd /app

STREAMS_SRC="streams/src/main/java/org/apache/kafka/streams"
INTERNALS="$STREAMS_SRC/processor/internals"

# ============================================================
# Bug A: CachingWindowStore ABBA cycle
# Remove recordFlushCompletion call and 3 injected methods
# ============================================================
CWS="$STREAMS_SRC/state/internals/CachingWindowStore.java"

python3 -c "
import re, sys
with open('$CWS') as f:
    src = f.read()

# Remove the call to recordFlushCompletion in putAndMaybeForward
src = src.replace('        recordFlushCompletion(binaryKey, entry.entry().context().timestamp());\n', '')

# Remove recordFlushCompletion method
src = re.sub(r'\n    private void recordFlushCompletion\(final Bytes key, final long timestamp\) \{[^}]*\}\n', '\n', src)

# Remove verifyPersistedWindow method (has try-with-resources, need multi-line)
src = re.sub(r'\n    private void verifyPersistedWindow\(final Bytes key, final long timestamp\) \{.*?\n    \}\n', '\n', src, flags=re.DOTALL)

# Remove fetchPersistedRange method
src = re.sub(r'\n    private WindowStoreIterator<byte\[\]> fetchPersistedRange\(final Bytes key, final long timestamp\) \{[^}]*\}\n', '\n', src)

with open('$CWS', 'w') as f:
    f.write(src)
print('Bug A fixed: removed injected callback chain from CachingWindowStore')
"

# ============================================================
# Bug B: KafkaStreams setState listener-under-lock
# Move stateListener.onChange() outside synchronized(stateLock)
# ============================================================
KS="$STREAMS_SRC/KafkaStreams.java"

python3 -c "
with open('$KS') as f:
    src = f.read()

# Find and remove the onChange block from inside synchronized(stateLock)
old_block = '''            // Ensure atomic state transition and notification
            if (stateListener != null) {
                stateListener.onChange(newState, oldState);
            }
        }

        return true;'''

new_block = '''        }

        if (stateListener != null) {
            stateListener.onChange(newState, oldState);
        }

        return true;'''

if old_block in src:
    src = src.replace(old_block, new_block)
    with open('$KS', 'w') as f:
        f.write(src)
    print('Bug B fixed: moved onChange outside synchronized(stateLock)')
else:
    print('Bug B: pattern not found, skipping')
"

# ============================================================
# Bug C: TopologyMetadata ↔ DefaultTaskManager observer cycle
# Remove observer pattern from TopologyMetadata and DefaultTaskManager
# ============================================================
TM="$INTERNALS/TopologyMetadata.java"
DTM="$INTERNALS/tasks/DefaultTaskManager.java"

# TopologyMetadata: remove all injected observer code
python3 -c "
import re
with open('$TM') as f:
    src = f.read()

# Remove lifecycleObservers field
src = src.replace('    private final List<TopologyLifecycleObserver> lifecycleObservers = new ArrayList<>();\n', '')

# Remove interface
src = re.sub(r'\n    public interface TopologyLifecycleObserver \{[^}]*\}\n', '\n', src)

# Remove registerLifecycleObserver method
src = re.sub(r'\n    public void registerLifecycleObserver\(final TopologyLifecycleObserver observer\) \{.*?\n    \}\n', '\n', src, flags=re.DOTALL)

# Remove notifyLifecycleObservers method
src = re.sub(r'\n    private void notifyLifecycleObservers\(\) \{.*?\n    \}\n', '\n', src, flags=re.DOTALL)

# Remove notifyLifecycleObservers() calls
src = src.replace('            notifyLifecycleObservers();\n', '')

with open('$TM', 'w') as f:
    f.write(src)
print('Bug C (TopologyMetadata): removed observer pattern')
"

# DefaultTaskManager: remove observer registration and inner class
python3 -c "
import re
with open('$DTM') as f:
    src = f.read()

# Remove registerLifecycleObserver call
src = src.replace('        topologyMetadata.registerLifecycleObserver(new TaskTopologyObserver());\n', '')

# Remove TaskTopologyObserver inner class
src = re.sub(r'\n    private class TaskTopologyObserver implements TopologyMetadata\.TopologyLifecycleObserver \{.*?\n    \}\n', '\n', src, flags=re.DOTALL)

# Remove validateTopologyConsistency method
src = re.sub(r'\n    private void validateTopologyConsistency\(final Set<String> activeTopologies\) \{.*?\n    \}\n', '\n', src, flags=re.DOTALL)

with open('$DTM', 'w') as f:
    f.write(src)
print('Bug C (DefaultTaskManager): removed observer and validation')
"

# ============================================================
# Bug D: DefaultStateUpdater missed signal
# Remove topology version check, requeueTaskForRestoration, and field
# ============================================================
DSU="$INTERNALS/DefaultStateUpdater.java"

python3 -c "
import re
with open('$DSU') as f:
    src = f.read()

# Remove the topology version check block in maybeCompleteRestoration
old_block = '''                    if (topologyMetadata.topologyVersion() > restorationStartTopologyVersion) {
                        log.info(\"Topology version changed during restoration of task {}, re-queuing\", task.id());
                        changelogReader.unregister(changelogPartitions);
                        requeueTaskForRestoration(task);
                        return;
                    }
'''
src = src.replace(old_block, '')

# Remove requeueTaskForRestoration method
src = re.sub(r'\n        private void requeueTaskForRestoration\(final StreamTask task\) \{.*?\n        \}\n', '\n', src, flags=re.DOTALL)

# Remove restorationStartTopologyVersion field
src = src.replace('    private volatile long restorationStartTopologyVersion = -1L;\n', '')

# Remove restorationStartTopologyVersion initialization
src = src.replace('            restorationStartTopologyVersion = topologyMetadata.topologyVersion();\n', '')

with open('$DSU', 'w') as f:
    f.write(src)
print('Bug D fixed: removed topology version check and missed signal path')
"

echo "Gold patch applied: all 4 concurrency bugs fixed."
