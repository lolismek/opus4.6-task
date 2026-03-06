#!/bin/bash
cd /app

# Recompile with agent's changes
javac Deadlock01Bad.java 2>/dev/null

# Run Fray to systematically explore thread interleavings
fray -cp . Deadlock01Bad -- --iter 100000 --redirect-stdout --output=/tmp/fray_workdir > /tmp/fray_output.txt 2>&1
RESULT=$?

# Write reward
mkdir -p /logs/verifier
if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
