# Kafka Streams Deadlock Injection Plan

## Overview

6 parallel Claude Code instances inject 3 deadlock patterns into the Kafka Streams module. Each pattern is injected twice: once with the lock graph as context, once without.

## Folder Structure

```
tasks/kafka-deadlock/
├── kafka/                              # Clean reference clone (do not modify)
├── lock_graph/                         # Pre-computed lock graph
│   ├── kafka_streams_both_lock_graph.json
│   └── kafka_streams_both_lock_graph.md
├── INJECTION_PLAN.md                   # This file
├── dbcp270-graph/
│   ├── kafka/                          # Copy of kafka repo
│   └── INJECTION_NOTES.md             # Agent writes this
├── dbcp270-nograph/
│   ├── kafka/
│   └── INJECTION_NOTES.md
├── derby5560-graph/
│   ├── kafka/
│   └── INJECTION_NOTES.md
├── derby5560-nograph/
│   ├── kafka/
│   └── INJECTION_NOTES.md
├── synth001-graph/
│   ├── kafka/
│   └── INJECTION_NOTES.md
└── synth001-nograph/
    ├── kafka/
    └── INJECTION_NOTES.md
```

## Setup Commands

```bash
cd tasks/kafka-deadlock

# Create lock graph directory
mkdir -p lock_graph
cp ../../output/kafka_streams_both_lock_graph.json lock_graph/
cp ../../output/kafka_streams_both_lock_graph.md lock_graph/

# Create 6 working copies
for d in dbcp270-graph dbcp270-nograph derby5560-graph derby5560-nograph synth001-graph synth001-nograph; do
  mkdir -p "$d"
  cp -a kafka "$d/kafka"
done
```

## Prompts

See below. Launch each in planning mode (`claude --plan`) from the respective `kafka/` directory.
