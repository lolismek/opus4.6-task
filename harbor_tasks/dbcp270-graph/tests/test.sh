#!/bin/bash
cd /app

# Recompile agent's changes (log errors for debugging)
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon > /tmp/compile_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/compile_output.txt /logs/verifier/
    exit 0
fi

# Run the verification test
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.ThreadCacheDeadlockTest" --no-daemon > /tmp/test_output.txt 2>&1
RESULT=$?

mkdir -p /logs/verifier
cp /tmp/test_output.txt /logs/verifier/

# INVERTED logic: the test PASSES when the deadlock bug is PRESENT
# (it asserts assertNotNull(deadlockedThreads) — detecting the deadlock).
# If the agent fixed the bug, the test will FAIL (no deadlock found).
if [ $RESULT -eq 0 ]; then
    echo "0" > /logs/verifier/reward.txt   # Test passed = bug still there
else
    echo "1" > /logs/verifier/reward.txt   # Test failed = bug is fixed
fi
