# Lock Graph Summary: zookeeper-server

**Mode**: light
**Java files scanned**: 70
**Files with locks**: 67
**Total lock acquisitions**: 469
**Lock order edges**: 401

## Lock Hotspots

| Class | Lock Acquisitions | File |
|-------|------------------|------|
| ZKWatchManager | 38 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java |
| DataTree | 34 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java |
| Leader | 34 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java |
| QuorumPeer | 29 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java |
| BlueThrottle | 18 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/BlueThrottle.java |
| WatchManager | 14 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java |
| ServerCnxn | 12 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ServerCnxn.java |
| ClientCnxn | 11 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java |
| ObserverMaster | 11 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java |
| ZooKeeperServer | 9 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java |
| ControllableConnectionFactory | 9 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/controller/ControllableConnectionFactory.java |
| WatcherCleaner | 9 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java |
| QuorumCnxManager | 9 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java |
| UnifiedServerSocket | 9 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/UnifiedServerSocket.java |
| SessionTrackerImpl | 8 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java |
| DataNode | 8 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java |
| PrepRequestProcessor | 8 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java |
| FileTxnLog | 8 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/persistence/FileTxnLog.java |
| WatchManagerOptimized | 8 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java |
| LearnerHandler | 8 | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java |

## Lock Order Edges

