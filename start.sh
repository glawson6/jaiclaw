#!/usr/bin/env bash
#
# JaiClaw Start — run the gateway (local or Docker) or interactive shell
#
# Reads API keys and configuration from JAICLAW_ENV_FILE (or docker-compose/.env)
#
# Usage:
#   ./start.sh              # start gateway locally (default, requires Java 21)
#   ./start.sh shell        # start interactive CLI shell (local, requires Java 21)
#   ./start.sh cli          # start interactive CLI shell (Docker, no Java needed)
#   ./start.sh docker       # start gateway via Docker Compose
#   ./start.sh gateway      # start gateway via Docker Compose (alias for docker)
#   ./start.sh local        # start gateway locally (explicit, same as default)
#   ./start.sh cron         # start cron-manager locally (requires Java 21)
#   ./start.sh cron docker  # start cron-manager via Docker Compose
#   ./start.sh telegram     # validate Telegram bot token → start gateway (local)
#   ./start.sh telegram docker  # validate Telegram bot token → start gateway (Docker)
#   ./start.sh --force-build          # rebuild from source, then start gateway locally
#   ./start.sh --force-build docker  # rebuild Docker image, then start gateway
#   ./start.sh stop         # stop Docker Compose stack
#   ./start.sh logs         # tail gateway container logs
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/docker-compose"

# Shared helpers (colors, logging, API key resolution)
source "$SCRIPT_DIR/scripts/common.sh"

# Source persistent config pointer (written by quickstart --reconfigure or first-run prompt)
[ -f "$HOME/.jaiclawrc" ] && source "$HOME/.jaiclawrc"
ENV_FILE="${JAICLAW_ENV_FILE:-$COMPOSE_DIR/.env}"

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
    if [ ! -f "$HOME/.jaiclawrc" ]; then
        info "Tip: run './quickstart.sh --reconfigure' to choose a persistent config location."
    fi
}

# ─── Sync API key to .env ────────────────────────────────────────────────────
#
# After resolve_api_key generates or reads a key, write it to the .env file so
# Docker Compose can pass it to the container. Without this, the container
# generates its own key (different from the host's) and the curl example fails.
#
sync_api_key_to_env() {
    if [ -n "${RESOLVED_API_KEY:-}" ] && [ -f "$ENV_FILE" ]; then
        local current
        current=$(grep "^JAICLAW_API_KEY=" "$ENV_FILE" | cut -d= -f2)
        if [ "$current" != "$RESOLVED_API_KEY" ]; then
            sed -i.bak "s|^JAICLAW_API_KEY=.*|JAICLAW_API_KEY=${RESOLVED_API_KEY}|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
        fi
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

# ─── Local build ────────────────────────────────────────────────────────────

ensure_local_build() {
    local jar="$1"
    if [ "$FORCE_BUILD" = true ]; then
        info "Force-building JaiClaw from source..."
        (cd "$SCRIPT_DIR" && ./mvnw install -DskipTests -q)
        ok "Build complete"
    elif [ ! -f "$jar" ]; then
        info "Building JaiClaw (first run)..."
        (cd "$SCRIPT_DIR" && ./mvnw install -DskipTests -q)
        ok "Build complete"
    fi
}

# ─── Image check ─────────────────────────────────────────────────────────────

ensure_image() {
    local module="${1:-jaiclaw-gateway-app}"
    local image="io.jaiclaw/${module}:0.1.0-SNAPSHOT"
    if [ "$FORCE_BUILD" = true ]; then
        info "Force-building Docker image for ${module}..."
        docker rmi "$image" 2>/dev/null || true
    elif docker image inspect "$image" &>/dev/null; then
        return 0
    else
        warn "Docker image for ${module} not found. Building..."
    fi
    ensure_java
    info "Running: ./mvnw package k8s:build -pl :${module} -am -Pk8s -DskipTests"
    (cd "$SCRIPT_DIR" && ./mvnw package k8s:build -pl ":${module}" -am -Pk8s -DskipTests)
    ok "Docker image built: $image"
}

# ─── Commands ────────────────────────────────────────────────────────────────

cmd_gateway() {
    header "JaiClaw Gateway (Docker)"
    load_env
    resolve_api_key
    sync_api_key_to_env
    ensure_docker
    ensure_image jaiclaw-gateway-app

    info "Starting gateway container..."
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d

    echo ""
    ok "Gateway is running on http://localhost:${GATEWAY_PORT:-8080}"
    print_security_info
    echo ""
    echo "Test it:"
    print_api_curl_example "${GATEWAY_PORT:-8080}"
    echo ""
    echo "Or with httpie:"
    print_api_httpie_example "${GATEWAY_PORT:-8080}"
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
    header "JaiClaw Interactive Shell"
    load_env
    ensure_java
    ensure_local_build "$SCRIPT_DIR/apps/jaiclaw-shell/target/jaiclaw-shell-0.1.0-SNAPSHOT.jar"

    echo "Starting interactive shell..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Type 'onboard' to run the setup wizard${NC}\n"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl :jaiclaw-shell -q)
}

