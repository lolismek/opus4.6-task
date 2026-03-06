# Lock Graph Summary: streams

**Mode**: both
**Java files scanned**: 46
**Files with locks**: 44
**Total lock acquisitions**: 360
**Lock order edges**: 94

## Lock Hotspots

| Class | Lock Acquisitions | File |
|-------|------------------|------|
| Topology | 38 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/Topology.java |
| NamedCache | 24 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java |
| KafkaStreams | 23 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java |
| DefaultStateUpdater | 22 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java |
| StreamsBuilder | 18 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/StreamsBuilder.java |
| StreamsMetricsImpl | 18 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/metrics/StreamsMetricsImpl.java |
| RocksDBStore | 18 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBStore.java |
| InternalTopologyBuilder | 16 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/InternalTopologyBuilder.java |
| CachingKeyValueStore | 16 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java |
| SynchronizedPartitionGroup | 14 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/SynchronizedPartitionGroup.java |
| Tasks | 13 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/Tasks.java |
| LogicalKeyValueSegment | 13 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java |
| InMemoryKeyValueStore | 12 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/InMemoryKeyValueStore.java |
| StateUpdaterThread | 10 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java |
| ThreadCache | 10 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java |
| StreamsMetadataState | 8 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/StreamsMetadataState.java |
| StateDirectory | 7 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/StateDirectory.java |
| MemoryLRUCache | 7 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/MemoryLRUCache.java |
| TimeOrderedCachingWindowStore | 5 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/TimeOrderedCachingWindowStore.java |
| CachingWindowStore | 5 | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingWindowStore.java |

## Lock Order Edges

