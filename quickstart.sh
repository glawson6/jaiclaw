#!/usr/bin/env bash
#
# JClaw Quickstart — Docker-based zero-friction launcher
#
# Prerequisites: Docker (with Docker Compose)
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/jclaw/jclaw/main/quickstart.sh | bash
#   -- or --
#   ./quickstart.sh
#
set -euo pipefail

JCLAW_REPO="https://github.com/jclaw/jclaw.git"
JCLAW_DIR="${JCLAW_DIR:-jclaw}"

# Detect if we're already inside the JClaw repo (./quickstart.sh vs curl | bash)
INSIDE_REPO=false
if [ -f "./mvnw" ] && [ -f "./pom.xml" ]; then
    INSIDE_REPO=true
    JCLAW_DIR="."
fi

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
debug() { printf "${DIM}  … %s${NC}\n" "$*"; }

# Run a command with a description, showing elapsed time on completion
# Usage: run_step "description" command args...
run_step() {
    local desc="$1"
    shift
    info "$desc"
    local start=$SECONDS
    "$@"
    local elapsed=$(( SECONDS - start ))
    if [ "$elapsed" -gt 1 ]; then
        debug "done (${elapsed}s)"
    fi
}

# ─── Preflight checks ────────────────────────────────────────────────────────

check_docker() {
    debug "Checking for Docker..."
    if ! command -v docker &>/dev/null; then
        err "Docker is not installed."
        echo ""
        echo "Install Docker Desktop:"
        echo "  macOS:   https://docs.docker.com/desktop/install/mac-install/"
        echo "  Linux:   https://docs.docker.com/engine/install/"
        echo "  Windows: https://docs.docker.com/desktop/install/windows-install/"
        exit 1
    fi

    debug "Checking Docker daemon..."
    if ! docker info &>/dev/null; then
        err "Docker daemon is not running. Please start Docker Desktop and try again."
        exit 1
    fi

    debug "Checking Docker Compose..."
    if ! docker compose version &>/dev/null; then
        err "Docker Compose is not available. Install Docker Desktop (includes Compose V2)."
        exit 1
    fi

    ok "Docker is available ($(docker compose version --short 2>/dev/null || echo 'unknown'))"
}

check_java() {
    debug "Checking for Java 21+..."

    # Source SDKMAN if available but not yet initialized (common in non-interactive shells)
    if [ -z "${SDKMAN_DIR:-}" ] && [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        debug "Sourcing SDKMAN..."
        export SDKMAN_DIR="$HOME/.sdkman"
        source "$SDKMAN_DIR/bin/sdkman-init.sh"
    fi

    # Check if JAVA_HOME is set and points to Java 21+
    if [ -n "${JAVA_HOME:-}" ]; then
        local java_cmd="${JAVA_HOME}/bin/java"
        debug "Checking JAVA_HOME=$JAVA_HOME"
        if [ -x "$java_cmd" ]; then
            local version
            version=$("$java_cmd" -version 2>&1 | head -1 | sed 's/.*"\([0-9][0-9]*\).*/\1/')
            if [ "$version" -ge 21 ] 2>/dev/null; then
                ok "Java $version found at JAVA_HOME ($JAVA_HOME)"
                return 0
            else
                debug "JAVA_HOME has Java $version (need 21+), checking PATH..."
            fi
        fi
    fi

    # Check PATH
    if command -v java &>/dev/null; then
        local java_path
        java_path=$(command -v java)
        debug "Checking PATH java at $java_path"
        local version
        version=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9][0-9]*\).*/\1/')
        if [ "$version" -ge 21 ] 2>/dev/null; then
            ok "Java $version found on PATH ($java_path)"
            return 0
        else
            debug "PATH java is version $version (need 21+)"
        fi
    fi

    # Check common SDKMAN Java 21 locations as a last resort
    local sdkman_java="${HOME}/.sdkman/candidates/java/current/bin/java"
    if [ -x "$sdkman_java" ]; then
        debug "Checking SDKMAN default at $sdkman_java"
        local version
        version=$("$sdkman_java" -version 2>&1 | head -1 | sed 's/.*"\([0-9][0-9]*\).*/\1/')
        if [ "$version" -ge 21 ] 2>/dev/null; then
            export JAVA_HOME="${HOME}/.sdkman/candidates/java/current"
            export PATH="${JAVA_HOME}/bin:${PATH}"
            ok "Java $version found via SDKMAN ($sdkman_java)"
            return 0
        fi
    fi

    debug "No Java 21+ found"
    return 1
}

# ─── Clone or update ─────────────────────────────────────────────────────────

clone_repo() {
    if [ "$INSIDE_REPO" = true ]; then
        ok "Running from JClaw source directory ($(pwd))"
        return 0
    fi

    if [ -d "$JCLAW_DIR/.git" ]; then
        run_step "Pulling latest changes..." git -C "$JCLAW_DIR" pull --ff-only 2>/dev/null || warn "Could not pull latest (offline?). Using existing code."
    else
        run_step "Cloning JClaw repository..." git clone "$JCLAW_REPO" "$JCLAW_DIR"
    fi
    ok "Source code ready"
}

