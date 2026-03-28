#!/usr/bin/env bash
#
# JaiClaw Multi-Tenant Start — run the gateway in MULTI tenant mode
#
# Reads API keys and tenant configuration from JAICLAW_ENV_FILE (or docker-compose/.env.multitenant)
#
# Usage:
#   ./start-multitenant.sh                     # start multi-tenant gateway locally (requires Java 21)
#   ./start-multitenant.sh local               # same as default
#   ./start-multitenant.sh docker              # start multi-tenant gateway via Docker Compose
#   ./start-multitenant.sh shell               # start interactive CLI shell (local, multi-tenant)
#   ./start-multitenant.sh cron                # start cron-manager locally (multi-tenant)
#   ./start-multitenant.sh cron docker         # start cron-manager via Docker Compose (multi-tenant)
#   ./start-multitenant.sh --force-build       # rebuild from source, then start gateway locally
#   ./start-multitenant.sh --force-build docker # rebuild Docker image, then start gateway
#   ./start-multitenant.sh validate            # validate multi-tenant configuration without starting
#   ./start-multitenant.sh stop                # stop Docker Compose stack
#   ./start-multitenant.sh logs                # tail gateway container logs
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/docker-compose"

# Shared helpers (colors, logging, API key resolution)
source "$SCRIPT_DIR/scripts/common.sh"

# Source persistent config pointer
[ -f "$HOME/.jaiclawrc" ] && source "$HOME/.jaiclawrc"

# Multi-tenant env file defaults to a separate file so it doesn't clash with single-tenant config
ENV_FILE="${JAICLAW_MT_ENV_FILE:-${JAICLAW_ENV_FILE:-$COMPOSE_DIR/.env.multitenant}}"

# ─── Defaults ─────────────────────────────────────────────────────────────────

# Force multi-tenant mode — this is the entire purpose of this script
export JAICLAW_TENANT_MODE=multi

# ─── Load .env ────────────────────────────────────────────────────────────────