cmd_cli() {
    header "JaiClaw Interactive Shell (Docker)"
    load_env
    ensure_docker
    ensure_image jaiclaw-shell

    echo "Starting interactive shell container..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Type 'onboard' to run the setup wizard${NC}\n"
    echo ""

    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" --profile cli run --rm cli
}

cmd_local() {
    header "JaiClaw Gateway (Local)"
    load_env
    resolve_api_key
    ensure_java
    ensure_local_build "$SCRIPT_DIR/apps/jaiclaw-gateway-app/target/jaiclaw-gateway-app-0.1.0-SNAPSHOT.jar"

    echo "Starting gateway on http://localhost:${SERVER_PORT:-8080}..."
    print_security_info
    echo ""
    echo "Test with:"
    print_api_curl_example "${SERVER_PORT:-8080}"
    echo ""
    echo "Or with httpie:"
    print_api_httpie_example "${SERVER_PORT:-8080}"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl :jaiclaw-gateway-app)
}

cmd_cron() {
    local mode="${1:-local}"

    if [ "$mode" = "docker" ]; then
        header "JaiClaw Cron Manager (Docker)"
        load_env
        resolve_api_key
        ensure_docker
        ensure_image jaiclaw-cron-manager

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
        echo "Or with httpie:"
        printf "  ${BOLD}http http://localhost:${CRON_MANAGER_PORT:-8090}/mcp X-API-Key:${cron_key}${NC}\n"
        echo ""
        echo "View logs:"
        printf "  ${BOLD}docker compose -f $COMPOSE_DIR/docker-compose.yml logs -f cron-manager${NC}\n"
        echo ""

        info "Tailing logs (Ctrl+C to detach — cron-manager keeps running)..."
        echo ""
        docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" logs -f cron-manager
    else
        header "JaiClaw Cron Manager (Local)"
        load_env
        resolve_api_key
        ensure_java

        ensure_local_build "$SCRIPT_DIR/apps/jaiclaw-cron-manager/target/jaiclaw-cron-manager-0.1.0-SNAPSHOT.jar"

        echo "Starting cron-manager on http://localhost:${JAICLAW_CRON_MANAGER_PORT:-8090}..."
        print_security_info
        echo ""
        echo "Test with:"
        local cron_key="${RESOLVED_API_KEY:-<your-api-key>}"
        printf "  ${BOLD}curl http://localhost:${JAICLAW_CRON_MANAGER_PORT:-8090}/mcp \\\\${NC}\n"
        printf "  ${BOLD}  -H \"X-API-Key: ${cron_key}\"${NC}\n"
        echo ""
        echo "Or with httpie:"
        printf "  ${BOLD}http http://localhost:${JAICLAW_CRON_MANAGER_PORT:-8090}/mcp X-API-Key:${cron_key}${NC}\n"
        echo ""
        printf "  ${DIM}Type 'cron-status' for cron job overview${NC}\n"
        printf "  ${DIM}Type 'cron-list' to list all jobs${NC}\n"
        echo ""

        (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl :jaiclaw-cron-manager)
    fi
}

cmd_telegram() {
    header "JaiClaw + Telegram"
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

    # Determine mode (local vs Docker)
    local mode="$1"
    if [ "$mode" = "docker" ]; then
        cmd_gateway
    else
        cmd_local
    fi
}

cmd_stop() {
    info "Stopping JaiClaw..."
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

COMMAND="${COMMAND:-local}"

case "$COMMAND" in
    local)    cmd_local ;;
    shell)    cmd_shell ;;
    cli)      cmd_cli ;;
    docker|gateway)  cmd_gateway ;;
    cron)     cmd_cron "${EXTRA_ARGS[0]:-local}" ;;
    telegram) cmd_telegram "${EXTRA_ARGS[0]:-}" ;;
    stop)     cmd_stop ;;
    logs)     cmd_logs ;;
    -h|--help|help)
        echo "Usage: ./start.sh [options] [command]"
        echo ""
        echo "Options:"
        echo "  --force-build    Force rebuild (local JARs or Docker images)"
        echo ""
        echo "Commands:"
        echo "  (default)        Start gateway locally (requires Java 21)"
        echo "  shell            Start interactive CLI shell (local Java)"
        echo "  cli              Start interactive CLI shell (Docker, no Java needed)"
        echo "  docker           Start gateway via Docker Compose"
        echo "  local            Start gateway locally (same as default)"
        echo "  cron             Start cron-manager locally (local Java)"
        echo "  cron docker      Start cron-manager via Docker Compose"
        echo "  telegram         Validate Telegram bot token and start gateway (local)"
        echo "  telegram docker  Validate Telegram bot token and start gateway (Docker)"
        echo "  stop             Stop Docker Compose stack"
        echo "  logs             Tail gateway container logs"
        echo ""
        echo "Configuration is loaded from \$JAICLAW_ENV_FILE (default: docker-compose/.env)."
        echo "Set JAICLAW_ENV_FILE or run './quickstart.sh --reconfigure' to change."
        ;;
    *)
        err "Unknown command: $COMMAND"
        echo "Run './start.sh help' for usage."
        exit 1
        ;;
esac
