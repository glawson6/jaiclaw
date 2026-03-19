#!/usr/bin/env bash
#
# JClaw Bootstrap — zero-prerequisite launcher (no Docker required)
#
# Installs JBang (if needed) → JBang installs Java 21 → Maven builds the
# gateway JAR → JBang launches the gateway in-process.
#
# The only real prerequisite is curl and a shell.
#
# Usage:
#   ./bootstrap.sh                  # build (if needed) and start gateway
#   ./bootstrap.sh --shell          # build (if needed) and start interactive CLI shell
#   ./bootstrap.sh --build-only     # just build, don't start
#   ./bootstrap.sh --skip-build     # skip build, start gateway (assumes prior build)
#   ./bootstrap.sh help             # print usage
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source persistent config pointer (written by quickstart --reconfigure or first-run prompt)
# JClaw.java already reads JCLAW_ENV_FILE from the environment
[ -f "$HOME/.jclawrc" ] && source "$HOME/.jclawrc"

# ─── Colors ───────────────────────────────────────────────────────────────────
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

# ─── Step 1: Ensure JBang ────────────────────────────────────────────────────

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

# ─── Step 2: Ensure Java 21 via JBang ────────────────────────────────────────

ensure_java() {
    # Let JBang resolve Java 21 — it downloads if needed
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

# ─── Step 3: Maven build ─────────────────────────────────────────────────────

MARKER_FILE="$SCRIPT_DIR/jclaw-gateway-app/target/.jclaw-installed"

needs_build() {
    # Check if the install marker exists and the gateway JAR is present
    local gateway_jar="$SCRIPT_DIR/jclaw-gateway-app/target/jclaw-gateway-app-0.1.0-SNAPSHOT.jar"
    if [ -f "$MARKER_FILE" ] && [ -f "$gateway_jar" ]; then
        return 1  # no build needed
    fi
    return 0  # build needed
}

maven_build() {
    header "Building JClaw"

    if ! needs_build; then
        ok "Gateway JAR already built (use --force-build to rebuild)"
        return 0
    fi

    info "Running Maven build (this may take a few minutes on first run)..."
    debug "JAVA_HOME=$JAVA_HOME"
    debug "Running: ./mvnw install -Pjbang -DskipTests"

    local start=$SECONDS
    (cd "$SCRIPT_DIR" && ./mvnw install -Pjbang -DskipTests 2>&1 | while IFS= read -r line; do
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

    # Verify the build succeeded
    local gateway_jar="$SCRIPT_DIR/jclaw-gateway-app/target/jclaw-gateway-app-0.1.0-SNAPSHOT.jar"
    if [ ! -f "$gateway_jar" ]; then
        err "Build failed — gateway JAR not found at $gateway_jar"
        echo ""
        echo "Run manually to see full output:"
        echo "  export JAVA_HOME=$JAVA_HOME"
        echo "  ./mvnw install -DskipTests"
        exit 1
    fi

    # Write marker so we skip builds next time
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

# ─── Step 4: Launch gateway via JBang ─────────────────────────────────────────

launch_gateway() {
    header "Starting JClaw Gateway"

    info "Launching gateway (no Docker)..."
    echo ""
    (cd "$SCRIPT_DIR" && jbang JClaw.java "$@")
}

launch_shell() {
    header "Starting JClaw Interactive Shell"

    echo "Starting interactive shell..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Type 'onboard' to run the setup wizard${NC}\n"
    echo ""

    (cd "$SCRIPT_DIR" && ./mvnw spring-boot:run -pl jclaw-shell -q)
}

# ─── Help ─────────────────────────────────────────────────────────────────────

print_help() {
    echo "Usage: ./bootstrap.sh [options] [-- gateway-args...]"
    echo ""
    echo "Zero-prerequisite JClaw launcher. Installs JBang + Java 21 automatically."
    echo ""
    echo "Options:"
    echo "  --shell          Start the interactive CLI shell instead of the gateway"
    echo "  --build-only     Build the gateway JAR and exit (don't start)"
    echo "  --skip-build     Skip the build step (assumes prior build)"
    echo "  --force-build    Force a rebuild even if the JAR exists"
    echo "  --help, help     Print this help"
    echo ""
    echo "Gateway arguments (passed after --):"
    echo "  gateway          Start gateway (default)"
    echo "  telegram         Validate Telegram token and start gateway"
    echo ""
    echo "Examples:"
    echo "  ./bootstrap.sh                     # build + start gateway"
    echo "  ./bootstrap.sh --shell             # build + start interactive shell"
    echo "  ./bootstrap.sh --build-only        # just build"
    echo "  ./bootstrap.sh -- telegram         # build + start with Telegram"
    echo "  ./bootstrap.sh --skip-build        # start without building"
    echo ""
    echo "Configuration: edit \$JCLAW_ENV_FILE (default: docker-compose/.env) to set API keys and channel tokens."
    echo "Run './quickstart.sh --reconfigure' to change the config location."
    echo ""
    echo "For Docker-based deployment, use: ./quickstart.sh or jbang JClawDocker.java"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    local build_only=false
    local skip_build=false
    local force_build=false
    local launch_mode=gateway
    local gateway_args=()

    # Parse arguments
    local past_separator=false
    for arg in "$@"; do
        if [ "$past_separator" = true ]; then
            gateway_args+=("$arg")
            continue
        fi
        case "$arg" in
            --shell)        launch_mode=shell ;;
            --build-only)   build_only=true ;;
            --skip-build)   skip_build=true ;;
            --force-build)  force_build=true ;;
            -h|--help|help) print_help; exit 0 ;;
            --)             past_separator=true ;;
            *)
                err "Unknown option: $arg"
                echo "Run './bootstrap.sh help' for usage."
                exit 1
                ;;
        esac
    done

    local total_start=$SECONDS

    header "JClaw Bootstrap"

    # Step 1: JBang
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

    # Step 4: Launch
    if [ "$build_only" = true ]; then
        local total_elapsed=$(( SECONDS - total_start ))
        ok "Done (${total_elapsed}s). Start the gateway with: jbang JClaw.java"
    elif [ "$launch_mode" = "shell" ]; then
        launch_shell
    else
        launch_gateway "${gateway_args[@]+"${gateway_args[@]}"}"
    fi
}

main "$@"
