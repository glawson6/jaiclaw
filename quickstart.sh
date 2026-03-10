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

# ─── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()  { printf "${CYAN}▸${NC} %s\n" "$*"; }
ok()    { printf "${GREEN}✓${NC} %s\n" "$*"; }
warn()  { printf "${YELLOW}!${NC} %s\n" "$*"; }
err()   { printf "${RED}✗${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}${CYAN}── %s ──${NC}\n\n" "$*"; }

# ─── Preflight checks ────────────────────────────────────────────────────────

check_docker() {
    if ! command -v docker &>/dev/null; then
        err "Docker is not installed."
        echo ""
        echo "Install Docker Desktop:"
        echo "  macOS:   https://docs.docker.com/desktop/install/mac-install/"
        echo "  Linux:   https://docs.docker.com/engine/install/"
        echo "  Windows: https://docs.docker.com/desktop/install/windows-install/"
        exit 1
    fi

    if ! docker info &>/dev/null; then
        err "Docker daemon is not running. Please start Docker Desktop and try again."
        exit 1
    fi

    if ! docker compose version &>/dev/null; then
        err "Docker Compose is not available. Install Docker Desktop (includes Compose V2)."
        exit 1
    fi

    ok "Docker is available"
}

check_java() {
    # Check if JAVA_HOME is set and points to Java 21+
    if [ -n "${JAVA_HOME:-}" ]; then
        local java_cmd="${JAVA_HOME}/bin/java"
        if [ -x "$java_cmd" ]; then
            local version
            version=$("$java_cmd" -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
            if [ "$version" -ge 21 ] 2>/dev/null; then
                ok "Java $version found at JAVA_HOME"
                return 0
            fi
        fi
    fi

    # Check PATH
    if command -v java &>/dev/null; then
        local version
        version=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
        if [ "$version" -ge 21 ] 2>/dev/null; then
            ok "Java $version found on PATH"
            return 0
        fi
    fi

    return 1
}

# ─── Clone or update ─────────────────────────────────────────────────────────

clone_repo() {
    if [ -d "$JCLAW_DIR/.git" ]; then
        info "JClaw directory already exists, pulling latest..."
        git -C "$JCLAW_DIR" pull --ff-only 2>/dev/null || warn "Could not pull latest (offline?). Using existing code."
    else
        info "Cloning JClaw..."
        git clone "$JCLAW_REPO" "$JCLAW_DIR"
    fi
    ok "Source code ready"
}

# ─── Build Docker image ──────────────────────────────────────────────────────

build_image() {
    header "Building Docker Image"

    # Check if we can build from source (requires Java 21)
    if check_java; then
        info "Building gateway Docker image with Maven + JKube..."
        cd "$JCLAW_DIR"
        ./mvnw package k8s:build -pl jclaw-gateway-app -Pk8s -DskipTests -q
        cd ..
        ok "Docker image built: io.jclaw/jclaw-gateway-app:0.1.0-SNAPSHOT"
    else
        # No Java — check if the image already exists
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

start_stack() {
    header "Starting JClaw"

    cd "$JCLAW_DIR/docker-compose"

    # Create .env from example if it doesn't exist
    if [ ! -f .env ]; then
        cp .env.example .env
        info "Created .env from template"
    fi

    docker compose up -d
    ok "Stack is starting"

    echo ""
    info "Pulling Ollama model (this may take a few minutes on first run)..."
    docker compose exec -T ollama ollama pull llama3.2 || warn "Could not pull model — you can do this later"

    cd ..
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
    echo "View logs:"
    printf "  ${BOLD}cd ${JCLAW_DIR}/docker-compose && docker compose logs -f gateway${NC}\n"
    echo ""
    echo "Stop:"
    printf "  ${BOLD}cd ${JCLAW_DIR}/docker-compose && docker compose down${NC}\n"
    echo ""
    echo "To add API keys or channel tokens, edit:"
    printf "  ${BOLD}${JCLAW_DIR}/docker-compose/.env${NC}\n"
    echo ""
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    header "JClaw Quickstart"

    check_docker
    clone_repo
    build_image
    start_stack
    print_success
}

main "$@"