load_env() {
    if [ ! -f "$ENV_FILE" ]; then
        # Fall back to the standard .env if no multitenant-specific file exists
        if [ -f "$COMPOSE_DIR/.env" ]; then
            ENV_FILE="$COMPOSE_DIR/.env"
            warn "No multi-tenant env file found. Falling back to $ENV_FILE"
            warn "Create $COMPOSE_DIR/.env.multitenant for multi-tenant-specific config."
        elif [ -f "$COMPOSE_DIR/.env.example" ]; then
            mkdir -p "$(dirname "$ENV_FILE")"
            cp "$COMPOSE_DIR/.env.example" "$ENV_FILE"
            warn "Created $ENV_FILE from template — edit it to add your API keys and tenant config."
        else
            err "No .env file found at $ENV_FILE"
            exit 1
        fi
    fi

    # Export all non-empty, non-comment lines from .env
    set -a
    while IFS='=' read -r key value; do
        [[ "$key" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$key" ]] && continue
        if [ -z "${!key:-}" ] && [ -n "$value" ]; then
            export "$key=$value"
        fi
    done < "$ENV_FILE"
    set +a

    ok "Loaded configuration from $ENV_FILE"
}

# ─── Sync API key to .env ────────────────────────────────────────────────────

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

# ─── Local build ─────────────────────────────────────────────────────────────

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

# ─── Multi-tenant configuration ──────────────────────────────────────────────

# Apply multi-tenant Spring Boot overrides via environment variables.
# These map to jaiclaw.tenant.* properties in application.yml.
apply_tenant_overrides() {
    # Core: force MULTI mode
    export JAICLAW_TENANT_MODE=multi

    # Default tenant ID — used as fallback when no X-Tenant-Id header is provided
    export JAICLAW_TENANT_DEFAULT_ID="${JAICLAW_TENANT_DEFAULT_ID:-default}"

    # Tenant config locations — directories to scan for per-tenant YAML/env files
    # e.g. "file:/etc/jaiclaw/tenants/,classpath:config/tenants/"
    export JAICLAW_TENANT_CONFIG_LOCATIONS="${JAICLAW_TENANT_CONFIG_LOCATIONS:-}"

    # Tenant resolution header (default: X-Tenant-Id)
    export JAICLAW_TENANT_HEADER="${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"

    # Security: JWT is recommended for multi-tenant; fall back to api-key if not set
    export JAICLAW_SECURITY_MODE="${JAICLAW_SECURITY_MODE:-api-key}"
}

# Print multi-tenant status and curl examples with tenant header
print_mt_info() {
    local port="${1:-8080}"
    local key="${RESOLVED_API_KEY:-<your-api-key>}"
    local tenant_header="${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"

    echo ""
    ok "Multi-tenant mode: ENABLED"
    info "Tenant header: ${tenant_header}"
    info "Default tenant: ${JAICLAW_TENANT_DEFAULT_ID:-default}"
    if [ -n "${JAICLAW_TENANT_CONFIG_LOCATIONS:-}" ]; then
        info "Tenant config: ${JAICLAW_TENANT_CONFIG_LOCATIONS}"
    fi
    echo ""
}

print_mt_curl_example() {
    local port="${1:-8080}"
    local key="${RESOLVED_API_KEY:-<your-api-key>}"
    local tenant_header="${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"

    printf "  ${BOLD}curl -X POST http://localhost:${port}/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  -H \"Content-Type: application/json\" \\\\${NC}\n"
    printf "  ${BOLD}  -H \"X-API-Key: ${key}\" \\\\${NC}\n"
    printf "  ${BOLD}  -H \"${tenant_header}: acme-corp\" \\\\${NC}\n"
    printf "  ${BOLD}  -d '{\"content\": \"hello\"}'${NC}\n"
}

print_mt_httpie_example() {
    local port="${1:-8080}"
    local key="${RESOLVED_API_KEY:-<your-api-key>}"
    local tenant_header="${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"

    printf "  ${BOLD}http POST http://localhost:${port}/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  X-API-Key:${key} \\\\${NC}\n"
    printf "  ${BOLD}  ${tenant_header}:acme-corp \\\\${NC}\n"
    printf "  ${BOLD}  content=hello${NC}\n"
}

# ─── Validation ──────────────────────────────────────────────────────────────

validate_config() {
    local errors=0

    header "Multi-Tenant Configuration Validation"

    # Check LLM provider
    local provider="${AI_PROVIDER:-anthropic}"
    case "$provider" in
        anthropic)
            if [ -z "${ANTHROPIC_API_KEY:-}" ] || [ "${ANTHROPIC_API_KEY}" = "not-set" ]; then
                err "ANTHROPIC_API_KEY is not set (AI_PROVIDER=anthropic)"
                errors=$((errors + 1))
            else
                ok "Anthropic API key: configured"
            fi
            ;;
        openai)
            if [ -z "${OPENAI_API_KEY:-}" ] || [ "${OPENAI_API_KEY}" = "not-set" ]; then
                err "OPENAI_API_KEY is not set (AI_PROVIDER=openai)"
                errors=$((errors + 1))
            else
                ok "OpenAI API key: configured"
            fi
            ;;
        ollama)
            ok "Ollama provider selected (no API key required)"
            ;;
        google-genai)
            if [ -z "${GEMINI_API_KEY:-}" ] || [ "${GEMINI_API_KEY}" = "not-set" ]; then
                err "GEMINI_API_KEY is not set (AI_PROVIDER=google-genai)"
                errors=$((errors + 1))
            else
                ok "Gemini API key: configured"
            fi
            ;;
        *)
            warn "Unknown AI_PROVIDER: $provider"
            ;;
    esac

    # Check security mode
    local sec_mode="${JAICLAW_SECURITY_MODE:-api-key}"
    case "$sec_mode" in
        api-key)
            ok "Security mode: api-key"
            if [ -z "${JAICLAW_API_KEY:-}" ]; then
                info "API key will be auto-generated on first start"
            fi
            ;;
        jwt)
            ok "Security mode: jwt (recommended for multi-tenant)"
            ;;
        none)
            warn "Security mode: DISABLED — not recommended for multi-tenant deployments"
            ;;
    esac

    # Check tenant config
    ok "Tenant mode: MULTI"
    info "Default tenant ID: ${JAICLAW_TENANT_DEFAULT_ID:-default}"
    info "Tenant header: ${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"

    if [ -n "${JAICLAW_TENANT_CONFIG_LOCATIONS:-}" ]; then
        ok "Tenant config locations: ${JAICLAW_TENANT_CONFIG_LOCATIONS}"
        # Check if directories exist (for file: prefixed paths)
        IFS=',' read -ra locations <<< "${JAICLAW_TENANT_CONFIG_LOCATIONS}"
        for loc in "${locations[@]}"; do
            loc="$(echo "$loc" | xargs)"  # trim whitespace
            if [[ "$loc" == file:* ]]; then
                local dir="${loc#file:}"
                if [ -d "$dir" ]; then
                    local count
                    count=$(find "$dir" -name "*.yml" -o -name "*.yaml" -o -name "*.env" 2>/dev/null | wc -l | xargs)
                    ok "  $dir — $count config file(s) found"
                else
                    warn "  $dir — directory does not exist"
                fi
            else
                info "  $loc (classpath — verified at runtime)"
            fi
        done
    else
        info "No tenant config locations set — all tenants use default agent config"
        info "Set JAICLAW_TENANT_CONFIG_LOCATIONS to load per-tenant YAML files"
    fi

    # Channel status
    echo ""
    info "Channel adapters:"
    [ "${TELEGRAM_ENABLED:-false}" = "true" ] && ok "  Telegram: enabled" || info "  Telegram: disabled"
    [ "${SLACK_ENABLED:-false}" = "true" ] && ok "  Slack: enabled" || info "  Slack: disabled"
    [ "${DISCORD_ENABLED:-false}" = "true" ] && ok "  Discord: enabled" || info "  Discord: disabled"
    [ "${EMAIL_ENABLED:-false}" = "true" ] && ok "  Email: enabled" || info "  Email: disabled"
    [ "${SMS_ENABLED:-false}" = "true" ] && ok "  SMS: enabled" || info "  SMS: disabled"

    echo ""
    if [ "$errors" -gt 0 ]; then
        err "$errors error(s) found — fix them before starting"
        return 1
    else
        ok "Configuration valid — ready to start"
        return 0
    fi
}

