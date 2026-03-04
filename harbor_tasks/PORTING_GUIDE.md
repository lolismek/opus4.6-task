# Porting SCTBench Tasks to Harbor

Guide for converting tasks from [Spaghetti Bench](https://github.com/cmu-pasta/spaghetti-bench) (SCTBench subset) into standalone [Harbor](https://github.com/harbor-framework/harbor) benchmark tasks.

## Source Repositories

| Repo | Contents |
|------|----------|
| [cmu-pasta/fray-benchmark](https://github.com/cmu-pasta/fray-benchmark) | Java source files (under `bms/SCTBench/src/main/java/cmu/pasta/fray/benchmark/sctbench/`) |
| [cmu-pasta/spaghetti-bench](https://github.com/cmu-pasta/spaghetti-bench) | Task metadata in `src/concurrency_bench/sctbench.jsonl`, agent/loader infrastructure |
| [cmu-pasta/fray](https://github.com/cmu-pasta/fray) | Fray concurrency testing framework (CLI tool + JVM instrumentation) |

## How Spaghetti Bench Runs SCTBench Tasks

Spaghetti-bench uses a simple workflow for SCTBench (no Gradle plugin, no JUnit):

1. Copy the single `.java` file to a workdir
2. Compile: `javac Deadlock01Bad.java`
3. Run with Fray CLI: `fray -cp . Deadlock01Bad -- --redirect-stdout --output=.fray_workdir`
4. Exit code 0 = bug not found (pass), non-zero = bug triggered (fail)

The `fray` CLI is a shell script wrapping an instrumented JDK, JVMTI agent, and Java agent. It calls the program's `main()` method and systematically explores thread interleavings.

## Harbor Task Anatomy

Each Harbor task has this structure:
```
task-name/
├── task.toml          # Metadata, timeouts, resource limits
├── instruction.md     # Bug description shown to the agent
├── environment/
│   └── Dockerfile     # Container with JDK + Fray + buggy code
├── tests/
│   └── test.sh        # Recompile + run Fray → write /logs/verifier/reward.txt
└── solution/
    └── solve.sh       # Gold patch (oracle agent)
```

Key Harbor conventions:
- `test.sh` writes `1` or `0` to `/logs/verifier/reward.txt`
- `tests/` is copied to `/tests` in the container by the evaluator
- `solution/` is copied to `/solution` for oracle runs
- Agent works in WORKDIR (typically `/app`)
- `allow_internet = true` is required (agent installs Claude Code CLI + calls API)

## Porting Checklist

For each new task:

1. **Copy the buggy `.java` file** from fray-benchmark
   - Remove the `package` declaration
   - Place in `environment/` (will be COPY'd to `/app`)

2. **Write `instruction.md`** — describe the bug without giving away the fix. Mention `fray -cp . ClassName -- --iter 1000` for verification.

3. **Write `solution/solve.sh`** — heredoc that overwrites the file with the fixed version.

4. **Copy the Dockerfile template** — only change the Java filename.

5. **Copy `test.sh`** — only change the class name.

6. **Update `task.toml`** — adjust difficulty and tags.

## Dockerfile Template

The Dockerfile builds Fray from source in a multi-stage build (build stage ~5 min on x86_64, ~10 min under QEMU on Apple Silicon; final image ~330 MB):

```dockerfile
FROM --platform=linux/amd64 ubuntu:22.04 AS base

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip curl git gcc g++ cmake make ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Amazon Corretto 25 (includes jmods, unlike eclipse-temurin)
RUN curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz \
    | tar -xz -C /opt \
    && ln -s /opt/amazon-corretto-25.* /opt/jdk25
ENV JAVA_HOME=/opt/jdk25
ENV PATH="$JAVA_HOME/bin:$PATH"

# Build Fray from source (pinned to v0.7.3)
WORKDIR /build
RUN git clone --depth 1 --branch v0.7.3 https://github.com/cmu-pasta/fray.git

WORKDIR /build/fray

# Fray requires JDK 11 for Gradle compilation toolchain
RUN curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz \
    | tar -xz -C /opt \
    && ln -s /opt/amazon-corretto-11.* /opt/jdk11

# Build Fray (skip tests and plugins)
# JDK25 env var is critical: jlink uses it to create the instrumented JDK
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

# Install Fray to /opt/fray
RUN mkdir -p /opt/fray/libs /opt/fray/bin \
    && cp core/build/libs/*-all.jar /opt/fray/libs/ \
    && cp instrumentation/agent/build/libs/*.jar /opt/fray/libs/ \
    && cp -r instrumentation/jdk/build/java-inst /opt/fray/ \
    && cp -r jvmti/build/native-libs /opt/fray/ \
    && ./gradlew -Pfray.installDir=/opt/fray/ genRunner --no-daemon \
    && cp bin/fray /opt/fray/bin/ \
    && chmod +x /opt/fray/bin/fray

# --- Final stage: slim image with just JDK 25 + Fray + buggy code ---
FROM --platform=linux/amd64 amazoncorretto:25

# Copy Fray installation
COPY --from=base /opt/fray /opt/fray
ENV PATH="/opt/fray/bin:$PATH"

# Copy buggy Java source
WORKDIR /app
COPY YourBuggyFile.java /app/YourBuggyFile.java

# Pre-compile the buggy code
RUN javac YourBuggyFile.java

# Create logs directory for verifier output
RUN mkdir -p /logs/verifier
```

### Why these specific choices

- **Ubuntu 22.04 build stage**: Fray's JVMTI C++ code uses `-nostdlib` linker flag. Amazon Linux's gcc has a `__dso_handle` linker incompatibility. Ubuntu's gcc handles it correctly.
- **Amazon Corretto 25 (not eclipse-temurin)**: Fray's `jlink` needs `.jmod` files to create the instrumented JDK. Eclipse-temurin strips jmods to save space; Corretto includes them.
- **JDK 25 specifically**: Fray v0.7.3's `instrumentation/jdk/build.gradle.kts` looks for `$JDK25` env var for jlink. Building with JDK 21 produces a broken instrumented JDK that reports false `DeadlockException` on every program.
- **JDK 11**: Fray's Gradle build uses `jvmToolchain(11)` for Kotlin/Java compilation.
- **`sed` to remove `#include <iostream>`**: The JVMTI `runtime.cc` includes `<iostream>` but never uses it. This unused include creates a C++ global initializer requiring `__dso_handle`, which conflicts with `-nostdlib`.
- **`--platform=linux/amd64`**: Fray's JVMTI native library is x86_64 only.

## test.sh Template

```bash
#!/bin/bash
cd /app
javac ClassName.java 2>/dev/null
fray -cp . ClassName -- --iter 1000 --redirect-stdout --output=/tmp/fray_workdir > /tmp/fray_output.txt 2>&1
RESULT=$?
mkdir -p /logs/verifier
if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
```

### Fray iteration count

Fray's default is 100,000 iterations. We use `--iter 1000` for two reasons:
- 1,000 iterations is sufficient for SCTBench deadlock/race bugs (spaghetti-bench was considering the same)
- Under QEMU emulation (Apple Silicon), 100k iterations takes ~2.5 min vs ~5 sec for 1,000

## Running a Task with Harbor

```bash
# Build the Docker image (first time only, ~10 min on Apple Silicon)
docker build --platform linux/amd64 -t hb__taskname harbor_tasks/taskname/environment/

# Run with Harbor (uses cached image if tagged as hb__taskname)
ANTHROPIC_API_KEY=<key> harbor run \
  --path harbor_tasks/taskname \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete
```

- `--no-delete` keeps the Docker image/container after the run (avoids rebuilding)
- Harbor names images as `hb__{task_name}` — pre-tag your image to skip builds
- Typical run time: ~1.5 min with cached image, ~13 min with full rebuild

## Available SCTBench Tasks (28 total)

### Easy (deadlock / simple race)
- `Deadlock01Bad` — 2-lock cyclic deadlock (**PORTED**)
- `AccountBad` — Race condition on shared account balance
- `Lazy01Bad` — Lazy initialization race
- `Sync01Bad` / `Sync02Bad` — Missing synchronization

### Medium (ordering / protocol bugs)
- `Carter01Bad` — Ordering violation
- `CircularBufferBad` — Producer-consumer race
- `FsbenchBad` — File system benchmark race
- `Phase01Bad` — Phase ordering bug
- `QueueBad` / `StackBad` — Concurrent data structure races
- `TwostageBad` / `Twostage100Bad` — Two-stage pipeline race
- `WronglockBad` / `Wronglock1Bad` / `Wronglock3Bad` — Wrong lock used

### Hard (memory ordering / complex)
- `Reorder3Bad` through `Reorder100Bad` — Memory reordering bugs (increasing threads)
- `ArithmeticProgBad` — Arithmetic progression race
- `BluetoothDriverBad` — Device driver concurrency bug
- `TokenRingBad` — Token ring protocol bug
- `StringBufferJDK` — JDK StringBuffer race
- `WorkStealQueue` — Work-stealing queue race

## Gotchas

- **Platform**: Fray JVMTI is x86_64-only. Always use `--platform=linux/amd64` in Dockerfile. On Apple Silicon, everything runs under QEMU emulation (~5-10x slower).
- **Fray version**: Pin to `v0.7.3` tag. Build requires JDK 11 + JDK 25.
- **JDK for jlink**: Must use JDK 25 with jmods. Set `JDK25` env var. Eclipse-temurin lacks jmods; use Amazon Corretto.
- **JVMTI linker error**: Remove unused `#include <iostream>` from `jvmti/src/cpp/runtime.cc` (conflicts with `-nostdlib`). Use Ubuntu as build base (not Amazon Linux) for gcc compatibility.
- **Package names**: Strip the `cmu.pasta.fray.benchmark.sctbench.*` package from source files.
- **No Gradle plugin needed**: SCTBench tasks use plain `javac` + `fray` CLI, not the Fray Gradle plugin.
- **`isLocked()` checks**: Some SCTBench files include manual deadlock detection that throws `RuntimeException("deadlock")` before actual deadlock. Fray still detects these as failures.
- **`allow_internet = true`**: Required in task.toml — Harbor's claude-code agent installs the CLI via `curl` and needs API access.
- **Docker disk usage**: Each image build uses ~2-3 GB of build cache. Run `docker system prune -a -f` periodically. On constrained disks, use `--no-delete` and pre-tag images to avoid rebuilds.