| From Lock | To Lock | Class.Method | Mechanism | Source | File:Line |
|-----------|---------|-------------|-----------|--------|-----------|
| this | Login.class | Login.reLogin | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/Login.java:442 |
| parent | list | DataTree.createNode | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:492 |
| parent | nodes | DataTree.deleteNode | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:586 |
| zks.outstandingChanges | n | PrepRequestProcessor.getRecordForPath | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:170 |
| this | cleanEvent | WatcherCleaner.addDeadWatcher | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:118 |
| this | processingCompletedEvent | WatcherCleaner.run | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:168 |
| this | self.QV_LOCK | QuorumCnxManager.connectOne | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:750 |
| outerLockObject | QV_LOCK | QuorumPeer.setLastSeenQuorumVerifier | nested_synchronized | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1994 |
| pendingQueue | this | ClientCnxnSocketNIO.doIO | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNIO.java:127 |
| pendingQueue | rwLock | ClientCnxnSocketNIO.doIO | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNIO.java:127 |
| Login.class | this | Login.reLogin | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/Login.java:446 |
| this | lock | Login.logout | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/Login.java:461 |
| waitingEvents | this | ClientCnxn.queuePacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:542 |
| waitingEvents | rwLock | ClientCnxn.queuePacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:542 |
| waitingEvents | lock | ClientCnxn.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:570 |
| waitingEvents | this | ClientCnxn.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:570 |
| pendingQueue | lock | ClientCnxn.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:936 |
| pendingQueue | this | ClientCnxn.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:936 |
| pendingQueue | rwLock | ClientCnxn.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:936 |
| pendingQueue | addRemovePathRWLock | ClientCnxn.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:939 |
| outgoingQueue | pendingQueue | ClientCnxn.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1307 |
| pendingQueue | lock | ClientCnxn.cleanup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1379 |
| pendingQueue | writeLock | ClientCnxn.cleanup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1379 |
| outgoingQueue | this | ClientCnxn.queuePacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1674 |
| outgoingQueue | rwLock | ClientCnxn.queuePacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1674 |
| waitingEvents | this | EventThread.queuePacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:542 |
| waitingEvents | rwLock | EventThread.queuePacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:542 |
| waitingEvents | lock | EventThread.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:570 |
| waitingEvents | this | EventThread.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:570 |
| pendingQueue | lock | SendThread.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:936 |
| pendingQueue | this | SendThread.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:936 |
| pendingQueue | rwLock | SendThread.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:936 |
| pendingQueue | addRemovePathRWLock | SendThread.readResponse | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:939 |
| outgoingQueue | pendingQueue | SendThread.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1307 |
| pendingQueue | lock | SendThread.cleanup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1379 |
| pendingQueue | writeLock | SendThread.cleanup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxn.java:1379 |
| dataWatches | lock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:364 |
| dataWatches | writeLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:364 |
| existWatches | lock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:373 |
| existWatches | writeLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:373 |
| childWatches | lock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:382 |
| childWatches | writeLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:382 |
| dataWatches | this | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:402 |
| dataWatches | rwLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:402 |
| dataWatches | addRemovePathRWLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:402 |
| existWatches | this | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:405 |
| existWatches | rwLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:405 |
| existWatches | addRemovePathRWLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:405 |
| childWatches | this | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:411 |
| childWatches | rwLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:411 |
| childWatches | addRemovePathRWLock | ZKWatchManager.materialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:411 |
| persistentWatches | rwLock | ZKWatchManager.addPersistentWatches | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:447 |
| persistentRecursiveWatches | rwLock | ZKWatchManager.addPersistentWatches | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZKWatchManager.java:458 |
| connectLock | this | ClientCnxnSocketNetty.connect | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:155 |
| connectLock | lock | ClientCnxnSocketNetty.connect | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:163 |
| connectLock | writeLock | ClientCnxnSocketNetty.connect | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:163 |
| connectLock | this | ClientCnxnSocketNetty.operationComplete | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:155 |
| connectLock | lock | ClientCnxnSocketNetty.operationComplete | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:163 |
| connectLock | writeLock | ClientCnxnSocketNetty.operationComplete | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:163 |
| connectLock | this | ClientCnxnSocketNetty.cleanup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:206 |
| pendingQueue | this | ClientCnxnSocketNetty.doWrite | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:362 |
| pendingQueue | rwLock | ClientCnxnSocketNetty.doWrite | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ClientCnxnSocketNetty.java:362 |
| watches | rwLock | ZooKeeper.register | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java:291 |
| watches | this | ZooKeeper.register | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java:296 |
| this | packet | ZooKeeper.whoAmI | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java:3205 |
| watches | rwLock | WatchRegistration.register | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java:291 |
| watches | this | WatchRegistration.register | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java:296 |
| lock | this | CircularBlockingQueue.offer | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/util/CircularBlockingQueue.java:81 |
| lock | rwLock | CircularBlockingQueue.offer | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/util/CircularBlockingQueue.java:81 |
| lock | addRemovePathRWLock | CircularBlockingQueue.offer | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/util/CircularBlockingQueue.java:82 |
| lock | this | CircularBlockingQueue.isEmpty | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/util/CircularBlockingQueue.java:132 |
| lock | this | CircularBlockingQueue.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/util/CircularBlockingQueue.java:143 |
| lock | rwLock | CircularBlockingQueue.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/util/CircularBlockingQueue.java:143 |
| this | rwLock | RequestThrottler.throttleSleep | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/RequestThrottler.java:201 |
| this | rwLock | ReferenceCountedACLCache.convertAcls | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:65 |
| this | rwLock | ReferenceCountedACLCache.convertLong | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:90 |
| this | rwLock | ReferenceCountedACLCache.addUsage | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:178 |
| this | rwLock | ReferenceCountedACLCache.removeUsage | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:196 |
| this | addRemovePathRWLock | ReferenceCountedACLCache.removeUsage | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:198 |
| this | rwLock | ReferenceCountedACLCache.purgeUnused | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:208 |
| this | addRemovePathRWLock | ReferenceCountedACLCache.purgeUnused | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ReferenceCountedACLCache.java:210 |
| n | this | SnapshotRecursiveSummary.printZnode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SnapshotRecursiveSummary.java:93 |
| this | rwLock | SessionTrackerImpl.getSessionExpiryMap | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:141 |
| this | rwLock | SessionTrackerImpl.touchSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:180 |
| this | ZooTrace.class | SessionTrackerImpl.setSessionClosing | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:226 |
| this | rwLock | SessionTrackerImpl.setSessionClosing | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:230 |
| this | rwLock | SessionTrackerImpl.removeSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:239 |
| this | addRemovePathRWLock | SessionTrackerImpl.removeSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:239 |
| this | ZooTrace.class | SessionTrackerImpl.removeSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:241 |
| this | rwLock | SessionTrackerImpl.trackSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:271 |
| this | ZooTrace.class | SessionTrackerImpl.trackSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:287 |
| this | rwLock | SessionTrackerImpl.checkSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:310 |
| this | rwLock | SessionTrackerImpl.setOwner | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTrackerImpl.java:328 |
| parent | n | DataTree.createNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:444 |
| parent | node | DataTree.createNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:444 |
| parent | this | DataTree.createNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:457 |
| parent | rwLock | DataTree.createNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:459 |
| list | this | DataTree.createNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:493 |
| list | rwLock | DataTree.createNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:493 |
| parent | this | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:548 |
| node | n | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:565 |
| node | this | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:566 |
| parent | n | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:575 |
| parent | node | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:575 |
| parent | rwLock | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:577 |
| parent | addRemovePathRWLock | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:579 |
| nodes | this | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:587 |
| nodes | rwLock | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:587 |
| nodes | addRemovePathRWLock | DataTree.deleteNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:587 |
| n | node | DataTree.setData | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:638 |
| n | this | DataTree.setData | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:645 |
| n | this | DataTree.getData | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:698 |
| n | addRemovePathRWLock | DataTree.getData | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:700 |
| n | this | DataTree.statNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:718 |
| n | this | DataTree.getChildren | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:732 |
| n | addRemovePathRWLock | DataTree.getChildren | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:737 |
| n | this | DataTree.setACL | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:766 |
| n | this | DataTree.getACL | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:783 |
| node | this | DataTree.getACL | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:791 |
| node | n | DataTree.getCounts | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1206 |
| node | this | DataTree.getCounts | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1206 |
| node | n | DataTree.traverseNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1251 |
| node | this | DataTree.traverseNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1251 |
| node | n | DataTree.serializeNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1307 |
| node | this | DataTree.serializeNode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1307 |
| node | this | DataTree.deserialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1355 |
| this | watchers | DataTree.dumpWatches | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1409 |
| this | watchers | DataTree.getWatchesByPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1429 |
| digestLog | this | DataTree.logZxidDigest | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1651 |
| digestLog | rwLock | DataTree.logZxidDigest | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1651 |
| digestLog | lock | DataTree.logZxidDigest | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:1652 |
| this | rwLock | DataNode.addChild | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java:106 |
| this | rwLock | DataNode.removeChild | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java:119 |
| this | addRemovePathRWLock | DataNode.removeChild | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java:119 |
| this | lock | DataNode.copyStat | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java:157 |
| this | rwLock | DataNode.copyStat | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java:157 |
| this | node | DataNode.deserialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java:179 |
| n | this | SnapshotFormatter.printZnode | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SnapshotFormatter.java:146 |
| n | this | SnapshotFormatter.printZnodeJson | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/SnapshotFormatter.java:221 |
| this | accurateMode ? requestPathStats : new Object() | ZooKeeperServer.startup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:812 |
| this | lock | ZooKeeperServer.shutdown | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:947 |
| outstandingChanges | fzk | ZooKeeperServer.processTxn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:1868 |
| outstandingChanges | lock | ZooKeeperServer.processTxn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:1869 |
| outstandingChanges | this | ZooKeeperServer.processTxn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:1869 |
| outstandingChanges | rwLock | ZooKeeperServer.processTxn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:1871 |
| outstandingChanges | addRemovePathRWLock | ZooKeeperServer.processTxn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZooKeeperServer.java:1871 |
| zks.outstandingChanges | rwLock | PrepRequestProcessor.getRecordForPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:165 |
| zks.outstandingChanges | this | PrepRequestProcessor.getRecordForPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:171 |
| zks.outstandingChanges | lock | PrepRequestProcessor.getRecordForPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:173 |
| n | this | PrepRequestProcessor.getRecordForPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:171 |
| zks.outstandingChanges | rwLock | PrepRequestProcessor.getOutstandingChange | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:191 |
| zks.outstandingChanges | this | PrepRequestProcessor.addChangeRecord | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:197 |
| zks.outstandingChanges | rwLock | PrepRequestProcessor.addChangeRecord | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:197 |
| zks.outstandingChanges | this | PrepRequestProcessor.rollbackPendingChanges | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:262 |
| zks.outstandingChanges | rwLock | PrepRequestProcessor.rollbackPendingChanges | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:264 |
| zks.outstandingChanges | addRemovePathRWLock | PrepRequestProcessor.rollbackPendingChanges | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:264 |
| zks.outstandingChanges | lock | PrepRequestProcessor.rollbackPendingChanges | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:274 |
| zks.outstandingChanges | ret | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:581 |
| zks.outstandingChanges | e.getValue() | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:581 |
| zks.outstandingChanges | this | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:585 |
| zks.outstandingChanges | rwLock | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:585 |
| zks.outstandingChanges | addRemovePathRWLock | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:585 |
| zks.outstandingChanges | n | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:593 |
| zks.outstandingChanges | fzk | PrepRequestProcessor.pRequest2Txn | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:594 |
| zks.outstandingChanges | lock | PrepRequestProcessor.getCurrentTreeDigest | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:1105 |
| zks.outstandingChanges | this | PrepRequestProcessor.getCurrentTreeDigest | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/PrepRequestProcessor.java:1105 |
| lock | writeLock | ZKDatabase.clear | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:185 |
| wl | lock | ZKDatabase.addCommittedProposal | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:326 |
| wl | this | ZKDatabase.addCommittedProposal | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:326 |
| wl | rwLock | ZKDatabase.addCommittedProposal | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:344 |
| wl | fzk | ZKDatabase.addCommittedProposal | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:345 |
| wl | addRemovePathRWLock | ZKDatabase.addCommittedProposal | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:347 |
| this | n | ZKDatabase.initConfigInZKDatabase | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/ZKDatabase.java:729 |
| outgoingBuffers | this | NIOServerCnxn.sendBuffer | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/NIOServerCnxn.java:154 |
| outgoingBuffers | rwLock | NIOServerCnxn.sendBuffer | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/NIOServerCnxn.java:154 |
| node | n | QuotaMetricsUtils.getQuotaLimitOrUsage | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/QuotaMetricsUtils.java:107 |
| node | this | QuotaMetricsUtils.getQuotaLimitOrUsage | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/QuotaMetricsUtils.java:107 |
| accurateMode ? requestPathStats : new Object() | lock | RequestPathMetricsCollector.start | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/RequestPathMetricsCollector.java:372 |
| accurateMode ? requestPathStats : new Object() | lock | PathStatsQueue.start | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/RequestPathMetricsCollector.java:372 |
| this | rwLock | CircularBuffer.write | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/CircularBuffer.java:59 |
| this | rwLock | CircularBuffer.peek | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/CircularBuffer.java:82 |
| this | rwLock | BitHashSet.add | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:72 |
| this | lock | BitHashSet.add | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:75 |
| this | rwLock | BitHashSet.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:95 |
| this | addRemovePathRWLock | BitHashSet.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:99 |
| this | lock | BitHashSet.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:100 |
| this | writeLock | BitHashSet.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:100 |
| this | rwLock | BitHashSet.contains | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:109 |
| this | lock | BitHashSet.cachedSize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:155 |
| this | rwLock | BitHashSet.cachedSize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitHashSet.java:155 |
| rwLock | lock | BitMap.add | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitMap.java:63 |
| rwLock | writeLock | BitMap.add | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitMap.java:63 |
| rwLock | this | BitMap.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitMap.java:105 |
| rwLock | addRemovePathRWLock | BitMap.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitMap.java:105 |
| rwLock | lock | BitMap.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitMap.java:133 |
| rwLock | this | BitMap.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/util/BitMap.java:133 |
| ProviderRegistry.class | lock | ProviderRegistry.reset | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/auth/ProviderRegistry.java:41 |
| ProviderRegistry.class | writeLock | ProviderRegistry.reset | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/auth/ProviderRegistry.java:41 |
| ProviderRegistry.class | FourLetterCommands.class | ProviderRegistry.initialize | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/auth/ProviderRegistry.java:50 |
| this | fzk | FileTxnLog.append | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/persistence/FileTxnLog.java:281 |
| this | rwLock | FileTxnLog.append | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/persistence/FileTxnLog.java:308 |
| this | lock | FileTxnLog.commit | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/persistence/FileTxnLog.java:424 |
| this | rwLock | FileTxnLog.commit | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/persistence/FileTxnLog.java:424 |
| addRemovePathRWLock | this | WatchManagerOptimized.addWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:90 |
| addRemovePathRWLock | rwLock | WatchManagerOptimized.addWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:90 |
| addRemovePathRWLock | rwLock | WatchManagerOptimized.removeWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:138 |
| addRemovePathRWLock | this | WatchManagerOptimized.removeWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:139 |
| addRemovePathRWLock | lock | WatchManagerOptimized.removeWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:142 |
| watchers | this | WatchManagerOptimized.triggerWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:227 |
| watchers | rwLock | WatchManagerOptimized.triggerWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:231 |
| addRemovePathRWLock | this | WatchManagerOptimized.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:270 |
| addRemovePathRWLock | rwLock | WatchManagerOptimized.remove | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:270 |
| watchers | lock | WatchManagerOptimized.getWatchesByPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:335 |
| watchers | this | WatchManagerOptimized.getWatchesByPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:335 |
| watchers | rwLock | WatchManagerOptimized.getWatchesByPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:335 |
| watchers | rwLock | WatchManagerOptimized.getWatcher2PathesMap | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:360 |
| watchers | this | WatchManagerOptimized.getWatcher2PathesMap | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:367 |
| watchers | rwLock | WatchManagerOptimized.dumpWatches | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManagerOptimized.java:382 |
| this | rwLock | WatcherCleaner.addDeadWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:114 |
| this | lock | WatcherCleaner.addDeadWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:117 |
| cleanEvent | lock | WatcherCleaner.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:133 |
| cleanEvent | this | WatcherCleaner.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:133 |
| cleanEvent | rwLock | WatcherCleaner.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:133 |
| this | lock | WatcherCleaner.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:155 |
| this | writeLock | WatcherCleaner.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:155 |
| this | rwLock | WatcherCleaner.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatcherCleaner.java:156 |
| this | lock | WatchManager.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:60 |
| this | rwLock | WatchManager.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:60 |
| this | rwLock | WatchManager.addWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:81 |
| this | rwLock | WatchManager.removeWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:114 |
| this | addRemovePathRWLock | WatchManager.removeWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:114 |
| this | lock | WatchManager.removeWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:122 |
| this | rwLock | WatchManager.triggerWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:146 |
| this | lock | WatchManager.triggerWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:147 |
| this | addRemovePathRWLock | WatchManager.triggerWatch | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:163 |
| this | lock | WatchManager.toString | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:223 |
| this | rwLock | WatchManager.toString | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:223 |
| this | rwLock | WatchManager.containsWatcher | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:264 |
| this | lock | WatchManager.getWatchesByPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:344 |
| this | rwLock | WatchManager.getWatchesByPath | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:344 |
| this | lock | WatchManager.getWatchesSummary | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:357 |
| this | rwLock | WatchManager.getWatchesSummary | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/watch/WatchManager.java:357 |
| FourLetterCommands.class | lock | FourLetterCommands.resetWhiteList | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/command/FourLetterCommands.java:163 |
| FourLetterCommands.class | writeLock | FourLetterCommands.resetWhiteList | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/command/FourLetterCommands.java:163 |
| FourLetterCommands.class | this | FourLetterCommands.isEnabled | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/command/FourLetterCommands.java:196 |
| FourLetterCommands.class | rwLock | FourLetterCommands.isEnabled | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/command/FourLetterCommands.java:205 |
| FourLetterCommands.class | lock | FourLetterCommands.isEnabled | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/command/FourLetterCommands.java:209 |
| this | rwLock | QuorumCnxManager.connectOne | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:716 |
| this | lock | QuorumCnxManager.connectOne | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:718 |
| this | QV_LOCK | QuorumCnxManager.connectOne | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:756 |
| self.QV_LOCK | QV_LOCK | QuorumCnxManager.connectOne | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:756 |
| self.QV_LOCK | this | QuorumCnxManager.connectOne | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:761 |
| self.QV_LOCK | rwLock | QuorumCnxManager.connectOne | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:761 |
| this | rwLock | QuorumCnxManager.finish | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:1217 |
| this | addRemovePathRWLock | QuorumCnxManager.finish | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:1217 |
| this | rwLock | QuorumCnxManager.send | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:1226 |
| this | rwLock | SendWorker.finish | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:1217 |
| this | addRemovePathRWLock | SendWorker.finish | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:1217 |
| this | rwLock | SendWorker.send | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumCnxManager.java:1226 |
| this | lock | ObserverZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverZooKeeperServer.java:116 |
| this | rwLock | ObserverZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverZooKeeperServer.java:116 |
| this | addRemovePathRWLock | ObserverZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverZooKeeperServer.java:121 |
| QV_LOCK | rwLock | QuorumPeer.getAddrs | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1070 |
| this | accurateMode ? requestPathStats : new Object() | QuorumPeer.start | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1197 |
| this | QV_LOCK | QuorumPeer.getCurrentAndNextConfigVoters | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1741 |
| this | learners | QuorumPeer.getQuorumPeers | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1764 |
| this | forwardingFollowers | QuorumPeer.getQuorumPeers | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1767 |
| this | rwLock | QuorumPeer.getQuorumPeers | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1770 |
| this | learners | QuorumPeer.restartLeaderElection | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:1948 |
| QV_LOCK | rwLock | QuorumPeer.setQuorumVerifier | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:2061 |
| this | QV_LOCK | QuorumPeer.initConfigInZKDatabase | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeer.java:2273 |
| forwardingFollowers | this | Leader.getNonVotingFollowers | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:212 |
| forwardingFollowers | rwLock | Leader.getNonVotingFollowers | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:212 |
| forwardingFollowers | this | Leader.addForwardingFollower | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:221 |
| forwardingFollowers | rwLock | Leader.addForwardingFollower | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:221 |
| forwardingFollowers | QV_LOCK | Leader.addForwardingFollower | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:225 |
| observingLearners | this | Leader.addObserverLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:242 |
| observingLearners | rwLock | Leader.addObserverLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:242 |
| observingLearners | this | Leader.getObservingLearnersInfo | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:250 |
| observingLearners | rwLock | Leader.getObservingLearnersInfo | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:250 |
| observingLearners | this | Leader.resetObserverConnectionStats | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:259 |
| this | lock | Leader.getNumPendingSyncs | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:268 |
| this | rwLock | Leader.getNumPendingSyncs | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:268 |
| learners | this | Leader.addLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:283 |
| learners | rwLock | Leader.addLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:283 |
| forwardingFollowers | this | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:295 |
| forwardingFollowers | rwLock | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:295 |
| forwardingFollowers | addRemovePathRWLock | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:295 |
| learners | this | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:298 |
| learners | rwLock | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:298 |
| learners | addRemovePathRWLock | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:298 |
| observingLearners | this | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:301 |
| observingLearners | rwLock | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:301 |
| observingLearners | addRemovePathRWLock | Leader.removeLearnerHandler | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:301 |
| forwardingFollowers | this | Leader.isLearnerSynced | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:307 |
| forwardingFollowers | this | Leader.isQuorumSynced | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:325 |
| forwardingFollowers | rwLock | Leader.isQuorumSynced | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:325 |
| this | QV_LOCK | Leader.lead | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:792 |
| this | learners | Leader.lead | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:800 |
| this | forwardingFollowers | Leader.lead | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:833 |
| learners | this | Leader.shutdown | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:886 |
| learners | rwLock | Leader.shutdown | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:887 |
| learners | addRemovePathRWLock | Leader.shutdown | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:887 |
| this | rwLock | Leader.tryToCommit | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:998 |
| this | addRemovePathRWLock | Leader.tryToCommit | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:998 |
| this | lock | Leader.tryToCommit | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1007 |
| this | QV_LOCK | Leader.tryToCommit | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1015 |
| this | ZooTrace.class | Leader.processAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1059 |
| this | fzk | Leader.processAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1062 |
| this | lock | Leader.processAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1077 |
| this | rwLock | Leader.processAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1077 |
| forwardingFollowers | waitingEvents | Leader.sendPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1198 |
| forwardingFollowers | outgoingQueue | Leader.sendPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1198 |
| this | QV_LOCK | Leader.propose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1317 |
| this | outerLockObject | Leader.propose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1320 |
| this | fzk | Leader.propose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1329 |
| this | forwardingFollowers | Leader.propose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1331 |
| this | lock | Leader.processSync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1344 |
| this | rwLock | Leader.processSync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1347 |
| this | fzk | Leader.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1372 |
| this | waitingEvents | Leader.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1375 |
| this | outgoingQueue | Leader.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1375 |
| this | rwLock | Leader.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1389 |
| this | forwardingFollowers | Leader.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1394 |
| this | observingLearners | Leader.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1396 |
| connectingFollowers | this | Leader.getEpochToPropose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1478 |
| connectingFollowers | rwLock | Leader.getEpochToPropose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1478 |
| connectingFollowers | QV_LOCK | Leader.getEpochToPropose | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1480 |
| electingFollowers | this | Leader.waitForEpochAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1525 |
| electingFollowers | rwLock | Leader.waitForEpochAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1529 |
| electingFollowers | QV_LOCK | Leader.waitForEpochAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1532 |
| this | QV_LOCK | Leader.startZkServer | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1585 |
| newLeaderProposal.qvAcksetPairs | fzk | Leader.waitForNewLeaderAck | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java:1628 |
| this | accurateMode ? requestPathStats : new Object() | LeaderZooKeeperServer.startup | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LeaderZooKeeperServer.java:87 |
| revalidateSessionLock | this | ObserverMaster.revalidateSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:279 |
| revalidateSessionLock | rwLock | ObserverMaster.revalidateSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:279 |
| this | fzk | ObserverMaster.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:297 |
| this | learners | ObserverMaster.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:304 |
| this | waitingEvents | ObserverMaster.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:307 |
| this | outgoingQueue | ObserverMaster.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:307 |
| this | rwLock | ObserverMaster.startForwarding | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:330 |
| this | fzk | ObserverMaster.removeProposedPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:361 |
| this | rwLock | ObserverMaster.removeProposedPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:373 |
| this | addRemovePathRWLock | ObserverMaster.removeProposedPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:373 |
| this | rwLock | ObserverMaster.cacheCommittedPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:378 |
| this | waitingEvents | ObserverMaster.sendPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:402 |
| this | outgoingQueue | ObserverMaster.sendPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:402 |
| this | fzk | ObserverMaster.sendPacket | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:404 |
| this | n | ObserverMaster.informAndActivate | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:423 |
| this | accurateMode ? requestPathStats : new Object() | ObserverMaster.start | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:450 |
| this | learners | ObserverMaster.stop | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/ObserverMaster.java:497 |
| this | lock | CommitProcessor.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/CommitProcessor.java:217 |
| this | rwLock | CommitProcessor.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/CommitProcessor.java:218 |
| zk | fzk | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:587 |
| zk | this | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:607 |
| zk | QV_LOCK | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:607 |
| zk | leaderIs | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:652 |
| zk | n | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:656 |
| zk | outerLockObject | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:665 |
| zk | rwLock | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:668 |
| zk | outstandingChanges | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:690 |
| zk | addRemovePathRWLock | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:691 |
| zk | snapshotAndRestoreLock | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:740 |
| zk | lock | Learner.syncWithLeader | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Learner.java:769 |
| rl | lock | LearnerHandler.syncFollower | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java:815 |
| rl | this | LearnerHandler.syncFollower | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java:815 |
| rl | writeLock | LearnerHandler.syncFollower | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java:894 |
| this | lock | LearnerHandler.getLearnerHandlerInfo | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java:1122 |
| this | rwLock | LearnerHandler.getLearnerHandlerInfo | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java:1122 |
| this | rwLock | LearnerSessionTracker.commitSession | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerSessionTracker.java:135 |
| this | ProviderRegistry.class | UnifiedServerSocket.reset | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/UnifiedServerSocket.java:730 |
| this | ProviderRegistry.class | UnifiedInputStream.reset | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/UnifiedServerSocket.java:730 |
| this | lock | FollowerZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FollowerZooKeeperServer.java:116 |
| this | rwLock | FollowerZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FollowerZooKeeperServer.java:116 |
| this | addRemovePathRWLock | FollowerZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FollowerZooKeeperServer.java:121 |
| this | waitingEvents | FollowerZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FollowerZooKeeperServer.java:124 |
| this | outgoingQueue | FollowerZooKeeperServer.sync | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FollowerZooKeeperServer.java:124 |
| self | QV_LOCK | FastLeaderElection.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:307 |
| self | this | FastLeaderElection.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:313 |
| self | learners | FastLeaderElection.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:319 |
| self | QV_LOCK | Messenger.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:307 |
| self | this | Messenger.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:313 |
| self | learners | Messenger.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:319 |
| self | QV_LOCK | WorkerReceiver.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:307 |
| self | this | WorkerReceiver.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:313 |
| self | learners | WorkerReceiver.run | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java:319 |
| login | this | SaslQuorumAuthLearner.createSaslToken | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/auth/SaslQuorumAuthLearner.java:199 |
| readLock | this | PathTrie.findMaxPrefix | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/common/PathTrie.java:330 |
| writeLock | lock | PathTrie.clear | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/common/PathTrie.java:342 |
| this | lock | HostConnectionManager.updateServerList | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:155 |
| this | rwLock | HostConnectionManager.updateServerList | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:159 |
| this | writeLock | HostConnectionManager.updateServerList | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:196 |
| this | lock | HostConnectionManager.next | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:280 |
| this | rwLock | HostConnectionManager.next | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:280 |
| this | lock | HostConnectionManager.getServerAtIndex | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:317 |
| this | rwLock | HostConnectionManager.getServerAtIndex | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:317 |
| this | lock | HostConnectionManager.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:338 |
| this | rwLock | HostConnectionManager.size | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/HostConnectionManager.java:338 |
| this | rwLock | DnsSrvHostProvider.applyServerListUpdate | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/DnsSrvHostProvider.java:291 |
| this | lock | DnsSrvHostProvider.applyServerListUpdate | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/DnsSrvHostProvider.java:295 |
| login | this | ZooKeeperSaslClient.createSaslToken | call_to_locking_method | treesitter | /tmp/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/client/ZooKeeperSaslClient.java:333 |

