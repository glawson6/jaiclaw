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
#   ./setup.sh --cron-manager # build + run cron-manager
#   ./setup.sh --build-only # build only, don't launch
#
set -euo pipefail

# Shared helpers (colors, logging, API key resolution)
source "$(dirname "$0")/scripts/common.sh"

# ─── Parse args ───────────────────────────────────────────────────────────────

MODE="shell"
for arg in "$@"; do
    case "$arg" in
        --gateway)      MODE="gateway" ;;
        --cron-manager) MODE="cron-manager" ;;
        --build-only)   MODE="build-only" ;;
        --help|-h)
            echo "Usage: ./setup.sh [--gateway|--cron-manager|--build-only]"
            echo ""
            echo "Options:"
            echo "  --gateway       Build and run the gateway server instead of the shell"
            echo "  --cron-manager  Build and run the cron-manager"
            echo "  --build-only    Build only, don't launch anything"
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
        version=$("$java_cmd" -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d. -f1)
        if [ "$version" -ge "$REQUIRED_JAVA_VERSION" ] 2>/dev/null; then
            # Set JAVA_HOME if not already pointing to the right place
            if [ -z "${JAVA_HOME:-}" ]; then
                JAVA_HOME="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current"
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
        set +u; source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"; set -u
        ok "SDKMAN installed"
    else
        # shellcheck source=/dev/null
        set +u; source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"; set -u
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
    # Source SDKMAN if available (disable -u temporarily — SDKMAN uses unbound vars)
    if [ -s "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" ]; then
        set +u
        source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
        set -u
    fi

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

    warn "After onboarding completes, restart the shell to activate your LLM configuration."
    echo ""

    ./mvnw spring-boot:run -pl jclaw-shell -q
}

launch_gateway() {
    header "Starting JClaw Gateway"

    resolve_api_key

    echo "The gateway server is starting on port 8080."
    print_security_info
    echo ""
    echo "Test with:"
    print_api_curl_example 8080
    echo ""
    echo "Or with httpie:"
    print_api_httpie_example 8080
    echo ""

    ./mvnw spring-boot:run -pl jclaw-gateway-app -q
}

launch_cron_manager() {
    header "Starting JClaw Cron Manager"

    resolve_api_key

    echo "The cron-manager is starting on port 8090."
    print_security_info
    echo ""
    echo "Test with:"
    local cron_key="${RESOLVED_API_KEY:-<your-api-key>}"
    printf "  ${BOLD}curl http://localhost:8090/mcp \\\\${NC}\n"
    printf "  ${BOLD}  -H \"X-API-Key: ${cron_key}\"${NC}\n"
    echo ""
    printf "  ${DIM}Type 'cron-status' for cron job overview${NC}\n"
    printf "  ${DIM}Type 'cron-list' to list all jobs${NC}\n"
    echo ""

    ./mvnw spring-boot:run -pl jclaw-cron-manager
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    header "JClaw Developer Setup"

    ensure_java
    build

    case "$MODE" in
        shell)        launch_shell ;;
        gateway)      launch_gateway ;;
        cron-manager) launch_cron_manager ;;
        build-only)   ok "Build finished. Run with: ./mvnw spring-boot:run -pl jclaw-shell" ;;
    esac
}

main
