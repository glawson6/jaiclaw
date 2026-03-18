#!/usr/bin/env bash
#
# JClaw Start — run the gateway (Docker) or interactive shell (local)
#
# Reads API keys and configuration from docker-compose/.env
#
# Usage:
#   ./start.sh              # start gateway via Docker Compose (default)
#   ./start.sh shell        # start interactive CLI shell (local, requires Java 21)
#   ./start.sh cli          # start interactive CLI shell (Docker, no Java needed)
#   ./start.sh gateway      # start gateway via Docker Compose
#   ./start.sh local        # start gateway locally (no Docker, requires Java 21)
#   ./start.sh stop         # stop Docker Compose stack
#   ./start.sh logs         # tail gateway container logs
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/docker-compose"
ENV_FILE="$COMPOSE_DIR/.env"

# ─── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
DIM='\033[2m'
BOLD='\033[1m'
NC='\033[0m'

info()  { printf "${CYAN}▸${NC} %s\n" "$*"; }
ok()    { printf "${GREEN}✓${NC} %s\n" "$*"; }
warn()  { printf "${YELLOW}!${NC} %s\n" "$*"; }
err()   { printf "${RED}✗${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}${CYAN}── %s ──${NC}\n\n" "$*"; }

# ─── Load .env ────────────────────────────────────────────────────────────────

load_env() {
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$COMPOSE_DIR/.env.example" ]; then
            cp "$COMPOSE_DIR/.env.example" "$ENV_FILE"
            warn "Created $ENV_FILE from template — edit it to add your API keys."
        else
            err "No .env file found at $ENV_FILE"
            exit 1
        fi
    fi

    # Export all non-empty, non-comment lines from .env
    set -a
    while IFS='=' read -r key value; do
        # Skip comments and blank lines
        [[ "$key" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$key" ]] && continue
        # Only export if not already set in the environment
        if [ -z "${!key:-}" ] && [ -n "$value" ]; then
            export "$key=$value"
        fi
    done < "$ENV_FILE"
    set +a

    ok "Loaded configuration from $ENV_FILE"
}

# ─── Java check ──────────────────────────────────────────────────────────────

ensure_java() {
    # Source SDKMAN if available
    if [ -s "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" ]; then
        set +u
        source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
        set -u
    fi

    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        local version
        version=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1 | sed 's/.*"\([0-9][0-9]*\).*/\1/')
        if [ "$version" -ge 21 ] 2>/dev/null; then
            export JAVA_HOME
            return 0
        fi
    fi

    if command -v java &>/dev/null; then
        local version
        version=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9][0-9]*\).*/\1/')
        if [ "$version" -ge 21 ] 2>/dev/null; then
            return 0
        fi
    fi

    err "Java 21+ is required. Install with: sdk install java 21.0.9-oracle"
    exit 1
}

# ─── Docker check ────────────────────────────────────────────────────────────

ensure_docker() {
    if ! command -v docker &>/dev/null; then
        err "Docker is not installed. Install Docker Desktop: https://docs.docker.com/desktop/"
        exit 1
    fi
    if ! docker info &>/dev/null; then
        err "Docker daemon is not running. Start Docker Desktop and try again."
        exit 1
    fi
}

# ─── Image check ─────────────────────────────────────────────────────────────

ensure_image() {
    local module="${1:-jclaw-gateway-app}"
    if ! docker image inspect "io.jclaw/${module}:0.1.0-SNAPSHOT" &>/dev/null; then
        warn "Docker image for ${module} not found. Building..."
        ensure_java
        info "Running: ./mvnw package k8s:build -pl ${module} -am -Pk8s -DskipTests"
        (cd "$SCRIPT_DIR" && ./mvnw package k8s:build -pl "${module}" -am -Pk8s -DskipTests)
        ok "Docker image built: io.jclaw/${module}:0.1.0-SNAPSHOT"
    fi
}

# ─── Commands ────────────────────────────────────────────────────────────────

