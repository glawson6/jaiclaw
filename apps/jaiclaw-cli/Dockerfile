# JaiClaw CLI — Approach C (Manual docker build)
#
# Build context is the project root.
# Application files go to /opt/jaiclaw/ (survives volume mounts).
# User data at /home/jaiclaw/.jaiclaw/ (mountable from host).
#
# Build: ./mvnw package -pl :jaiclaw-cli -Pdocker -DskipTests

FROM eclipse-temurin:21-jre

ARG CLI_JAR

# Create non-root user
RUN groupadd -r jaiclaw && \
    useradd -r -g jaiclaw -m -d /home/jaiclaw -s /bin/bash jaiclaw

# Create directory structure
RUN mkdir -p /opt/jaiclaw/bin \
             /opt/jaiclaw/scripts \
             /home/jaiclaw/.jaiclaw/profiles/default/sessions

# Copy launcher, auth script, and fat JAR
COPY bin/jaiclaw /opt/jaiclaw/bin/jaiclaw
COPY scripts/auth-status.sh /opt/jaiclaw/scripts/auth-status.sh
COPY ${CLI_JAR} /opt/jaiclaw/bin/jaiclaw-cli.jar

# Set permissions
RUN chmod +x /opt/jaiclaw/bin/jaiclaw \
             /opt/jaiclaw/scripts/auth-status.sh && \
    chown -R jaiclaw:jaiclaw /home/jaiclaw /opt/jaiclaw

ENV JAICLAW_HOME=/home/jaiclaw/.jaiclaw

USER jaiclaw
WORKDIR /home/jaiclaw

ENTRYPOINT ["/opt/jaiclaw/bin/jaiclaw"]
