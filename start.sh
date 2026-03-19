#!/usr/bin/env bash
#
# JClaw Start — run the gateway (Docker) or interactive shell (local)
#
# Reads API keys and configuration from JCLAW_ENV_FILE (or docker-compose/.env)
#
# Usage:
#   ./start.sh              # start gateway via Docker Compose (default)
#   ./start.sh shell        # start interactive CLI shell (local, requires Java 21)
#   ./start.sh cli          # start interactive CLI shell (Docker, no Java needed)
#   ./start.sh gateway      # start gateway via Docker Compose
#   ./start.sh local        # start gateway locally (no Docker, requires Java 21)
#   ./start.sh cron         # start cron-manager locally (requires Java 21)
#   ./start.sh cron docker  # start cron-manager via Docker Compose
#   ./start.sh telegram     # validate Telegram bot token → start gateway (Docker)
#   ./start.sh telegram local  # validate Telegram bot token → start gateway (local Java)
#   ./start.sh --force-build gateway  # rebuild Docker image, then start gateway
#   ./start.sh stop         # stop Docker Compose stack
#   ./start.sh logs         # tail gateway container logs
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/docker-compose"

# Shared helpers (colors, logging, API key resolution)
source "$SCRIPT_DIR/scripts/common.sh"

# Source persistent config pointer (written by quickstart --reconfigure or first-run prompt)
[ -f "$HOME/.jclawrc" ] && source "$HOME/.jclawrc"
ENV_FILE="${JCLAW_ENV_FILE:-$COMPOSE_DIR/.env}"

# ─── Load .env ────────────────────────────────────────────────────────────────

load_env() {
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$COMPOSE_DIR/.env.example" ]; then
            mkdir -p "$(dirname "$ENV_FILE")"
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
    if [ ! -f "$HOME/.jclawrc" ]; then
        info "Tip: run './quickstart.sh --reconfigure' to choose a persistent config location."
    fi
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
    local image="io.jclaw/${module}:0.1.0-SNAPSHOT"
    if [ "$FORCE_BUILD" = true ]; then
        info "Force-building Docker image for ${module}..."
        docker rmi "$image" 2>/dev/null || true
    elif docker image inspect "$image" &>/dev/null; then
        return 0
    else
        warn "Docker image for ${module} not found. Building..."
    fi
    ensure_java
    info "Running: ./mvnw package k8s:build -pl ${module} -am -Pk8s -DskipTests"
    (cd "$SCRIPT_DIR" && ./mvnw package k8s:build -pl "${module}" -am -Pk8s -DskipTests)
    ok "Docker image built: $image"
}

# ─── Commands ────────────────────────────────────────────────────────────────

cmd_gateway() {
    header "JClaw Gateway (Docker)"
    load_env
    resolve_api_key
    ensure_docker
    ensure_image jclaw-gateway-app

    info "Starting gateway container..."
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d

    echo ""
    ok "Gateway is running on http://localhost:${GATEWAY_PORT:-8080}"
    print_security_info
    echo ""
    echo "Test it:"
    print_api_curl_example "${GATEWAY_PORT:-8080}"
    echo ""
    echo "View logs:"
    printf "  ${BOLD}./start.sh logs${NC}\n"
    echo ""
    echo "Stop:"
    printf "  ${BOLD}./start.sh stop${NC}\n"
    echo ""

    info "Tailing logs (Ctrl+C to detach — gateway keeps running)..."
    echo ""
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" logs -f gateway
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

    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" --profile cli run --rm cli
}

cmd_local() {
    header "JClaw Gateway (Local)"
    load_env
    resolve_api_key
    ensure_java

    # Build if needed
    if [ ! -f "$SCRIPT_DIR/jclaw-gateway-app/target/jclaw-gateway-app-0.1.0-SNAPSHOT.jar" ]; then
        info "Building JClaw (first run)..."
        (cd "$SCRIPT_DIR" && ./mvnw install -DskipTests -q)
        ok "Build complete"
    fi

    echo "Starting gateway on http://localhost:${SERVER_PORT:-8080}..."
    print_security_info
    echo ""
    echo "Test with:"
    print_api_curl_example "${SERVER_PORT:-8080}"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl jclaw-gateway-app)
}

