#!/usr/bin/env bash
#
# System Monitor — zero-prerequisite launcher
#
# Installs JBang (if needed) → JBang installs Java 21 → Maven builds the
# fat JAR → launches the system monitor gateway with embedded cron manager.
#
# The only prerequisite is curl (or wget) and a shell.
#
# Usage:
#   ./run.sh                  # build (if needed) and start
#   ./run.sh --build-only     # just build, don't start
#   ./run.sh --skip-build     # skip build, start (assumes prior build)
#   ./run.sh --force-build    # rebuild from source, then start
#   ./run.sh --help           # print usage
#
# Environment variables:
#   TELEGRAM_BOT_TOKEN    Telegram Bot API token (required)
#   TELEGRAM_CHAT_ID      Chat ID to receive daily health reports (required)
#   ANTHROPIC_API_KEY     Anthropic API key (required)
#   MONITOR_SCHEDULE      Cron expression (default: "0 7 * * *" = 7 AM daily)
#   MONITOR_TIMEZONE      Timezone (default: America/New_York)
#   JCLAW_ENV_FILE        Path to .env file for additional config
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODULE_ARTIFACT="jclaw-example-system-monitor"
JAR_NAME="${MODULE_ARTIFACT}-0.1.0-SNAPSHOT.jar"
JAR_PATH="$SCRIPT_DIR/target/$JAR_NAME"
MARKER_FILE="$SCRIPT_DIR/target/.sysmonitor-built"

# ─── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
DIM='\033[2m'
BOLD='\033[1m'
NC='\033[0m'

info()   { printf "${CYAN}▸${NC} %s\n" "$*"; }
ok()     { printf "${GREEN}✓${NC} %s\n" "$*"; }
warn()   { printf "${YELLOW}!${NC} %s\n" "$*"; }
err()    { printf "${RED}✗${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}${CYAN}── %s ──${NC}\n\n" "$*"; }
debug()  { printf "${DIM}  … %s${NC}\n" "$*"; }

# ─── Step 1: Ensure JBang ──────────────────────────────────────────────────────

ensure_jbang() {
    if command -v jbang &>/dev/null; then
        ok "JBang found ($(jbang --version 2>/dev/null || echo 'unknown'))"
        return 0
    fi

    # Check SDKMAN
    if [ -s "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" ]; then
        debug "Sourcing SDKMAN..."
        set +u
        source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
        set -u
        if command -v jbang &>/dev/null; then
            ok "JBang found via SDKMAN ($(jbang --version 2>/dev/null || echo 'unknown'))"
            return 0
        fi
    fi

    # Check common install locations
    if [ -x "$HOME/.jbang/bin/jbang" ]; then
        export PATH="$HOME/.jbang/bin:$PATH"
        ok "JBang found at ~/.jbang/bin"
        return 0
    fi

    info "Installing JBang..."
    if command -v curl &>/dev/null; then
        curl -Ls https://sh.jbang.dev | bash -s - app setup 2>/dev/null
    elif command -v wget &>/dev/null; then
        wget -q -O - https://sh.jbang.dev | bash -s - app setup 2>/dev/null
    else
        err "Neither curl nor wget found. Install JBang manually:"
        echo "  https://www.jbang.dev/download/"
        exit 1
    fi

    # Add to PATH for this session
    if [ -x "$HOME/.jbang/bin/jbang" ]; then
        export PATH="$HOME/.jbang/bin:$PATH"
    fi

    if ! command -v jbang &>/dev/null; then
        err "JBang installation failed. Install manually:"
        echo "  https://www.jbang.dev/download/"
        exit 1
    fi

    ok "JBang installed ($(jbang --version 2>/dev/null || echo 'unknown'))"
}

# ─── Step 2: Ensure Java 21 via JBang ──────────────────────────────────────────

