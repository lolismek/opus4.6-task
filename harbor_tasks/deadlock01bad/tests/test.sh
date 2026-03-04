#!/bin/bash
cd /app

# Recompile with agent's changes
./gradlew compileJava compileTestJava --no-daemon 2>/dev/null

# Run Fray test
./gradlew frayTest --no-daemon > /tmp/fray_output.txt 2>&1
RESULT=$?

# Write reward
mkdir -p /logs/verifier
if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
