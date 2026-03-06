#!/bin/bash
cd /app

# Inject the hidden verification test back into the source tree
TEST_SRC="streams/src/test/java/org/apache/kafka/streams/processor/internals"
cp /opt/verifier/Synth001DeadlockVerificationTest.java "$TEST_SRC/"

# Recompile agent's changes + injected Fray test (log errors for debugging)
./gradlew :streams:compileJava :streams:compileTestJava -x test -x checkstyleTest -x checkstyleMain -x spotbugsMain --no-daemon > /tmp/compile_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/compile_output.txt /logs/verifier/
    exit 0
fi

# Run the verification test through Fray (1000 iterations)
# The test only uses JDK classes (Files, Path, locks) so it only needs the
# compiled test class directory — not the full Kafka classpath.
TEST_CP="/app/streams/build/classes/java/test"
fray -cp "$TEST_CP" org.apache.kafka.streams.processor.internals.Synth001DeadlockVerificationTest -- --iter 1000 > /tmp/fray_output.txt 2>&1
RESULT=$?

mkdir -p /logs/verifier
cp /tmp/fray_output.txt /logs/verifier/

# Fray exit code 0 = no deadlock found = bug is fixed
# Fray exit code non-zero = deadlock found = bug still present
if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt   # No deadlock = fixed
else
    echo "0" > /logs/verifier/reward.txt   # Deadlock found = still buggy
fi