# ─── Build Docker image ──────────────────────────────────────────────────────

build_image() {
    header "Building Docker Image"

    # Check if we can build from source (requires Java 21)
    if check_java; then
        info "Building gateway Docker image with Maven + JKube..."
        info "This may take several minutes on the first run (downloading dependencies + compiling)."
        debug "Running: ./mvnw package k8s:build -pl jclaw-gateway-app -am -Pk8s -DskipTests"
        local start=$SECONDS
        (cd "$JCLAW_DIR" && ./mvnw package k8s:build -pl jclaw-gateway-app -am -Pk8s -DskipTests 2>&1 | while IFS= read -r line; do
            # Show Maven phase transitions and key events
            case "$line" in
                *"BUILD SUCCESS"*)  printf "${GREEN}  ▸ %s${NC}\n" "$line" ;;
                *"BUILD FAILURE"*)  printf "${RED}  ▸ %s${NC}\n" "$line" ;;
                *"--- "*":"*" ---"*) printf "${DIM}  ▸ %s${NC}\n" "$line" ;;
                *"Downloading"*)    ;; # suppress individual download lines
                *"Downloaded"*)     ;; # suppress individual download lines
                *"[ERROR]"*)        printf "${RED}  %s${NC}\n" "$line" ;;
                *"[WARNING]"*)      printf "${YELLOW}  %s${NC}\n" "$line" ;;
            esac
        done)
        local elapsed=$(( SECONDS - start ))
        local mins=$(( elapsed / 60 ))
        local secs=$(( elapsed % 60 ))
        if [ "$mins" -gt 0 ]; then
            ok "Docker image built: io.jclaw/jclaw-gateway-app:0.1.0-SNAPSHOT (${mins}m ${secs}s)"
        else
            ok "Docker image built: io.jclaw/jclaw-gateway-app:0.1.0-SNAPSHOT (${secs}s)"
        fi
    else
        # No Java — check if the image already exists
        debug "Checking for pre-built Docker image..."
        if docker image inspect io.jclaw/jclaw-gateway-app:0.1.0-SNAPSHOT &>/dev/null; then
            ok "Docker image already exists (skipping build)"
        else
            err "Cannot build Docker image — Java 21+ is required for the Maven build."
            echo ""
            echo "Options:"
            echo "  1. Install Java 21:  curl -s https://get.sdkman.io | bash && sdk install java 21.0.9-oracle"
            echo "  2. Use the developer setup instead:  ./setup.sh"
            echo ""
            exit 1
        fi
    fi
}

# ─── Start stack ──────────────────────────────────────────────────────────────

has_api_key() {
    [ -n "${OPENAI_API_KEY:-}" ] || [ -n "${ANTHROPIC_API_KEY:-}" ]
}

start_stack() {
    header "Starting JClaw"

    local compose_dir="$JCLAW_DIR/docker-compose"

    if [ ! -d "$compose_dir" ]; then
        err "Docker compose directory not found: $compose_dir"
        exit 1
    fi

    # Create .env from example if it doesn't exist
    if [ ! -f "$compose_dir/.env" ] && [ -f "$compose_dir/.env.example" ]; then
        cp "$compose_dir/.env.example" "$compose_dir/.env"
        info "Created .env from template"
    fi

    # Write env-provided API keys and enable the matching provider
    if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
        sed -i.bak "s|^ANTHROPIC_API_KEY=.*|ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}|" "$compose_dir/.env"
        sed -i.bak "s|^ANTHROPIC_ENABLED=.*|ANTHROPIC_ENABLED=true|" "$compose_dir/.env"
        sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=anthropic|" "$compose_dir/.env"
        rm -f "$compose_dir/.env.bak"
    fi
    if [ -n "${OPENAI_API_KEY:-}" ]; then
        sed -i.bak "s|^OPENAI_API_KEY=.*|OPENAI_API_KEY=${OPENAI_API_KEY}|" "$compose_dir/.env"
        sed -i.bak "s|^OPENAI_ENABLED=.*|OPENAI_ENABLED=true|" "$compose_dir/.env"
        sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=openai|" "$compose_dir/.env"
        rm -f "$compose_dir/.env.bak"
    fi

    # Decide whether to include Ollama
    local use_ollama=true
    if has_api_key; then
        use_ollama=false
        ok "API key detected — skipping Ollama (you can start it later with: docker compose --profile ollama up -d)"
    else
        # No API key — enable Ollama as the provider
        sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=ollama|" "$compose_dir/.env"
        sed -i.bak "s|^OLLAMA_ENABLED=.*|OLLAMA_ENABLED=true|" "$compose_dir/.env"
        sed -i.bak "s|^ANTHROPIC_ENABLED=.*|ANTHROPIC_ENABLED=false|" "$compose_dir/.env"
        rm -f "$compose_dir/.env.bak"
    fi

    debug "Starting containers..."
    local start=$SECONDS
    if [ "$use_ollama" = true ]; then
        info "No API key set — starting with Ollama (local LLM). This pulls a ~3GB image on first run."
        docker compose -f "$compose_dir/docker-compose.yml" --profile ollama up -d
    else
        docker compose -f "$compose_dir/docker-compose.yml" up -d
    fi
    local elapsed=$(( SECONDS - start ))
    ok "Stack is running (${elapsed}s)"

    # Pull a model into Ollama if we started it
    if [ "$use_ollama" = true ]; then
        echo ""
        info "Pulling Ollama model (this may take a few minutes on first run)..."
        debug "Running: ollama pull llama3.2"
        start=$SECONDS
        docker compose -f "$compose_dir/docker-compose.yml" --profile ollama exec -T ollama ollama pull llama3.2 2>&1 | while IFS= read -r line; do
            # Show pull progress
            case "$line" in
                *"pulling"*|*"verifying"*|*"writing"*|*"success"*)
                    printf "${DIM}  ▸ %s${NC}\n" "$line" ;;
            esac
        done || warn "Could not pull model — you can do this later"
        elapsed=$(( SECONDS - start ))
        ok "Ollama model ready (${elapsed}s)"
    fi
}

