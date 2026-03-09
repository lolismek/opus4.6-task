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

# --- Stage 2: Kafka builder ---
FROM --platform=linux/amd64 amazoncorretto:25 AS kafka-builder

# CRITICAL: amazoncorretto:25 is missing findutils (provides xargs, needed by gradlew)
RUN yum install -y findutils tar gzip && yum clean all

COPY kafka/ /build/kafka/
WORKDIR /build/kafka

# Add printTestClasspath task so Fray can discover the test classpath
RUN cat >> /build/kafka/build.gradle << 'GRADLE'

subprojects {
    tasks.register("printTestClasspath") {
        doLast {
            def cp = []
            cp.addAll(sourceSets.main.output.classesDirs.files.collect { it.absolutePath })
            cp.addAll(sourceSets.test.output.classesDirs.files.collect { it.absolutePath })
            cp.addAll(configurations.testRuntimeClasspath.files.collect { it.absolutePath })
            println cp.join(File.pathSeparator)
        }
    }
}
GRADLE

# Compile main + test classes for the streams module
RUN ./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon

# Pre-compute test classpath for fray-gradle wrapper
RUN ./gradlew -q :streams:printTestClasspath --no-daemon > /tmp/streams-test-classpath.txt

# --- Stage 3: Final image ---
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

# Hide verification test from agent — moved to /opt/verifier/ so test.sh can inject it at verification time
RUN mkdir -p /opt/verifier \
    && mv /app/streams/src/test/java/org/apache/kafka/streams/state/internals/Dbcp270DeadlockVerificationTest.java \
       /opt/verifier/Dbcp270DeadlockVerificationTest.java \
    && rm -f /app/streams/build/classes/java/test/org/apache/kafka/streams/state/internals/Dbcp270DeadlockVerificationTest.class

# Pre-computed test classpath for fray-gradle (fallback if live Gradle query fails)
COPY --from=kafka-builder /tmp/streams-test-classpath.txt /opt/fray/streams-test-classpath.txt

# fray-gradle wrapper: runs a test class through Fray using the streams test classpath
RUN printf '#!/bin/bash\nset -e\nif [ $# -lt 1 ]; then echo "Usage: fray-gradle <main-class> [-- fray-options...]"; exit 1; fi\nMAIN_CLASS="$1"; shift\nFRAY_OPTS=""\nif [ "$1" = "--" ]; then shift; FRAY_OPTS="$@"; fi\ncd /app\nCP=$(./gradlew -q :streams:printTestClasspath --no-daemon 2>/dev/null || cat /opt/fray/streams-test-classpath.txt)\nexec fray -cp "$CP" "$MAIN_CLASS" -- $FRAY_OPTS\n' > /usr/local/bin/fray-gradle && chmod +x /usr/local/bin/fray-gradle

RUN mkdir -p /logs/verifier