# ─── Commands ────────────────────────────────────────────────────────────────

cmd_gateway() {
    header "JaiClaw Multi-Tenant Gateway (Docker)"
    load_env
    apply_tenant_overrides
    resolve_api_key
    sync_api_key_to_env
    ensure_docker
    ensure_image jaiclaw-gateway-app

    info "Starting multi-tenant gateway container..."
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" up -d

    local port="${GATEWAY_PORT:-8080}"
    ok "Gateway is running on http://localhost:${port}"
    print_security_info
    print_mt_info "$port"

    echo "Test it (include ${JAICLAW_TENANT_HEADER:-X-Tenant-Id} header):"
    print_mt_curl_example "$port"
    echo ""
    echo "Or with httpie:"
    print_mt_httpie_example "$port"
    echo ""
    echo "View logs:"
    printf "  ${BOLD}./start-multitenant.sh logs${NC}\n"
    echo ""
    echo "Stop:"
    printf "  ${BOLD}./start-multitenant.sh stop${NC}\n"
    echo ""

    info "Tailing logs (Ctrl+C to detach — gateway keeps running)..."
    echo ""
    docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" logs -f gateway
}

cmd_local() {
    header "JaiClaw Multi-Tenant Gateway (Local)"
    load_env
    apply_tenant_overrides
    resolve_api_key
    ensure_java
    ensure_local_build "$SCRIPT_DIR/apps/jaiclaw-gateway-app/target/jaiclaw-gateway-app-0.1.0-SNAPSHOT.jar"

    local port="${SERVER_PORT:-8080}"
    echo "Starting multi-tenant gateway on http://localhost:${port}..."
    print_security_info
    print_mt_info "$port"

    echo "Test with (include ${JAICLAW_TENANT_HEADER:-X-Tenant-Id} header):"
    print_mt_curl_example "$port"
    echo ""
    echo "Or with httpie:"
    print_mt_httpie_example "$port"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl :jaiclaw-gateway-app)
}

cmd_shell() {
    header "JaiClaw Multi-Tenant Interactive Shell"
    load_env
    apply_tenant_overrides
    ensure_java
    ensure_local_build "$SCRIPT_DIR/apps/jaiclaw-shell/target/jaiclaw-shell-0.1.0-SNAPSHOT.jar"

    echo "Starting interactive shell (multi-tenant mode)..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Tenant context is set via the shell session${NC}\n"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl :jaiclaw-shell)
}