ensure_java() {
    # If JAVA_HOME is already set and points to Java 21+, use it
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        local version
        version=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1 | sed 's/.*"\([0-9][0-9]*\).*/\1/')
        if [ "${version:-0}" -ge 21 ] 2>/dev/null; then
            ok "Java ${version} found (JAVA_HOME=$JAVA_HOME)"
            return 0
        fi
    fi

    # Let JBang resolve Java 21
    debug "Ensuring Java 21 is available..."
    local java_home
    java_home=$(jbang jdk home 21 2>/dev/null) || {
        info "Downloading Java 21 via JBang (one-time)..."
        jbang jdk install 21
        java_home=$(jbang jdk home 21 2>/dev/null) || {
            err "Failed to install Java 21 via JBang."
            exit 1
        }
    }

    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    ok "Java 21 ready (JAVA_HOME=$JAVA_HOME)"
}

# ─── Step 3: Maven build ───────────────────────────────────────────────────────

needs_build() {
    if [ -f "$MARKER_FILE" ] && [ -f "$JAR_PATH" ]; then
        return 1  # no build needed
    fi
    return 0  # build needed
}

maven_build() {
    header "Building System Monitor"

    if ! needs_build; then
        ok "JAR already built (use --force-build to rebuild)"
        return 0
    fi

    info "Running Maven build (this may take a few minutes on first run)..."
    debug "JAVA_HOME=$JAVA_HOME"
    debug "Running: ./mvnw package -pl :${MODULE_ARTIFACT} -am -DskipTests"

    local start=$SECONDS
    (cd "$PROJECT_ROOT" && ./mvnw package -pl ":${MODULE_ARTIFACT}" -am -DskipTests 2>&1 | while IFS= read -r line; do
        case "$line" in
            *"BUILD SUCCESS"*)   printf "${GREEN}  ▸ %s${NC}\n" "$line" ;;
            *"BUILD FAILURE"*)   printf "${RED}  ▸ %s${NC}\n" "$line" ;;
            *"--- "*":"*" ---"*) printf "${DIM}  ▸ %s${NC}\n" "$line" ;;
            *"[ERROR]"*)         printf "${RED}  %s${NC}\n" "$line" ;;
            *"Downloading"*)     ;;
            *"Downloaded"*)      ;;
            *"[WARNING]"*)       ;;
        esac
    done)

    if [ ! -f "$JAR_PATH" ]; then
        err "Build failed — JAR not found at $JAR_PATH"
        echo ""
        echo "Run manually to see full output:"
        echo "  export JAVA_HOME=$JAVA_HOME"
        echo "  cd $PROJECT_ROOT"
        echo "  ./mvnw package -pl :${MODULE_ARTIFACT} -am -DskipTests"
        exit 1
    fi

    touch "$MARKER_FILE"

    local elapsed=$(( SECONDS - start ))
    local mins=$(( elapsed / 60 ))
    local secs=$(( elapsed % 60 ))
    if [ "$mins" -gt 0 ]; then
        ok "Build complete (${mins}m ${secs}s)"
    else
        ok "Build complete (${secs}s)"
    fi
}

# ─── Step 4: Load .env ─────────────────────────────────────────────────────────

