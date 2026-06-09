# JaiClaw E2E Test Runner — Docker Image
#
# Multi-stage build for running e2e tests locally without Java/Maven setup.
#
# Build:  ./e2e/build.sh
# Run:    docker run --rm -e E2E_SCENARIOS=4 io.jaiclaw/jaiclaw-e2e-runner:<version>
#

# ─── Stage 1: Builder ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POM files first (cache layer for dependencies)
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
COPY jaiclaw-bom/pom.xml jaiclaw-bom/pom.xml
COPY core/ core/
COPY channels/ channels/
COPY extensions/ extensions/
COPY apps/ apps/
COPY tools/ tools/
COPY jaiclaw-spring-boot-starter/ jaiclaw-spring-boot-starter/
COPY jaiclaw-starters/ jaiclaw-starters/
COPY jaiclaw-maven-plugin/ jaiclaw-maven-plugin/
COPY jaiclaw-examples/ jaiclaw-examples/

# Build all modules (install to local repo for scaffold scenario)
RUN chmod +x mvnw && \
    ./mvnw install -DskipTests -B -q \
      -Dmaven.repo.local=/build/.m2/repository

# ─── Stage 2: Runner ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk

# Install utilities needed by the test runner
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      curl jq procps lsof && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r jaiclaw && \
    useradd -r -g jaiclaw -m -d /home/jaiclaw -s /bin/bash jaiclaw

# Copy full source tree (needed for quickstart.sh, bin/jaiclaw, scaffolding)
COPY --chown=jaiclaw:jaiclaw . /opt/jaiclaw/

# Copy pre-built Maven repository from builder
COPY --from=builder --chown=jaiclaw:jaiclaw /build/.m2/repository /home/jaiclaw/.m2/repository

# Copy pre-built target directories from builder
COPY --from=builder --chown=jaiclaw:jaiclaw /build/apps/jaiclaw-cli/target/ /opt/jaiclaw/apps/jaiclaw-cli/target/
COPY --from=builder --chown=jaiclaw:jaiclaw /build/apps/jaiclaw-gateway-app/target/ /opt/jaiclaw/apps/jaiclaw-gateway-app/target/
COPY --from=builder --chown=jaiclaw:jaiclaw /build/apps/jaiclaw-shell-commands/target/ /opt/jaiclaw/apps/jaiclaw-shell-commands/target/

# Create jaiclaw home directory
RUN mkdir -p /home/jaiclaw/.jaiclaw && \
    chown -R jaiclaw:jaiclaw /home/jaiclaw/.jaiclaw

WORKDIR /opt/jaiclaw

# Make scripts executable
RUN chmod +x e2e/run-e2e-tests.sh bin/jaiclaw mvnw quickstart.sh

# Set environment
ENV JAVA_HOME=/opt/java/openjdk
ENV PROJECT_ROOT=/opt/jaiclaw
ENV MAVEN_OPTS="-Dmaven.repo.local=/home/jaiclaw/.m2/repository"

USER jaiclaw

ENTRYPOINT ["/opt/jaiclaw/e2e/run-e2e-tests.sh"]
CMD []