cmd_cron() {
    local mode="${1:-local}"

    if [ "$mode" = "docker" ]; then
        header "JaiClaw Multi-Tenant Cron Manager (Docker)"
        load_env
        apply_tenant_overrides
        resolve_api_key
        ensure_docker
        ensure_image jaiclaw-cron-manager

        info "Starting multi-tenant cron-manager container..."
        docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" --profile cron-manager up -d cron-manager

        local port="${CRON_MANAGER_PORT:-8090}"
        local key="${RESOLVED_API_KEY:-<your-api-key>}"
        local tenant_header="${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"
        echo ""
        ok "Cron Manager is running on http://localhost:${port}"
        print_security_info
        print_mt_info "$port"

        echo "Test it:"
        printf "  ${BOLD}curl http://localhost:${port}/mcp \\\\${NC}\n"
        printf "  ${BOLD}  -H \"X-API-Key: ${key}\" \\\\${NC}\n"
        printf "  ${BOLD}  -H \"${tenant_header}: acme-corp\"${NC}\n"
        echo ""

        info "Tailing logs (Ctrl+C to detach — cron-manager keeps running)..."
        echo ""
        docker compose -f "$COMPOSE_DIR/docker-compose.yml" --env-file "$ENV_FILE" logs -f cron-manager
    else
        header "JaiClaw Multi-Tenant Cron Manager (Local)"
        load_env
        apply_tenant_overrides
        resolve_api_key
        ensure_java
        ensure_local_build "$SCRIPT_DIR/apps/jaiclaw-cron-manager/target/jaiclaw-cron-manager-0.1.0-SNAPSHOT.jar"

        local port="${JAICLAW_CRON_MANAGER_PORT:-8090}"
        local key="${RESOLVED_API_KEY:-<your-api-key>}"
        local tenant_header="${JAICLAW_TENANT_HEADER:-X-Tenant-Id}"
        echo "Starting multi-tenant cron-manager on http://localhost:${port}..."
        print_security_info
        print_mt_info "$port"

        echo "Test with:"
        printf "  ${BOLD}curl http://localhost:${port}/mcp \\\\${NC}\n"
        printf "  ${BOLD}  -H \"X-API-Key: ${key}\" \\\\${NC}\n"
        printf "  ${BOLD}  -H \"${tenant_header}: acme-corp\"${NC}\n"
        echo ""

        (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl :jaiclaw-cron-manager)
    fi
}

cmd_validate() {
    load_env
    apply_tenant_overrides
    resolve_api_key
    validate_config
}

cmd_stop() {
    info "Stopping JaiClaw multi-tenant stack..."
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
    local)            cmd_local ;;
    shell)            cmd_shell ;;
    docker|gateway)   cmd_gateway ;;
    cron)             cmd_cron "${EXTRA_ARGS[0]:-local}" ;;
    validate|check)   cmd_validate ;;
    stop)             cmd_stop ;;
    logs)             cmd_logs ;;
    -h|--help|help)
        echo "Usage: ./start-multitenant.sh [options] [command]"
        echo ""
        echo "Starts JaiClaw in MULTI-tenant mode with strict tenant isolation."
        echo "All requests must include the tenant header (default: X-Tenant-Id)."
        echo ""
        echo "Options:"
        echo "  --force-build    Force rebuild (local JARs or Docker images)"
        echo ""
        echo "Commands:"
        echo "  (default)        Start multi-tenant gateway locally (requires Java 21)"
        echo "  local            Same as default"
        echo "  docker           Start multi-tenant gateway via Docker Compose"
        echo "  shell            Start interactive CLI shell (multi-tenant, local Java)"
        echo "  cron             Start cron-manager locally (multi-tenant)"
        echo "  cron docker      Start cron-manager via Docker Compose (multi-tenant)"
        echo "  validate         Validate multi-tenant configuration without starting"
        echo "  stop             Stop Docker Compose stack"
        echo "  logs             Tail gateway container logs"
        echo ""
        echo "Environment variables:"
        echo "  JAICLAW_MT_ENV_FILE            Path to multi-tenant .env file"
        echo "                                 (default: docker-compose/.env.multitenant)"
        echo "  JAICLAW_TENANT_DEFAULT_ID      Default tenant ID (default: 'default')"
        echo "  JAICLAW_TENANT_HEADER          HTTP header for tenant resolution"
        echo "                                 (default: 'X-Tenant-Id')"
        echo "  JAICLAW_TENANT_CONFIG_LOCATIONS  Comma-separated directories for per-tenant"
        echo "                                 YAML files (e.g. file:/etc/jaiclaw/tenants/)"
        echo "  JAICLAW_SECURITY_MODE          Security mode: api-key, jwt, or none"
        echo ""
        echo "Example .env.multitenant:"
        echo "  AI_PROVIDER=anthropic"
        echo "  ANTHROPIC_API_KEY=sk-ant-..."
        echo "  JAICLAW_SECURITY_MODE=api-key"
        echo "  JAICLAW_TENANT_DEFAULT_ID=default"
        echo "  JAICLAW_TENANT_CONFIG_LOCATIONS=file:./config/tenants/"
        echo ""
        echo "Per-tenant config files (optional, in config locations dir):"
        echo "  config/tenants/acme-corp.yml"
        echo "  config/tenants/startup-inc.yml"
        echo ""
        echo "See docs/jaiclaw-multi-tenancy-architecture-implementation.md for full details."
        ;;
    *)
        err "Unknown command: $COMMAND"
        echo "Run './start-multitenant.sh help' for usage."
        exit 1
        ;;
esac
