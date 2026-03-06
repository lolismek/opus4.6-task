Now create a Harbor benchmark task from your injection so it can be used to evaluate AI coding agents.

**What is Harbor**: Harbor is a framework that runs an AI agent inside a Docker container with a buggy codebase. The agent reads an instruction file, tries to fix the bug, then a verifier script checks if the fix works. The agent gets a reward of 1 (fixed) or 0 (still broken).

**Your task directory**: Create the Harbor task at `harbor_tasks/<task_name>/` where `<task_name>` matches your injection folder name (e.g., if you're working in `tasks/kafka-deadlock/dbcp270-graph/`, create `harbor_tasks/dbcp270-graph/`). The structure must be:

```
harbor_tasks/<task_name>/
├── task.toml
├── instruction.md
├── environment/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── kafka/              # Full Kafka source copy (with your injected bug)
├── tests/
│   └── test.sh
└── solution/
    └── solve.sh
```

**Dockerfile requirements** (CRITICAL — follow exactly to avoid build failures):

The Dockerfile is a multi-stage build with 3 stages: Fray builder, Kafka builder, and final image.

**Stage 1: Fray builder** — Use this EXACT pattern. It has been debugged extensively and any deviation will break:

```dockerfile
FROM --platform=linux/amd64 ubuntu:22.04 AS fray-builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip curl git gcc g++ cmake make ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Amazon Corretto 25 (NOT eclipse-temurin — it lacks jmods needed by jlink)
RUN curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz \
    | tar -xz -C /opt \
    && ln -s /opt/amazon-corretto-25.* /opt/jdk25
ENV JAVA_HOME=/opt/jdk25
ENV PATH="$JAVA_HOME/bin:$PATH"

# Fray v0.7.3 (pinned — other versions have incompatibilities)
WORKDIR /build
RUN git clone --depth 1 --branch v0.7.3 https://github.com/cmu-pasta/fray.git
WORKDIR /build/fray

# JDK 11 for Gradle toolchain
RUN curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz \
    | tar -xz -C /opt \
    && ln -s /opt/amazon-corretto-11.* /opt/jdk11

# Build Fray — these sed commands fix known build issues:
# 1. Remove plugins/integration-test subprojects (not needed, can fail)
# 2. Remove unused #include <iostream> from JVMTI code (causes __dso_handle linker error with -nostdlib)
ENV JDK11=/opt/jdk11
ENV JDK25=/opt/jdk25
ENV JRE=/opt/jdk25
RUN sed -i '/include("plugins/d' settings.gradle.kts \
    && sed -i '/include("integration-test")/d' settings.gradle.kts \
    && sed -i '/#include <iostream>/d' jvmti/src/cpp/runtime.cc \
    && chmod +x gradlew \
    && ./gradlew build -x test -x check \
       -Porg.gradle.java.installations.paths=/opt/jdk11,/opt/jdk25 \
       --no-daemon

RUN mkdir -p /opt/fray/libs /opt/fray/bin \
    && cp core/build/libs/*-all.jar /opt/fray/libs/ \
    && cp instrumentation/agent/build/libs/*.jar /opt/fray/libs/ \
    && cp -r instrumentation/jdk/build/java-inst /opt/fray/ \
    && cp -r jvmti/build/native-libs /opt/fray/ \
    && ./gradlew -Pfray.installDir=/opt/fray/ genRunner --no-daemon \
    && cp bin/fray /opt/fray/bin/ \
    && chmod +x /opt/fray/bin/fray
```

**Stage 2: Kafka builder** — Compiles the streams module (and its dependencies) using Gradle. The `amazoncorretto:25` base image is minimal (Amazon Linux 2023) and **missing `findutils`** which provides `xargs` — without it, `./gradlew` will fail with "xargs is not available":

```dockerfile
FROM --platform=linux/amd64 amazoncorretto:25 AS kafka-builder

# CRITICAL: amazoncorretto:25 is missing findutils (provides xargs, needed by gradlew)
RUN yum install -y findutils tar gzip && yum clean all

COPY kafka/ /build/kafka/
WORKDIR /build/kafka
# Compile main + test classes for the streams module
RUN ./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

**Stage 3: Final image** — Contains Fray, the full Kafka source tree (with Gradle), pre-compiled classes, AND the Gradle dependency cache (so the agent/verifier don't re-download ~500MB of dependencies):

```dockerfile
FROM --platform=linux/amd64 amazoncorretto:25

# CRITICAL: same findutils requirement as Stage 2
RUN yum install -y findutils tar gzip && yum clean all

# Fray
COPY --from=fray-builder /opt/fray /opt/fray
ENV PATH="/opt/fray/bin:$PATH"

# Gradle dependency cache (avoids re-downloading ~500MB on every ./gradlew invocation)
COPY --from=kafka-builder /root/.gradle/ /root/.gradle/

# Full Kafka tree with Gradle wrapper + pre-compiled classes
# The agent edits source here and uses ./gradlew to recompile
COPY --from=kafka-builder /build/kafka/ /app/
WORKDIR /app

RUN mkdir -p /logs/verifier
```

**IMPORTANT: .dockerignore** — Create `environment/.dockerignore` with:
```
kafka/.git
kafka/.gradle
**/build/
```
This prevents copying git history (~17MB), local Gradle cache, and stale build artifacts into the Docker build context. The kafka/ directory inside environment/ should be a copy of your injected Kafka source (copy it from your working directory in `tasks/kafka-deadlock/<your_folder>/kafka/`).

**test.sh**: The verifier recompiles the agent's changes and runs the verification test using Gradle.

**CRITICAL — READ YOUR TEST BEFORE WRITING test.sh**: You MUST check whether your verification test PASSES when the deadlock is present, or FAILS when the deadlock is present. This determines the reward logic:

- **If your test PASSES when the bug is PRESENT** (e.g., it asserts `assertNotNull(deadlockedThreads)` or `assertTrue(deadlockDetected)`): Then test passing means the agent FAILED to fix the bug. Use **INVERTED** reward logic: `echo "0"` on test pass, `echo "1"` on test fail.
- **If your test FAILS when the bug is PRESENT** (e.g., it calls `fail("Deadlock detected...")` when a deadlock is found): Then test passing means the agent SUCCEEDED. Use **STANDARD** reward logic: `echo "1"` on test pass, `echo "0"` on test fail.

Most of the injected tests assert the deadlock EXISTS (pass when buggy), so you likely need INVERTED logic. **Check your test carefully.**

Here is the template — replace the `--tests` argument and choose the correct reward block:

```bash
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
./gradlew :streams:test --tests "your.package.YourDeadlockTest" --no-daemon > /tmp/test_output.txt 2>&1
RESULT=$?

mkdir -p /logs/verifier
cp /tmp/test_output.txt /logs/verifier/

# === CHOOSE ONE OF THE TWO BLOCKS BELOW ===

# STANDARD (test FAILS when bug is present, PASSES when fixed):
# if [ $RESULT -eq 0 ]; then
#     echo "1" > /logs/verifier/reward.txt   # Test passed = bug fixed
# else
#     echo "0" > /logs/verifier/reward.txt   # Test failed = bug still there
# fi

# INVERTED (test PASSES when bug is present, FAILS when fixed):
# if [ $RESULT -eq 0 ]; then
#     echo "0" > /logs/verifier/reward.txt   # Test passed = bug still there
# else
#     echo "1" > /logs/verifier/reward.txt   # Test failed = bug is fixed
# fi
```

**instruction.md**: Describe the bug symptoms WITHOUT revealing the root cause. Mention:
- Which test class fails and how to run it (the Gradle command)
- That `fray` is available for systematic interleaving exploration
- The symptom: deadlock detected (threads hang, ThreadMXBean reports deadlocked threads)
- Point the agent at the streams module source in `/app/streams/src/`
- That they can recompile with `./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon`

Do NOT mention: the specific lock cycle, which files contain the bug, the pattern name, or that it was injected.

**solution/solve.sh**: A script that reverts your injected changes (the gold patch). Use `sed` or heredoc to patch the specific files back to their original state.

**task.toml**:
```toml
[metadata]
author = "arxlab"
difficulty = "hard"
category = "concurrency"
tags = ["java", "deadlock", "kafka", "streams"]

[environment]
cpus = 2
memory_mb = 8192
storage_mb = 20480
build_timeout_sec = 900.0
allow_internet = true

[agent]
timeout_sec = 900.0

[verifier]
timeout_sec = 600.0
```

**Important**: Read the existing Harbor task at `harbor_tasks/deadlock01bad/` for reference, but note that yours is more complex (multi-file, needs Gradle, larger codebase).

**Deliverable**: The complete Harbor task directory, ready to build and test with:
```bash
docker build --platform linux/amd64 -t hb__<task_name> harbor_tasks/<task_name>/environment/
```
