FROM --platform=linux/amd64 eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip curl git \
    && rm -rf /var/lib/apt/lists/*

# Copy the pre-configured Gradle project
COPY app /app
WORKDIR /app

# Install Gradle wrapper
RUN curl -fsSL https://services.gradle.org/distributions/gradle-8.10-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && rm /tmp/gradle.zip \
    && /opt/gradle-8.10/bin/gradle wrapper --gradle-version 8.10 \
    && chmod +x gradlew

# Pre-download all dependencies and Fray artifacts (jlink, instrumented JDK, etc.)
# We run frayTest expecting it to fail (buggy code), but this caches everything.
RUN ./gradlew jlink compileJava compileTestJava --no-daemon 2>/dev/null; true
RUN ./gradlew frayTest --no-daemon 2>/dev/null; true

# Create logs directory for verifier output
RUN mkdir -p /logs/verifier

WORKDIR /app
