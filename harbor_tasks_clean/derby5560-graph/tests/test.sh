#!/bin/bash
cd /app

# === Phase 1: Functional correctness ===
# Recompile the agent's changes
./gradlew :streams:compileJava :streams:compileTestJava -x test -x checkstyleTest -x checkstyleMain -x spotbugsMain --no-daemon > /tmp/compile_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/compile_output.txt /logs/verifier/
    exit 0
fi

# Run existing streams unit tests covering the modified subsystem
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.CachingWindowStoreTest" --tests "org.apache.kafka.streams.state.internals.NamedCacheTest" -x checkstyleTest --no-daemon > /tmp/test_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/test_output.txt /logs/verifier/
    exit 0
fi

# === Phase 2: Deadlock fix verification ===
mkdir -p /tmp/verifier-classes
javac -d /tmp/verifier-classes /opt/verifier/CachingWindowStoreVerificationTest.java > /tmp/verifier_compile.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/verifier_compile.txt /logs/verifier/
    exit 0
fi

fray -cp /tmp/verifier-classes org.apache.kafka.streams.state.internals.CachingWindowStoreVerificationTest -- --iter 1000 > /tmp/fray_output.txt 2>&1
RESULT=$?

mkdir -p /logs/verifier
cp /tmp/fray_output.txt /logs/verifier/

if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
