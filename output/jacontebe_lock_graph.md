# Lock Graph Summary: jacontebe

**Mode**: light
**Java files scanned**: 27
**Files with locks**: 18
**Total lock acquisitions**: 66
**Lock order edges**: 27

## Lock Hotspots

| Class | Lock Acquisitions | File |
|-------|------------------|------|
| MockRAMDirectory | 13 | JaConTeBe_TSVD/jacontebe/lucene/src/org/apache/lucene/store/MockRAMDirectory.java |
| DeadlockMonitorTest | 6 | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java |
| SimpleFactory | 6 | JaConTeBe_TSVD/jacontebe/pool/src/Test162.java |
| Thread1 | 3 | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java |
| Thread2 | 3 | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java |
| Test6977738 | 2 | JaConTeBe_TSVD/jacontebe/jdk6/src/Test6977738.java |
| Test6934356 | 2 | JaConTeBe_TSVD/jacontebe/jdk6/src/Test6934356.java |
| TestThread | 2 | JaConTeBe_TSVD/jacontebe/jdk6/src/Test6934356.java |
| ActivationLibrary | 2 | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/ActivationLibrary.java |
| DestroyThread | 2 | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/ActivationLibrary.java |
| TestFailedException | 2 | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestFailedException.java |
| WaitingMonitorTest | 2 | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/WaitingMonitorTest.java |
| GlobalDriver | 2 | JaConTeBe_TSVD/jacontebe/jacontebe/src/edu/illinois/jacontebe/globalevent/GlobalDriver.java |
| Dbcp271 | 2 | JaConTeBe_TSVD/jacontebe/dbcp/src/org/apache/commons/dbcp/Dbcp271.java |
| Test162 | 2 | JaConTeBe_TSVD/jacontebe/pool/src/Test162.java |
| Test146 | 2 | JaConTeBe_TSVD/jacontebe/pool/src/Test146.java |
| Test120 | 2 | JaConTeBe_TSVD/jacontebe/pool/src/Test120.java |
| Test46 | 2 | JaConTeBe_TSVD/jacontebe/pool/src/org/apache/commons/pool/Test46.java |
| TestLibrary | 1 | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestLibrary.java |
| StreamPipe | 1 | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/StreamPipe.java |

## Lock Order Edges

| From Lock | To Lock | Class.Method | Mechanism | Source | File:Line |
|-----------|---------|-------------|-----------|--------|-----------|
| o1 | o2 | DeadlockMonitorTest.run | nested_synchronized | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:29 |
| o2 | o1 | DeadlockMonitorTest.run | nested_synchronized | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:48 |
| o1 | o2 | Thread1.run | nested_synchronized | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:29 |
| o2 | o1 | Thread2.run | nested_synchronized | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:48 |
| TestLibrary.class | this | TestLibrary.getExtraProperties | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestLibrary.java:371 |
| TestLibrary.class | dir | TestLibrary.getExtraProperties | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestLibrary.java:371 |
| TestLibrary.class | ps | TestLibrary.getExtraProperties | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestLibrary.java:374 |
| TestLibrary.class | pw | TestLibrary.getExtraProperties | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestLibrary.java:374 |
| ps | pw | TestFailedException.printStackTrace | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestFailedException.java:61 |
| pw | ps | TestFailedException.printStackTrace | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jdk7/src/testUtils/TestFailedException.java:76 |
| o1 | ps | DeadlockMonitorTest.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:24 |
| o1 | pw | DeadlockMonitorTest.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:24 |
| o2 | ps | DeadlockMonitorTest.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:43 |
| o2 | pw | DeadlockMonitorTest.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:43 |
| o1 | ps | Thread1.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:24 |
| o1 | pw | Thread1.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:24 |
| o2 | ps | Thread2.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:43 |
| o2 | pw | Thread2.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/DeadlockMonitorTest.java:43 |
| o | ps | WaitingMonitorTest.main | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/WaitingMonitorTest.java:17 |
| o | pw | WaitingMonitorTest.main | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/WaitingMonitorTest.java:17 |
| o | ps | WaitingMonitorTest.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/WaitingMonitorTest.java:17 |
| o | pw | WaitingMonitorTest.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/test/edu/illinois/jacontebe/monitors/WaitingMonitorTest.java:17 |
| ob | ps | GlobalDriver.startStep | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/src/edu/illinois/jacontebe/globalevent/GlobalDriver.java:61 |
| ob | pw | GlobalDriver.startStep | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/jacontebe/src/edu/illinois/jacontebe/globalevent/GlobalDriver.java:61 |
| this | dir | Groovy4736.writeFile | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/groovy/src/Groovy4736.java:70 |
| testBasePool | this | Writer.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/pool/src/org/apache/commons/pool/Test46.java:163 |
| testBasePool | dir | Writer.run | call_to_locking_method | treesitter | JaConTeBe_TSVD/jacontebe/pool/src/org/apache/commons/pool/Test46.java:163 |

## Detected Cycles

1. **pw** ↔ **ps**
2. **o2** ↔ **o1**

## Wait/Notify Risk

Classes with `wait()`/`await()` but no `notify()`/`signal()` in same class:

- Dbcp271
- Dbcp369
- DeadlockMonitorTest
- Derby5447
- Derby5560
- Derby5561
- DriverThread
- FillBufferThread
- NullRunnable
- RacingThreadsTest
- Test46
- Test4779253
- Test50463
- Test6934356
- Test6977738
- Test7122142
- TestBarrier
- TestConnection
- TestThread
- Thread1
- Thread2
- Thread3
- WaitingMonitorTest
- WorkerThread
- WorkerThread2
- Write
- Writer

## Candidate Insertion Points

- **Cycle**: o1 ↔ o2 (pattern: JDK-6492872, score: 0.53)
- **Cycle**: ps ↔ pw (pattern: DERBY-5447, score: 0.70)
- **Wait risk**: NullRunnable (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: TestConnection (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Dbcp271 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Thread1 (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: Test7122142 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Writer (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Thread2 (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: Derby5561 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Test46 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Dbcp369 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: FillBufferThread (pattern: LOG4J-38137, score: 0.25)
- **Wait risk**: Test6934356 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: DriverThread (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: TestThread (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: Test6977738 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Derby5447 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Test50463 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: TestBarrier (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Thread3 (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: Derby5560 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: Test4779253 (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: WorkerThread2 (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: Write (pattern: LUCENE-1544, score: 0.22)
- **Wait risk**: DeadlockMonitorTest (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: WaitingMonitorTest (pattern: LOG4J-38137, score: 0.20)
- **Wait risk**: WorkerThread (pattern: LOG4J-38137, score: 0.23)
- **Wait risk**: RacingThreadsTest (pattern: LUCENE-2783, score: 0.23)