cmd_gateway() {
    header "JClaw Gateway (Docker)"
    load_env
    ensure_docker
    ensure_image jclaw-gateway-app

    info "Starting gateway container..."
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" up -d

    echo ""
    ok "Gateway is running on http://localhost:${GATEWAY_PORT:-8080}"
    echo ""
    echo "Test it:"
    printf "  ${BOLD}curl -X POST http://localhost:${GATEWAY_PORT:-8080}/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  -H \"Content-Type: application/json\" \\\\${NC}\n"
    printf "  ${BOLD}  -d '{\"content\": \"hello\"}'${NC}\n"
    echo ""
    echo "View logs:"
    printf "  ${BOLD}./start.sh logs${NC}\n"
    echo ""
    echo "Stop:"
    printf "  ${BOLD}./start.sh stop${NC}\n"
    echo ""

    info "Tailing logs (Ctrl+C to detach — gateway keeps running)..."
    echo ""
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" logs -f gateway
}

cmd_shell() {
    header "JClaw Interactive Shell"
    load_env
    ensure_java

    # Build if needed
    if [ ! -f "$SCRIPT_DIR/jclaw-shell/target/jclaw-shell-0.1.0-SNAPSHOT.jar" ]; then
        info "Building JClaw (first run)..."
        (cd "$SCRIPT_DIR" && ./mvnw install -DskipTests -q)
        ok "Build complete"
    fi

    echo "Starting interactive shell..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Type 'onboard' to run the setup wizard${NC}\n"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl jclaw-shell -q)
}

cmd_cli() {
    header "JClaw Interactive Shell (Docker)"
    load_env
    ensure_docker
    ensure_image jclaw-shell

    echo "Starting interactive shell container..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Type 'onboard' to run the setup wizard${NC}\n"
    echo ""

    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --profile cli run --rm cli
}

cmd_local() {
    header "JClaw Gateway (Local)"
    load_env
    ensure_java

    # Build if needed
    if [ ! -f "$SCRIPT_DIR/jclaw-gateway-app/target/jclaw-gateway-app-0.1.0-SNAPSHOT.jar" ]; then
        info "Building JClaw (first run)..."
        (cd "$SCRIPT_DIR" && ./mvnw install -DskipTests -q)
        ok "Build complete"
    fi

    echo "Starting gateway on http://localhost:${SERVER_PORT:-8080}..."
    echo ""
    echo "Test with:"
    printf "  ${BOLD}curl -X POST http://localhost:${SERVER_PORT:-8080}/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  -H \"Content-Type: application/json\" \\\\${NC}\n"
    printf "  ${BOLD}  -d '{\"content\": \"hello\"}'${NC}\n"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl jclaw-gateway-app)
}

cmd_stop() {
    info "Stopping JClaw..."
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" down
    ok "Stopped"
}

cmd_logs() {
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" logs -f gateway
}

# ─── Main ────────────────────────────────────────────────────────────────────

COMMAND="${1:-gateway}"

case "$COMMAND" in
    gateway)  cmd_gateway ;;
    shell)    cmd_shell ;;
    cli)      cmd_cli ;;
    local)    cmd_local ;;
    stop)     cmd_stop ;;
    logs)     cmd_logs ;;
    -h|--help|help)
        echo "Usage: ./start.sh [command]"
        echo ""
        echo "Commands:"
        echo "  gateway   Start gateway via Docker Compose (default)"
        echo "  shell     Start interactive CLI shell (local Java)"
        echo "  cli       Start interactive CLI shell (Docker, no Java needed)"
        echo "  local     Start gateway locally without Docker (local Java)"
        echo "  stop      Stop Docker Compose stack"
        echo "  logs      Tail gateway container logs"
        echo ""
        echo "Configuration is loaded from docker-compose/.env"
        echo "Edit that file to set API keys, provider, and channel tokens."
        ;;
    *)
        err "Unknown command: $COMMAND"
        echo "Run './start.sh help' for usage."
        exit 1
        ;;
esac
