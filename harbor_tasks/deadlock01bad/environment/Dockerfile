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
COPY Deadlock01Bad.java /app/Deadlock01Bad.java

# Pre-compile the buggy code
RUN javac Deadlock01Bad.java

# Create logs directory for verifier output
RUN mkdir -p /logs/verifier