# ─── Telegram setup ──────────────────────────────────────────────────────────

TELEGRAM_BOT_USERNAME=""

setup_telegram() {
    local compose_dir="$JCLAW_DIR/docker-compose"
    echo ""
    read -rp "$(printf "${CYAN}▸${NC} Set up Telegram bot? (y/N): ")" setup_tg
    if [[ ! "$setup_tg" =~ ^[Yy]$ ]]; then
        return
    fi

    echo ""
    echo "  To get a bot token:"
    echo "    1. Open Telegram and message @BotFather"
    echo "    2. Send /newbot and follow the prompts"
    echo "    3. Copy the token"
    echo ""
    read -rp "  Enter your Telegram bot token (from @BotFather): " tg_token
    if [ -z "$tg_token" ]; then
        warn "No token provided. Skipping Telegram setup."
        return
    fi

    # Validate
    info "Validating token..."
    local response
    response=$(curl -sf "https://api.telegram.org/bot${tg_token}/getMe" 2>/dev/null) || {
        warn "Token validation failed. Saving anyway — you can fix it later in .env"
        sed -i.bak "s|^TELEGRAM_BOT_TOKEN=.*|TELEGRAM_BOT_TOKEN=${tg_token}|" "$compose_dir/.env"
        rm -f "$compose_dir/.env.bak"
        return
    }
    local bot_username
    bot_username=$(echo "$response" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
    ok "Bot validated: @${bot_username}"

    # Write to .env
    sed -i.bak "s|^TELEGRAM_BOT_TOKEN=.*|TELEGRAM_BOT_TOKEN=${tg_token}|" "$compose_dir/.env"
    rm -f "$compose_dir/.env.bak"

    TELEGRAM_BOT_USERNAME="$bot_username"
}

# ─── Done ─────────────────────────────────────────────────────────────────────

print_success() {
    header "JClaw is Running"

    echo "Test it:"
    echo ""
    printf "  ${BOLD}curl -X POST http://localhost:8080/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  -H \"Content-Type: application/json\" \\\\${NC}\n"
    printf "  ${BOLD}  -d '{\"content\": \"hello\"}'${NC}\n"
    echo ""
    echo "Health check:"
    printf "  ${BOLD}curl http://localhost:8080/api/health${NC}\n"
    echo ""
    local compose_dir="${JCLAW_DIR}/docker-compose"
    echo "View logs:"
    printf "  ${BOLD}docker compose -f ${compose_dir}/docker-compose.yml logs -f gateway${NC}\n"
    echo ""
    echo "Stop:"
    printf "  ${BOLD}docker compose -f ${compose_dir}/docker-compose.yml down${NC}\n"
    echo ""
    if [ -n "$TELEGRAM_BOT_USERNAME" ]; then
        echo "Telegram bot:"
        printf "  ${BOLD}https://t.me/${TELEGRAM_BOT_USERNAME}${NC}\n"
        echo ""
    fi
    echo "To add API keys or channel tokens, edit:"
    printf "  ${BOLD}${compose_dir}/.env${NC}\n"
    echo ""
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    local total_start=$SECONDS

    header "JClaw Quickstart"
    debug "JCLAW_DIR=$JCLAW_DIR  INSIDE_REPO=$INSIDE_REPO"

    check_docker
    clone_repo
    build_image
    setup_telegram
    start_stack

    local total_elapsed=$(( SECONDS - total_start ))
    local mins=$(( total_elapsed / 60 ))
    local secs=$(( total_elapsed % 60 ))
    if [ "$mins" -gt 0 ]; then
        ok "Total time: ${mins}m ${secs}s"
    else
        ok "Total time: ${secs}s"
    fi

    print_success
}

main "$@"