cmd_cron() {
    local mode="${1:-local}"

    if [ "$mode" = "docker" ]; then
        header "JClaw Cron Manager (Docker)"
        load_env
        resolve_api_key
        ensure_docker
        ensure_image jclaw-cron-manager

        info "Starting cron-manager container..."
        docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" --profile cron-manager up -d cron-manager

        echo ""
        ok "Cron Manager is running on http://localhost:${CRON_MANAGER_PORT:-8090}"
        print_security_info
        echo ""
        echo "Test it:"
        local cron_key="${RESOLVED_API_KEY:-<your-api-key>}"
        printf "  ${BOLD}curl http://localhost:${CRON_MANAGER_PORT:-8090}/mcp \\\\${NC}\n"
        printf "  ${BOLD}  -H \"X-API-Key: ${cron_key}\"${NC}\n"
        echo ""
        echo "View logs:"
        printf "  ${BOLD}docker compose -f $COMPOSE_DIR/docker-compose.yml logs -f cron-manager${NC}\n"
        echo ""

        info "Tailing logs (Ctrl+C to detach — cron-manager keeps running)..."
        echo ""
        docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" logs -f cron-manager
    else
        header "JClaw Cron Manager (Local)"
        load_env
        resolve_api_key
        ensure_java

        # Build if needed
        if [ ! -f "$SCRIPT_DIR/jclaw-cron-manager/target/jclaw-cron-manager-0.1.0-SNAPSHOT.jar" ]; then
            info "Building JClaw (first run)..."
            (cd "$SCRIPT_DIR" && ./mvnw install -DskipTests -q)
            ok "Build complete"
        fi

        echo "Starting cron-manager on http://localhost:${JCLAW_CRON_MANAGER_PORT:-8090}..."
        print_security_info
        echo ""
        echo "Test with:"
        local cron_key="${RESOLVED_API_KEY:-<your-api-key>}"
        printf "  ${BOLD}curl http://localhost:${JCLAW_CRON_MANAGER_PORT:-8090}/mcp \\\\${NC}\n"
        printf "  ${BOLD}  -H \"X-API-Key: ${cron_key}\"${NC}\n"
        echo ""
        printf "  ${DIM}Type 'cron-status' for cron job overview${NC}\n"
        printf "  ${DIM}Type 'cron-list' to list all jobs${NC}\n"
        echo ""

        (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl jclaw-cron-manager)
    fi
}

cmd_telegram() {
    header "JClaw + Telegram"
    load_env

    # Check for bot token
    if [ -z "${TELEGRAM_BOT_TOKEN:-}" ]; then
        err "TELEGRAM_BOT_TOKEN is not set."
        echo ""
        echo "To set up Telegram:"
        echo "  1. Open Telegram and message @BotFather"
        echo "  2. Send /newbot and follow the prompts"
        echo "  3. Copy the bot token"
        echo "  4. Add to docker-compose/.env:"
        echo "     TELEGRAM_BOT_TOKEN=<your-token>"
        echo ""
        echo "Full guide: docs/TELEGRAM-SETUP.md"
        exit 1
    fi

    # Validate token via /getMe
    info "Validating Telegram bot token..."
    local response
    response=$(curl -sf "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe" 2>/dev/null) || {
        err "Telegram token validation failed. Check your TELEGRAM_BOT_TOKEN."
        exit 1
    }
    local bot_username
    bot_username=$(echo "$response" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
    ok "Bot validated: @${bot_username}"
    echo ""
    printf "  ${BOLD}Bot link: https://t.me/${bot_username}${NC}\n"
    echo "  Open the link above in Telegram to chat with your bot."
    echo ""

    # Determine mode (Docker vs local)
    local mode="$1"
    if [ "$mode" = "local" ]; then
        cmd_local
    else
        cmd_gateway
    fi
}

cmd_stop() {
    info "Stopping JClaw..."
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" down
    ok "Stopped"
}

cmd_logs() {
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" logs -f gateway
}

# ─── Main ────────────────────────────────────────────────────────────────────

FORCE_BUILD=false
COMMAND=""
EXTRA_ARGS=()

# Parse global flags and command
for arg in "$@"; do
    case "$arg" in
        --force-build) FORCE_BUILD=true ;;
        *)
            if [ -z "$COMMAND" ]; then
                COMMAND="$arg"
            else
                EXTRA_ARGS+=("$arg")
            fi
            ;;
    esac
done

COMMAND="${COMMAND:-gateway}"

case "$COMMAND" in
    gateway)  cmd_gateway ;;
    shell)    cmd_shell ;;
    cli)      cmd_cli ;;
    local)    cmd_local ;;
    cron)     cmd_cron "${EXTRA_ARGS[0]:-local}" ;;
    telegram) cmd_telegram "${EXTRA_ARGS[0]:-}" ;;
    stop)     cmd_stop ;;
    logs)     cmd_logs ;;
    -h|--help|help)
        echo "Usage: ./start.sh [options] [command]"
        echo ""
        echo "Options:"
        echo "  --force-build    Force rebuild Docker images even if they exist"
        echo ""
        echo "Commands:"
        echo "  gateway          Start gateway via Docker Compose (default)"
        echo "  shell            Start interactive CLI shell (local Java)"
        echo "  cli              Start interactive CLI shell (Docker, no Java needed)"
        echo "  local            Start gateway locally without Docker (local Java)"
        echo "  cron             Start cron-manager locally (local Java)"
        echo "  cron docker      Start cron-manager via Docker Compose"
        echo "  telegram         Validate Telegram bot token and start gateway (Docker)"
        echo "  telegram local   Validate Telegram bot token and start gateway (local Java)"
        echo "  stop             Stop Docker Compose stack"
        echo "  logs             Tail gateway container logs"
        echo ""
        echo "Configuration is loaded from \$JCLAW_ENV_FILE (default: docker-compose/.env)."
        echo "Set JCLAW_ENV_FILE or run './quickstart.sh --reconfigure' to change."
        ;;
    *)
        err "Unknown command: $COMMAND"
        echo "Run './start.sh help' for usage."
        exit 1
        ;;
esac
