#!/usr/bin/env bash
#
# JClaw Developer Setup — build from source and launch the shell
#
# Installs Java 21 via SDKMAN if not present, builds all modules,
# and starts the interactive shell with the onboarding wizard.
#
# Usage:
#   ./setup.sh              # build + run shell
#   ./setup.sh --gateway    # build + run gateway instead
#   ./setup.sh --build-only # build only, don't launch
#
set -euo pipefail

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

# ─── Parse args ───────────────────────────────────────────────────────────────

MODE="shell"
for arg in "$@"; do
    case "$arg" in
        --gateway)    MODE="gateway" ;;
        --build-only) MODE="build-only" ;;
        --help|-h)
            echo "Usage: ./setup.sh [--gateway|--build-only]"
            echo ""
            echo "Options:"
            echo "  --gateway     Build and run the gateway server instead of the shell"
            echo "  --build-only  Build only, don't launch anything"
            echo ""
            exit 0
            ;;
    esac
done

# ─── Ensure we're in the project root ─────────────────────────────────────────

if [ ! -f "pom.xml" ] || [ ! -d "jclaw-core" ]; then
    err "Run this script from the JClaw project root directory."
    exit 1
fi

# ─── Java 21 ──────────────────────────────────────────────────────────────────

REQUIRED_JAVA_VERSION=21

detect_java() {
    local java_cmd=""

    # 1. Check JAVA_HOME
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        java_cmd="${JAVA_HOME}/bin/java"
    # 2. Check PATH
    elif command -v java &>/dev/null; then
        java_cmd="java"
    fi

    if [ -n "$java_cmd" ]; then
        local version
        version=$("$java_cmd" -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
        if [ "$version" -ge "$REQUIRED_JAVA_VERSION" ] 2>/dev/null; then
            # Set JAVA_HOME if not already pointing to the right place
            if [ -z "${JAVA_HOME:-}" ]; then
                JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")" 2>/dev/null || true)
            fi
            ok "Java $version detected (JAVA_HOME=$JAVA_HOME)"
            export JAVA_HOME
            return 0
        else
            warn "Java $version found, but Java $REQUIRED_JAVA_VERSION+ is required"
        fi
    fi

    return 1
}

install_java() {
    header "Installing Java $REQUIRED_JAVA_VERSION"

    # Install SDKMAN if not present
    if [ ! -d "${SDKMAN_DIR:-$HOME/.sdkman}" ]; then
        info "Installing SDKMAN..."
        curl -s "https://get.sdkman.io" | bash
        # shellcheck source=/dev/null
        source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
        ok "SDKMAN installed"
    else
        # shellcheck source=/dev/null
        source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
        ok "SDKMAN already installed"
    fi

    # Install Java 21
    info "Installing Java $REQUIRED_JAVA_VERSION via SDKMAN..."
    sdk install java 21.0.5-tem <<< "y" 2>/dev/null || sdk install java 21.0.5-tem || true

    # Activate
    sdk use java 21.0.5-tem 2>/dev/null || true
    export JAVA_HOME="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current"

    # Verify
    if detect_java; then
        return 0
    else
        err "Failed to install Java $REQUIRED_JAVA_VERSION. Install manually:"
        echo "  https://adoptium.net/temurin/releases/?version=21"
        exit 1
    fi
}

ensure_java() {
    if ! detect_java; then
        install_java
    fi
}

# ─── Build ────────────────────────────────────────────────────────────────────

build() {
    header "Building JClaw"

    info "Building all modules (this may take a few minutes on first run)..."
    ./mvnw install -DskipTests -q

    ok "Build complete"
}

# ─── Launch ───────────────────────────────────────────────────────────────────

launch_shell() {
    header "Starting JClaw Shell"

    echo "The interactive shell is starting. Run the onboarding wizard to configure:"
    echo ""
    printf "  ${BOLD}jclaw> onboard${NC}\n"
    echo ""

    ./mvnw spring-boot:run -pl jclaw-shell -q
}

launch_gateway() {
    header "Starting JClaw Gateway"

    echo "The gateway server is starting on port 8080."
    echo ""
    echo "Test with:"
    printf "  ${BOLD}curl -X POST http://localhost:8080/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  -H \"Content-Type: application/json\" \\\\${NC}\n"
    printf "  ${BOLD}  -d '{\"content\": \"hello\"}'${NC}\n"
    echo ""

    ./mvnw spring-boot:run -pl jclaw-gateway-app -q
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    header "JClaw Developer Setup"

    ensure_java
    build

    case "$MODE" in
        shell)      launch_shell ;;
        gateway)    launch_gateway ;;
        build-only) ok "Build finished. Run with: ./mvnw spring-boot:run -pl jclaw-shell" ;;
    esac
}

main
