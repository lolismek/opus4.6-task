# Porting SCTBench Tasks to Harbor

Guide for converting tasks from [Spaghetti Bench](https://github.com/cmu-pasta/spaghetti-bench) (SCTBench subset) into standalone [Harbor](https://github.com/harbor-ai/harbor) benchmark tasks.

## Source Repositories

| Repo | Contents |
|------|----------|
| [cmu-pasta/fray-benchmark](https://github.com/cmu-pasta/fray-benchmark) | Java source files for all benchmarks (under `bms/SCTBench/src/main/java/cmu/pasta/fray/benchmark/sctbench/`) |
| [cmu-pasta/spaghetti-bench](https://github.com/cmu-pasta/spaghetti-bench) | Task metadata in `src/concurrency_bench/sctbench.jsonl` |
| [cmu-pasta/fray](https://github.com/cmu-pasta/fray) | Fray concurrency testing framework (Gradle plugin + JUnit extension) |

## SCTBench Task Anatomy

Each SCTBench task is a single Java file with a `main()` method that reproduces a concurrency bug. The JSONL entry specifies:
- `instance_id`: Task name (e.g., `Deadlock01Bad`)
- `path`: Source path in fray-benchmark (e.g., `benchmarks/SCTBench/cs/origin/Deadlock01Bad.java`)
- `test_class`: Class name to invoke
- `test_method`: Always `main`

The original files live in packages (`cmu.pasta.fray.benchmark.sctbench.cs.origin`). For Harbor tasks, we strip the package declaration to keep things simple.

## Porting Checklist

For each new task:

1. **Copy the buggy `.java` file** from `fray-benchmark/bms/SCTBench/src/main/java/cmu/pasta/fray/benchmark/sctbench/...`
   - Remove the `package` declaration
   - Place in `environment/app/src/main/java/`

2. **Write a Fray test wrapper** in `environment/app/src/test/java/<ClassName>Test.java`:
   ```java
   import org.junit.jupiter.api.extension.ExtendWith;
   import org.pastalab.fray.junit.junit5.FrayTestExtension;
   import org.pastalab.fray.junit.junit5.annotations.ConcurrencyTest;

   @ExtendWith(FrayTestExtension.class)
   public class <ClassName>Test {
       @ConcurrencyTest(iterations = 100)
       public void test() throws Exception {
           <ClassName>.main(new String[]{});
       }
   }
   ```

3. **Write `instruction.md`** — Describe the bug without giving away the fix. Mention that `./gradlew frayTest` verifies the fix.

4. **Write `solution/solve.sh`** — Gold patch that fixes the bug. Use a heredoc to overwrite the file with the corrected version.

5. **Copy the Dockerfile template** — Only change: the Java filenames if needed. The template pre-installs Fray and pre-compiles everything.

6. **Copy `test.sh`** — No changes needed between tasks.

7. **Update `task.toml`** — Adjust difficulty, tags, and timeouts as needed.

## Dockerfile Template

The Dockerfile in `deadlock01bad/environment/Dockerfile` is reusable for all SCTBench tasks. Key layers:
- `eclipse-temurin:21-jdk` base image
- Gradle wrapper installation
- Fray Gradle plugin pulls in all Fray artifacts automatically
- Pre-compilation at build time so the agent only recompiles changed source

## build.gradle.kts Template

```kotlin
plugins {
    id("java")
    id("org.pastalab.fray.gradle") version "0.7.3"
}

repositories {
    mavenCentral()
}

fray {
    version = "0.7.3"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.test {
    useJUnitPlatform()
}
```

## test.sh Template

```bash
#!/bin/bash
cd /app
./gradlew compileJava compileTestJava --no-daemon 2>/dev/null
./gradlew frayTest --no-daemon > /tmp/fray_output.txt 2>&1
RESULT=$?
mkdir -p /logs/verifier
if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
```

## Available SCTBench Tasks (28 total)

### Easy (deadlock / simple race)
- `Deadlock01Bad` — 2-lock cyclic deadlock (PORTED)
- `AccountBad` — Race condition on shared account balance
- `Lazy01Bad` — Lazy initialization race
- `Sync01Bad` / `Sync02Bad` — Missing synchronization

### Medium (ordering / protocol bugs)
- `Carter01Bad` — Ordering violation
- `CircularBufferBad` — Producer-consumer race
- `FsbenchBad` — File system benchmark race
- `Phase01Bad` — Phase ordering bug
- `QueueBad` — Concurrent queue race
- `StackBad` — Concurrent stack race
- `TwostageBad` / `Twostage100Bad` — Two-stage pipeline race
- `WronglockBad` / `Wronglock1Bad` / `Wronglock3Bad` — Wrong lock used for synchronization

### Hard (memory ordering / complex)
- `Reorder3Bad` / `Reorder4Bad` / `Reorder5Bad` / `Reorder10Bad` / `Reorder20Bad` — Memory reordering bugs (increasing thread count)
- `Reorder50Bad` / `Reorder100Bad` — High thread count reordering
- `ArithmeticProgBad` — Arithmetic progression race
- `BluetoothDriverBad` — Device driver concurrency bug
- `TokenRingBad` — Token ring protocol bug
- `StringBufferJDK` — JDK StringBuffer race
- `WorkStealQueue` — Work-stealing queue race

## Kafka Tasks

The Spaghetti Bench also includes Kafka tasks (in `kafka.jsonl`). These are significantly harder to port:
- Multi-module Gradle project
- Require specific Kafka commit checkout
- Much larger Dockerfiles
- Consider porting these only after all SCTBench tasks are done.

## Gotchas

- **Fray version**: Pin to `0.7.3` (latest stable release). The `0.7.4-SNAPSHOT` in the Fray repo is unreleased.
- **Gradle cache**: The Dockerfile pre-warms the Gradle cache. If you change the Fray version, rebuild the Docker image from scratch.
- **JDK**: Fray requires JDK 21. The Gradle toolchain config ensures this.
- **Timeout tuning**: Most SCTBench tasks are small. 600s agent timeout and 120s verifier timeout should suffice. Increase for Kafka tasks.
- **Package names**: Strip the `cmu.pasta.fray.benchmark.sctbench.*` package from source files. Harbor tasks use default (no-package) classes.
- **`isLocked()` checks**: Some SCTBench files (like `Deadlock01Bad`) include manual deadlock detection via `isLocked()`. These throw `RuntimeException("deadlock")` before the actual deadlock occurs. Fray still detects these as failures.
