#!/bin/bash
# Setup 6 working copies of Kafka for parallel deadlock injection.
# Run from: tasks/kafka-deadlock/
#
# After running this script, cd into each <task>/kafka/ directory
# and launch Claude Code manually with the corresponding prompt.

set -e

DIRS="dbcp270-graph dbcp270-nograph derby5560-graph derby5560-nograph synth001-graph synth001-nograph"

# 1. Copy lock graph
mkdir -p lock_graph
cp ../../output/kafka_streams_both_lock_graph.json lock_graph/
cp ../../output/kafka_streams_both_lock_graph.md lock_graph/

# 2. Create 6 kafka copies
for d in $DIRS; do
  if [ -d "$d/kafka" ]; then
    echo "SKIP: $d/kafka already exists"
  else
    echo "Copying kafka -> $d/kafka ..."
    mkdir -p "$d"
    cp -a kafka "$d/kafka"
  fi
done

echo ""
echo "=== Setup complete ==="
echo ""
echo "To inject, cd into each directory and launch Claude Code with the prompt:"
echo ""
for d in $DIRS; do
  echo "  $d/kafka/  <-  prompts/${d}.md"
done