## Detected Cycles

1. **lock** ↔ **this**
2. **watchers** ↔ **this**
3. **this** ↔ **n**
4. **node** ↔ **n**
5. **this** ↔ **forwardingFollowers**
6. **writeLock** ↔ **lock**
7. **this** ↔ **waitingEvents**
8. **this** ↔ **rwLock**
9. **this** ↔ **self.QV_LOCK**
10. **lock** ↔ **addRemovePathRWLock**
11. **observingLearners** ↔ **this**
12. **this** ↔ **outgoingQueue**
13. **this** ↔ **node**
14. **this** ↔ **cleanEvent**
15. **Login.class** ↔ **this**
16. **rwLock** ↔ **lock**
17. **rwLock** ↔ **addRemovePathRWLock**
18. **this** ↔ **addRemovePathRWLock**
19. **learners** ↔ **this**

## Wait/Notify Risk

Classes with `wait()`/`await()` but no `notify()`/`signal()` in same class:

- ClientCnxnSocketNetty
- Learner
- LearnerCnxAcceptor
- Listener
- QuorumCnxManager
- ZooKeeperMain
- ZooKeeperServerMain

## Candidate Insertion Points

- **Cycle**: this ↔ Login.class (pattern: DBCP-270, score: 0.50)
- **Cycle**: this ↔ cleanEvent (pattern: DBCP-270, score: 0.50)
- **Cycle**: this ↔ self.QV_LOCK (pattern: DBCP-270, score: 0.50)
- **Cycle**: this ↔ lock (pattern: DERBY-5447, score: 0.70)
- **Cycle**: waitingEvents ↔ this (pattern: DERBY-5447, score: 0.70)
- **Cycle**: outgoingQueue ↔ this (pattern: DERBY-5447, score: 0.70)
- **Cycle**: lock ↔ rwLock (pattern: DERBY-5447, score: 0.70)
- **Cycle**: lock ↔ addRemovePathRWLock (pattern: DERBY-5447, score: 0.70)
- **Cycle**: this ↔ rwLock (pattern: DERBY-5447, score: 0.70)
- **Cycle**: this ↔ addRemovePathRWLock (pattern: DERBY-5447, score: 0.70)
- **Cycle**: n ↔ this (pattern: DERBY-5447, score: 0.72)
- **Cycle**: node ↔ n (pattern: DERBY-5447, score: 0.70)
- **Cycle**: node ↔ this (pattern: DERBY-5447, score: 0.70)
- **Cycle**: this ↔ watchers (pattern: DERBY-5447, score: 0.70)
- **Cycle**: lock ↔ writeLock (pattern: GROOVY-4736, score: 0.73)
- **Cycle**: rwLock ↔ addRemovePathRWLock (pattern: DERBY-5447, score: 0.70)
- **Cycle**: this ↔ learners (pattern: DERBY-5447, score: 0.72)
- **Cycle**: this ↔ forwardingFollowers (pattern: DERBY-5447, score: 0.70)
- **Cycle**: observingLearners ↔ this (pattern: DERBY-5447, score: 0.70)
- **Wait risk**: ClientCnxnSocketNetty (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: ZooKeeperMain (pattern: LUCENE-1544, score: 0.22)
- **Wait risk**: LearnerCnxAcceptor (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Learner (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: ZooKeeperServerMain (pattern: LUCENE-1544, score: 0.22)
- **Wait risk**: Listener (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: QuorumCnxManager (pattern: LOG4J-38137, score: 0.20)