load_env() {
    local env_file="${JCLAW_ENV_FILE:-$PROJECT_ROOT/docker-compose/.env}"

    if [ -f "$env_file" ]; then
        set -a
        while IFS='=' read -r key value; do
            [[ "$key" =~ ^[[:space:]]*# ]] && continue
            [[ -z "$key" ]] && continue
            # Only set if not already in environment
            if [ -z "${!key:-}" ] && [ -n "$value" ]; then
                export "$key=$value"
            fi
        done < "$env_file"
        set +a
        debug "Loaded config from $env_file"
    fi
}

# ─── Step 5: Validate and launch ───────────────────────────────────────────────

validate_config() {
    local missing=()

    if [ -z "${TELEGRAM_BOT_TOKEN:-}" ]; then
        missing+=("TELEGRAM_BOT_TOKEN")
    fi
    if [ -z "${TELEGRAM_CHAT_ID:-}" ]; then
        missing+=("TELEGRAM_CHAT_ID")
    fi
    if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
        missing+=("ANTHROPIC_API_KEY")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        warn "Missing required environment variables:"
        for var in "${missing[@]}"; do
            printf "  ${YELLOW}•${NC} %s\n" "$var"
        done
        echo ""
        echo "Set them in your environment or in \$JCLAW_ENV_FILE:"
        echo "  export TELEGRAM_BOT_TOKEN=your-bot-token"
        echo "  export TELEGRAM_CHAT_ID=your-chat-id"
        echo "  export ANTHROPIC_API_KEY=your-api-key"
        echo ""
        echo "Starting anyway — the app will fail at runtime if these are needed."
        echo ""
    fi

    # Validate Telegram token if present
    if [ -n "${TELEGRAM_BOT_TOKEN:-}" ] && command -v curl &>/dev/null; then
        debug "Validating Telegram bot token..."
        local response
        if response=$(curl -sf "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe" 2>/dev/null); then
            local bot_username
            bot_username=$(echo "$response" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
            ok "Telegram bot validated: @${bot_username}"
            printf "  ${BOLD}Bot link: https://t.me/${bot_username}${NC}\n"
            echo ""
        else
            warn "Telegram token validation failed — check TELEGRAM_BOT_TOKEN"
        fi
    fi
}

launch() {
    header "Starting System Monitor"

    local schedule="${MONITOR_SCHEDULE:-0 7 * * *}"
    local timezone="${MONITOR_TIMEZONE:-America/New_York}"
    local port="${GATEWAY_PORT:-8080}"

    info "Gateway: http://localhost:${port}"
    info "Schedule: ${schedule} (${timezone})"
    if [ -n "${TELEGRAM_CHAT_ID:-}" ]; then
        info "Reports → Telegram chat ${TELEGRAM_CHAT_ID}"
    fi
    echo ""

    exec "$JAVA_HOME/bin/java" \
        -Xms256m -Xmx512m \
        -XX:+UseZGC \
        -jar "$JAR_PATH"
}

# ─── Help ──────────────────────────────────────────────────────────────────────

print_help() {
    echo "Usage: ./run.sh [options]"
    echo ""
    echo "Zero-prerequisite launcher for the JClaw System Monitor example."
    echo "Installs JBang + Java 21 automatically, builds the fat JAR, and runs."
    echo ""
    echo "Options:"
    echo "  --build-only     Build the JAR and exit (don't start)"
    echo "  --skip-build     Skip build, start immediately (assumes prior build)"
    echo "  --force-build    Force rebuild even if JAR exists"
    echo "  --help, help     Print this help"
    echo ""
    echo "Required environment variables:"
    echo "  TELEGRAM_BOT_TOKEN    Telegram Bot API token"
    echo "  TELEGRAM_CHAT_ID      Chat ID to receive health reports"
    echo "  ANTHROPIC_API_KEY     Anthropic API key"
    echo ""
    echo "Optional environment variables:"
    echo "  MONITOR_SCHEDULE      Cron expression (default: \"0 7 * * *\")"
    echo "  MONITOR_TIMEZONE      Timezone (default: America/New_York)"
    echo "  GATEWAY_PORT          HTTP port (default: 8080)"
    echo "  JCLAW_ENV_FILE        Path to .env file for additional config"
    echo ""
    echo "Examples:"
    echo "  ./run.sh                                    # build + start"
    echo "  ./run.sh --build-only                       # just build"
    echo "  MONITOR_SCHEDULE='0 */6 * * *' ./run.sh     # report every 6 hours"
}

# ─── Main ──────────────────────────────────────────────────────────────────────

main() {
    local build_only=false
    local skip_build=false
    local force_build=false

    for arg in "$@"; do
        case "$arg" in
            --build-only)   build_only=true ;;
            --skip-build)   skip_build=true ;;
            --force-build)  force_build=true ;;
            -h|--help|help) print_help; exit 0 ;;
            *)
                err "Unknown option: $arg"
                echo "Run './run.sh --help' for usage."
                exit 1
                ;;
        esac
    done

    header "JClaw System Monitor"

    # Step 1: JBang (for Java management)
    ensure_jbang

    # Step 2: Java 21
    ensure_java

    # Step 3: Build
    if [ "$skip_build" = false ]; then
        if [ "$force_build" = true ]; then
            rm -f "$MARKER_FILE"
        fi
        maven_build
    fi

    if [ "$build_only" = true ]; then
        ok "Build complete. Start with: ./run.sh --skip-build"
        exit 0
    fi

    # Step 4: Load .env config
    load_env

    # Step 5: Validate and launch
    validate_config
    launch
}

main "$@"