| From Lock | To Lock | Class.Method | Mechanism | Source | File:Line |
|-----------|---------|-------------|-----------|--------|-----------|
| KafkaStreams.this | KafkaStreams.changeThreadCount | KafkaStreams.clientInstanceIds | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1868 |
| DefaultStateUpdater.tasksAndActionsLock | DefaultStateUpdater.restoredActiveTasksLock | DefaultStateUpdater.executeWithQueuesLocked | lock_under_lock | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java:1071 |
| DefaultStateUpdater.tasksAndActionsLock | DefaultStateUpdater.exceptionsAndFailedTasksLock | DefaultStateUpdater.executeWithQueuesLocked | lock_under_lock | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java:1072 |
| DefaultStateUpdater.restoredActiveTasksLock | DefaultStateUpdater.exceptionsAndFailedTasksLock | DefaultStateUpdater.executeWithQueuesLocked | lock_under_lock | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java:1072 |
| CachingKeyValueStore.position | CachingKeyValueStore.wrappedPosition | CachingKeyValueStore.getPosition | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java:119 |
| MemoryLRUCache.this | MemoryLRUCache.position | MemoryLRUCache.put | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/MemoryLRUCache.java:140 |
| MemoryLRUCache.this | MemoryLRUCache.position | MemoryLRUCache.delete | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/MemoryLRUCache.java:170 |
| RocksDBStore.this | RocksDBStore.position | RocksDBStore.put | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBStore.java:417 |
| LogicalKeyValueSegment.this | LogicalKeyValueSegment.openIterators | LogicalKeyValueSegment.close | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:150 |
| ThreadCache.this | ThreadCache.cache | ThreadCache.resize | nested_synchronized | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:91 |
| StreamsBuilder.this | InternalStreamsBuilder.this | StreamsBuilder.addStateStore | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/StreamsBuilder.java:530 |
| StreamsBuilder.this | InternalStreamsBuilder.this | StreamsBuilder.addGlobalStore | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/StreamsBuilder.java:567 |
| KafkaStreams.this | KafkaStreams.stateLock | KafkaStreams.onChange | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:658 |
| KafkaStreams.changeThreadCount | KafkaStreams.threads | KafkaStreams.addStreamThread | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1119 |
| KafkaStreams.stateLock | KafkaStreams.threads | KafkaStreams.addStreamThread | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1138 |
| KafkaStreams.changeThreadCount | KafkaStreams.threads | KafkaStreams.removeStreamThread | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1198 |
| KafkaStreams.this | KafkaStreams.stateLock | KafkaStreams.start | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1390 |
| KafkaStreams.this | GlobalStreamThread.this | KafkaStreams.start | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1397 |
| KafkaStreams.this | StateDirectory.this | KafkaStreams.start | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:1408 |
| GlobalStreamThread.this | GlobalStreamThread.stateLock | GlobalStreamThread.start | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/processor/internals/GlobalStreamThread.java:467 |
| CachingKeyValueStore.lock | CachingKeyValueStore.position | CachingKeyValueStore.query | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java:150 |
| CachingKeyValueStore.lock | CachingKeyValueStore.wrappedPosition | CachingKeyValueStore.query | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java:150 |
| CachingKeyValueStore.lock | CachingKeyValueStore.position | CachingKeyValueStore.put | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java:269 |
| CachingKeyValueStore.lock | CachingKeyValueStore.position | CachingKeyValueStore.putIfAbsent | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java:308 |
| MemoryLRUCache.position | MemoryLRUCache.this | MemoryLRUCache.init | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/MemoryLRUCache.java:101 |
| MemoryLRUCache.position | MemoryLRUCache.this | MemoryLRUCache.put | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/MemoryLRUCache.java:142 |
| MemoryLRUCache.this | MemoryLRUCache.position | MemoryLRUCache.putIfAbsent | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/MemoryLRUCache.java:155 |
| RocksDBStore.this | RocksDBStore.position | RocksDBStore.putIfAbsent | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBStore.java:429 |
| RocksDBStore.this | RocksDBStore.position | RocksDBStore.delete | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBStore.java:525 |
| RocksDBStore.this | RocksDBStore.openIterators | RocksDBStore.close | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/RocksDBStore.java:709 |
| InMemoryKeyValueStore.position | InMemoryKeyValueStore.this | InMemoryKeyValueStore.init | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/InMemoryKeyValueStore.java:87 |
| InMemoryKeyValueStore.this | InMemoryKeyValueStore.position | InMemoryKeyValueStore.put | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/InMemoryKeyValueStore.java:140 |
| InMemoryKeyValueStore.this | InMemoryKeyValueStore.position | InMemoryKeyValueStore.putAll | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/InMemoryKeyValueStore.java:168 |
| LogicalKeyValueSegment.this | RocksDBStore.this | LogicalKeyValueSegment.put | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:105 |
| LogicalKeyValueSegment.this | RocksDBStore.position | LogicalKeyValueSegment.put | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:105 |
| LogicalKeyValueSegment.this | RocksDBStore.this | LogicalKeyValueSegment.putIfAbsent | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:112 |
| LogicalKeyValueSegment.this | RocksDBStore.position | LogicalKeyValueSegment.putAll | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:119 |
| LogicalKeyValueSegment.this | RocksDBStore.this | LogicalKeyValueSegment.delete | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:128 |
| LogicalKeyValueSegment.this | RocksDBStore.this | LogicalKeyValueSegment.get | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:185 |
| LogicalKeyValueSegment.this | RocksDBStore.this | LogicalKeyValueSegment.range | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/LogicalKeyValueSegment.java:213 |
| ThreadCache.this | NamedCache.this | ThreadCache.resize | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:92 |
| ThreadCache.cache | NamedCache.this | ThreadCache.resize | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:92 |
| ThreadCache.cache | NamedCache.this | ThreadCache.flush | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:147 |
| ThreadCache.cache | NamedCache.this | ThreadCache.put | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:177 |
| ThreadCache.cache | NamedCache.this | ThreadCache.putIfAbsent | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:188 |
| ThreadCache.cache | NamedCache.this | ThreadCache.delete | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java:214 |
| NamedCache.this | StreamsMetricsImpl.cacheLevelSensors | NamedCache.close | call_to_locking_method | treesitter | tasks/kafka-deadlock/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java:362 |
| *(($bcvar1:java.lang.Object*)) | *(($bcvar1:java.lang.Object*))->telemetryProvider | AbstractCoordinator$JoinGroupResponseHandler$Lambda$_3_232.accept | infer_starvation | infer | :118 |
| AppInfoParser.class | Metrics.this | AppInfoParser.registerAppInfo | infer_starvation | infer | :588 |
| AppInfoParser.class | Metrics.this | AppInfoParser.unregisterAppInfo | infer_starvation | infer | :549 |
| AppInfoParser.class | AsyncKafkaConsumer.this | AsyncKafkaConsumer.close | infer_starvation | infer | :549 |
| AppInfoParser.class | AsyncKafkaConsumer.this | AsyncKafkaConsumer.<init> | infer_starvation | infer | :588 |
| AppInfoParser.class | ClassicKafkaConsumer.this | ClassicKafkaConsumer.<init> | infer_starvation | infer | :588 |
| AppInfoParser.class | ClassicKafkaConsumer.this | ClassicKafkaConsumer.close | infer_starvation | infer | :549 |
| AppInfoParser.class | Metrics.this | KafkaAdminClient.<init> | infer_starvation | infer | :588 |
| AppInfoParser.class | KafkaProducer.this | KafkaProducer.close | infer_starvation | infer | :549 |
| AppInfoParser.class | KafkaProducer.this | KafkaProducer.<init> | infer_starvation | infer | :588 |
| Metadata.this | MetadataResponse.this | Metadata.update | infer_starvation | infer | :217 |
| Metadata.this | MetadataResponse.this | Metadata.updateWithCurrentRequestVersion | infer_starvation | infer | :217 |
| NetworkClient.this | MetadataResponse.this | NetworkClient$DefaultMetadataUpdater.handleSuccessfulResponse | infer_starvation | infer | :217 |
| PositionsValidator.this | ApiVersions.this | internals.PositionsValidator$Lambda$_7_29 | infer_starvation | infer | :57 |
| PositionsValidator.this | ApiVersions.this | PositionsValidator.lambda$validatePositionsOnMetadataChange$0 | infer_starvation | infer | :57 |
| ProducerMetadata.this | MetadataResponse.this | ProducerMetadata.update | infer_starvation | infer | :217 |
| *(($bcvar1:java.lang.Object*)) | *(($bcvar1:java.lang.Object*))->telemetryProvider | RequestManagers$2$Lambda$_3_117.accept | infer_starvation | infer | :118 |
| AppInfoParser.class | ShareConsumerImpl.this | ShareConsumerImpl.<init> | infer_starvation | infer | :588 |
| AppInfoParser.class | ShareConsumerImpl.this | ShareConsumerImpl.close | infer_starvation | infer | :549 |
| SubscriptionState.this | ApiVersions.this | SubscriptionState.maybeValidatePositionForCurrentLeader | infer_starvation | infer | :57 |
| AppInfoParser.class | ConsumerWrapper.this | ConsumerWrapper.close | infer_starvation | infer | :549 |
| KafkaStreams.this | AppInfoParser.class | streams.KafkaStreams$Lambda$_82_25 | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.lambda$new$11 | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | streams.KafkaStreams$Lambda$_10_502 | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | streams.KafkaStreams$Lambda$_10_617 | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams$Lambda$_82_25.accept | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.addStreamThread | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.lambda$setUncaughtExceptionHandler$0 | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | streams.KafkaStreams$Lambda$_82_57 | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams$Lambda$_10_617.accept | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.replaceStreamThread | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams$Lambda$_82_57.accept | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams$Lambda$_10_502.accept | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.handleStreamsUncaughtException | infer_starvation | infer | :61 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.lambda$setUncaughtExceptionHandler$2 | infer_starvation | infer | :61 |
| *(($bcvar1:java.lang.Object*))->streamsMetrics->threadLevelMetrics | *(($bcvar1:java.lang.Object*))->streamsMetrics->metrics | KafkaStreams$Lambda$_58_2.accept | infer_starvation | infer | :549 |
| *(($bcvar1:java.lang.Object*))->streamsMetrics->threadLevelSensors | *(($bcvar1:java.lang.Object*))->streamsMetrics->metrics | KafkaStreams$Lambda$_58_2.accept | infer_starvation | infer | :441 |
| KafkaStreams.this | AppInfoParser.class | KafkaStreams.lambda$new$13 | infer_starvation | infer | :61 |
| StreamsBuilder.this | StreamsBuilder.internalStreamsBuilder | StreamsBuilder.globalTable | infer_starvation | infer | :421 |
| StreamsBuilder.this | StreamsBuilder.internalStreamsBuilder | StreamsBuilder.table | infer_starvation | infer | :421 |
| *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->taskLevelSensors | *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->metrics | TaskManager.lambda$handleRestoringAndUpdatingTasks$4 | infer_starvation | infer | :441 |
| *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->nodeLevelSensors | *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->metrics | TaskManager.lambda$handleRestoringAndUpdatingTasks$4 | infer_starvation | infer | :441 |
| *(($bcvar3:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->taskLevelSensors | *(($bcvar3:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->metrics | internals.TaskManager$Lambda$_52_62 | infer_starvation | infer | :441 |
| *(($bcvar3:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->nodeLevelSensors | *(($bcvar3:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->metrics | internals.TaskManager$Lambda$_52_62 | infer_starvation | infer | :441 |
| *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->taskLevelSensors | *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->metrics | TaskManager.recycleTaskFromStateUpdater | infer_starvation | infer | :441 |
| *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->nodeLevelSensors | *((task:org.apache.kafka.streams.processor.internals.Task*))->streamsMetrics->metrics | TaskManager.recycleTaskFromStateUpdater | infer_starvation | infer | :441 |
| TopologyMetadata.this | InternalTopologyBuilder.this | TopologyMetadata.registerAndBuildNewTopology | infer_starvation | infer | :434 |

## Detected Cycles

1. **MemoryLRUCache.position** ↔ **MemoryLRUCache.this**
2. **InMemoryKeyValueStore.position** ↔ **InMemoryKeyValueStore.this**

## Wait/Notify Risk

Classes with `wait()`/`await()` but no `notify()`/`signal()` in same class:

- GlobalStreamThread

## Candidate Insertion Points

- **Cycle**: MemoryLRUCache.this ↔ MemoryLRUCache.position (pattern: DERBY-5447, score: 0.70)
- **Cycle**: InMemoryKeyValueStore.position ↔ InMemoryKeyValueStore.this (pattern: DERBY-5447, score: 0.70)
- **Wait risk**: GlobalStreamThread (pattern: POOL-146, score: 0.25)
