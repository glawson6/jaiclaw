#!/usr/bin/env bash
#
# JClaw Quickstart — Docker-based zero-friction launcher
#
# Prerequisites: Docker (with Docker Compose)
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/jclaw/jclaw/main/quickstart.sh | bash
#   -- or --
#   ./quickstart.sh              # build + start gateway (Docker)
#   ./quickstart.sh --shell      # build + start interactive CLI shell (Docker)
#   ./quickstart.sh --cron-manager # also build + start the cron-manager sidecar
#   ./quickstart.sh --force-build  # force rebuild Docker images
#   ./quickstart.sh --reconfigure  # re-run interactive setup (provider, keys, channels)
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

# Source persistent config pointer (written by quickstart --reconfigure or first-run prompt)
[ -f "$HOME/.jclawrc" ] && source "$HOME/.jclawrc"

# Shared helpers (colors, logging, API key resolution)
source "$JCLAW_DIR/scripts/common.sh"

# Extra helpers unique to quickstart
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

    # Remove existing image when force-building
    if [ "$FORCE_BUILD" = true ]; then
        info "Force-build requested — removing existing image..."
        docker rmi io.jclaw/jclaw-gateway-app:0.1.0-SNAPSHOT 2>/dev/null || true
    fi

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

build_shell_image() {
    header "Building CLI Shell Docker Image"

    # Remove existing image when force-building
    if [ "$FORCE_BUILD" = true ]; then
        info "Force-build requested — removing existing shell image..."
        docker rmi io.jclaw/jclaw-shell:0.1.0-SNAPSHOT 2>/dev/null || true
    fi

    if check_java; then
        info "Building shell Docker image with Maven + JKube..."
        debug "Running: ./mvnw package k8s:build -pl jclaw-shell -am -Pk8s -DskipTests"
        local start=$SECONDS
        (cd "$JCLAW_DIR" && ./mvnw package k8s:build -pl jclaw-shell -am -Pk8s -DskipTests 2>&1 | while IFS= read -r line; do
            case "$line" in
                *"BUILD SUCCESS"*)  printf "${GREEN}  ▸ %s${NC}\n" "$line" ;;
                *"BUILD FAILURE"*)  printf "${RED}  ▸ %s${NC}\n" "$line" ;;
                *"--- "*":"*" ---"*) printf "${DIM}  ▸ %s${NC}\n" "$line" ;;
                *"Downloading"*)    ;;
                *"Downloaded"*)     ;;
                *"[ERROR]"*)        printf "${RED}  %s${NC}\n" "$line" ;;
                *"[WARNING]"*)      printf "${YELLOW}  %s${NC}\n" "$line" ;;
            esac
        done)
        local elapsed=$(( SECONDS - start ))
        local mins=$(( elapsed / 60 ))
        local secs=$(( elapsed % 60 ))
        if [ "$mins" -gt 0 ]; then
            ok "Docker image built: io.jclaw/jclaw-shell:0.1.0-SNAPSHOT (${mins}m ${secs}s)"
        else
            ok "Docker image built: io.jclaw/jclaw-shell:0.1.0-SNAPSHOT (${secs}s)"
        fi
    else
        debug "Checking for pre-built shell Docker image..."
        if docker image inspect io.jclaw/jclaw-shell:0.1.0-SNAPSHOT &>/dev/null; then
            ok "Shell Docker image already exists (skipping build)"
        else
            err "Cannot build shell Docker image — Java 21+ is required for the Maven build."
            exit 1
        fi
    fi
}

build_cron_manager_image() {
    header "Building Cron Manager Docker Image"

    # Remove existing image when force-building
    if [ "$FORCE_BUILD" = true ]; then
        info "Force-build requested — removing existing cron-manager image..."
        docker rmi io.jclaw/jclaw-cron-manager:0.1.0-SNAPSHOT 2>/dev/null || true
    fi

    if check_java; then
        info "Building cron-manager Docker image with Maven + JKube..."
        debug "Running: ./mvnw package k8s:build -pl jclaw-cron-manager -am -Pk8s -DskipTests"
        local start=$SECONDS
        (cd "$JCLAW_DIR" && ./mvnw package k8s:build -pl jclaw-cron-manager -am -Pk8s -DskipTests 2>&1 | while IFS= read -r line; do
            case "$line" in
                *"BUILD SUCCESS"*)  printf "${GREEN}  ▸ %s${NC}\n" "$line" ;;
                *"BUILD FAILURE"*)  printf "${RED}  ▸ %s${NC}\n" "$line" ;;
                *"--- "*":"*" ---"*) printf "${DIM}  ▸ %s${NC}\n" "$line" ;;
                *"Downloading"*)    ;;
                *"Downloaded"*)     ;;
                *"[ERROR]"*)        printf "${RED}  %s${NC}\n" "$line" ;;
                *"[WARNING]"*)      printf "${YELLOW}  %s${NC}\n" "$line" ;;
            esac
        done)
        local elapsed=$(( SECONDS - start ))
        local mins=$(( elapsed / 60 ))
        local secs=$(( elapsed % 60 ))
        if [ "$mins" -gt 0 ]; then
            ok "Docker image built: io.jclaw/jclaw-cron-manager:0.1.0-SNAPSHOT (${mins}m ${secs}s)"
        else
            ok "Docker image built: io.jclaw/jclaw-cron-manager:0.1.0-SNAPSHOT (${secs}s)"
        fi
    else
        debug "Checking for pre-built cron-manager Docker image..."
        if docker image inspect io.jclaw/jclaw-cron-manager:0.1.0-SNAPSHOT &>/dev/null; then
            ok "Cron Manager Docker image already exists (skipping build)"
        else
            err "Cannot build cron-manager Docker image — Java 21+ is required for the Maven build."
            exit 1
        fi
    fi
}

launch_shell() {
    local compose_dir="$JCLAW_DIR/docker-compose"

    header "Starting JClaw Interactive Shell"

    echo "Starting interactive shell container..."
    echo ""
    printf "  ${DIM}Type 'help' for available commands${NC}\n"
    printf "  ${DIM}Type 'chat hello' to talk to the agent${NC}\n"
    printf "  ${DIM}Type 'onboard' to run the setup wizard${NC}\n"
    echo ""

    docker compose -f "$compose_dir/docker-compose.yml" --env-file "$ENV_FILE" --profile cli run --rm cli
}

# ─── Env file resolution ─────────────────────────────────────────────────────

resolve_env_file() {
    local compose_dir="$JCLAW_DIR/docker-compose"

    # Already set (from ~/.jclawrc or environment)
    if [ -n "${JCLAW_ENV_FILE:-}" ]; then
        ENV_FILE="$JCLAW_ENV_FILE"
        ok "Using config from $ENV_FILE"
        # Create from template if missing
        if [ ! -f "$ENV_FILE" ] && [ -f "$compose_dir/.env.example" ]; then
            mkdir -p "$(dirname "$ENV_FILE")"
            cp "$compose_dir/.env.example" "$ENV_FILE"
            info "Created $ENV_FILE from template"
        fi
        return
    fi

    # Backward compat: project-local .env already exists
    if [ -f "$compose_dir/.env" ]; then
        ENV_FILE="$compose_dir/.env"
        ok "Using existing config at $ENV_FILE"
        return
    fi

    # First run — prompt the user
    echo ""
    printf "${BOLD}Where should configuration be saved?${NC}\n"
    echo "  1. ~/.jclaw/.env (recommended — persists across projects)"
    echo "  2. docker-compose/.env (project-local)"
    echo ""
    read -rp "$(printf "${CYAN}▸${NC} Choice [1]: ")" env_choice
    env_choice="${env_choice:-1}"

    case "$env_choice" in
        1)
            ENV_FILE="$HOME/.jclaw/.env"
            mkdir -p "$HOME/.jclaw"
            ;;
        2)
            ENV_FILE="$compose_dir/.env"
            ;;
        *)
            warn "Invalid choice, using ~/.jclaw/.env"
            ENV_FILE="$HOME/.jclaw/.env"
            mkdir -p "$HOME/.jclaw"
            ;;
    esac

    # Write ~/.jclawrc so all scripts find this location
    echo "JCLAW_ENV_FILE=\"$ENV_FILE\"" > "$HOME/.jclawrc"
    export JCLAW_ENV_FILE="$ENV_FILE"
    ok "Saved config location to ~/.jclawrc"

    # Copy template
    if [ ! -f "$ENV_FILE" ] && [ -f "$compose_dir/.env.example" ]; then
        cp "$compose_dir/.env.example" "$ENV_FILE"
        info "Created $ENV_FILE from template"
    fi
}

# ─── Reconfigure ─────────────────────────────────────────────────────────────

reconfigure() {
    local compose_dir="$JCLAW_DIR/docker-compose"

    header "JClaw Reconfigure"

    # Step 1: .env location
    echo ""
    printf "${BOLD}Where should configuration be saved?${NC}\n"
    local current="${JCLAW_ENV_FILE:-$compose_dir/.env}"
    echo "  Current: $current"
    echo ""
    echo "  1. ~/.jclaw/.env (recommended — persists across projects)"
    echo "  2. docker-compose/.env (project-local)"
    echo "  3. Keep current ($current)"
    echo ""
    read -rp "$(printf "${CYAN}▸${NC} Choice [3]: ")" env_choice
    env_choice="${env_choice:-3}"

    case "$env_choice" in
        1)
            ENV_FILE="$HOME/.jclaw/.env"
            mkdir -p "$HOME/.jclaw"
            ;;
        2)
            ENV_FILE="$compose_dir/.env"
            ;;
        *)
            ENV_FILE="$current"
            ;;
    esac

    # Update ~/.jclawrc
    echo "JCLAW_ENV_FILE=\"$ENV_FILE\"" > "$HOME/.jclawrc"
    export JCLAW_ENV_FILE="$ENV_FILE"
    ok "Config location: $ENV_FILE"

    # Create from template if missing
    if [ ! -f "$ENV_FILE" ] && [ -f "$compose_dir/.env.example" ]; then
        mkdir -p "$(dirname "$ENV_FILE")"
        cp "$compose_dir/.env.example" "$ENV_FILE"
    fi

    # Step 2: LLM provider
    echo ""
    printf "${BOLD}Select LLM provider:${NC}\n"
    echo "  1. Anthropic (Claude)"
    echo "  2. OpenAI"
    echo "  3. Google Gemini"
    echo "  4. Ollama (local, free)"
    echo ""
    read -rp "$(printf "${CYAN}▸${NC} Choice [1]: ")" provider_choice
    provider_choice="${provider_choice:-1}"

    case "$provider_choice" in
        1)
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=anthropic|" "$ENV_FILE"
            sed -i.bak "s|^ANTHROPIC_ENABLED=.*|ANTHROPIC_ENABLED=true|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"

            echo ""
            read -rp "$(printf "${CYAN}▸${NC} Anthropic API key: ")" api_key
            if [ -n "$api_key" ]; then
                sed -i.bak "s|^ANTHROPIC_API_KEY=.*|ANTHROPIC_API_KEY=${api_key}|" "$ENV_FILE"
                rm -f "$ENV_FILE.bak"
                ok "Anthropic API key saved"
            fi
            ;;
        2)
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=openai|" "$ENV_FILE"
            sed -i.bak "s|^OPENAI_ENABLED=.*|OPENAI_ENABLED=true|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"

            echo ""
            read -rp "$(printf "${CYAN}▸${NC} OpenAI API key: ")" api_key
            if [ -n "$api_key" ]; then
                sed -i.bak "s|^OPENAI_API_KEY=.*|OPENAI_API_KEY=${api_key}|" "$ENV_FILE"
                rm -f "$ENV_FILE.bak"
                ok "OpenAI API key saved"
            fi
            ;;
        3)
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=google-genai|" "$ENV_FILE"
            sed -i.bak "s|^GEMINI_ENABLED=.*|GEMINI_ENABLED=true|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"

            echo ""
            read -rp "$(printf "${CYAN}▸${NC} Gemini API key (from https://aistudio.google.com/apikey): ")" api_key
            if [ -n "$api_key" ]; then
                sed -i.bak "s|^GEMINI_API_KEY=.*|GEMINI_API_KEY=${api_key}|" "$ENV_FILE"
                rm -f "$ENV_FILE.bak"
                ok "Gemini API key saved"
            fi
            ;;
        4)
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=ollama|" "$ENV_FILE"
            sed -i.bak "s|^OLLAMA_ENABLED=.*|OLLAMA_ENABLED=true|" "$ENV_FILE"
            sed -i.bak "s|^ANTHROPIC_ENABLED=.*|ANTHROPIC_ENABLED=false|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
            ok "Ollama selected — will start with Docker Compose"
            ;;
        *)
            warn "Invalid choice, keeping current provider"
            ;;
    esac

    # Step 3: Telegram
    setup_telegram

    # Step 4: Security
    echo ""
    printf "${BOLD}Security mode:${NC}\n"
    echo "  1. API Key (default — simple shared key)"
    echo "  2. JWT (token-based authentication)"
    echo "  3. None (disable security — development only)"
    echo ""
    read -rp "$(printf "${CYAN}▸${NC} Choice [1]: ")" sec_choice
    sec_choice="${sec_choice:-1}"

    case "$sec_choice" in
        1)
            sed -i.bak "s|^JCLAW_SECURITY_MODE=.*|JCLAW_SECURITY_MODE=api-key|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
            echo ""
            read -rp "$(printf "${CYAN}▸${NC} Custom API key (leave blank to auto-generate): ")" custom_key
            if [ -n "$custom_key" ]; then
                sed -i.bak "s|^JCLAW_API_KEY=.*|JCLAW_API_KEY=${custom_key}|" "$ENV_FILE"
                rm -f "$ENV_FILE.bak"
                ok "Custom API key saved"
            else
                ok "API key will be auto-generated on startup"
            fi
            ;;
        2)
            sed -i.bak "s|^JCLAW_SECURITY_MODE=.*|JCLAW_SECURITY_MODE=jwt|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
            ok "JWT security mode selected"
            ;;
        3)
            sed -i.bak "s|^JCLAW_SECURITY_MODE=.*|JCLAW_SECURITY_MODE=none|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
            warn "Security disabled — do not use in production"
            ;;
        *)
            warn "Invalid choice, keeping current security mode"
            ;;
    esac

    ok "Reconfiguration complete"
    echo ""

    # Source the .env file so start_stack() sees the values we just wrote
    # (without this, has_api_key() checks shell env — which is empty — and falls back to Ollama)
    set -a
    source "$ENV_FILE"
    set +a

    # Restart the stack
    info "Restarting stack with new configuration..."
    docker compose -f "$compose_dir/docker-compose.yml" --env-file "$ENV_FILE" down 2>/dev/null || true
    start_stack
    print_success
}

# ─── Start stack ──────────────────────────────────────────────────────────────

has_api_key() {
    { [ -n "${OPENAI_API_KEY:-}" ] && [ "${OPENAI_API_KEY}" != "not-set" ]; } ||
    { [ -n "${ANTHROPIC_API_KEY:-}" ] && [ "${ANTHROPIC_API_KEY}" != "not-set" ]; } ||
    { [ -n "${GEMINI_API_KEY:-}" ] && [ "${GEMINI_API_KEY}" != "not-set" ]; }
}

start_stack() {
    header "Starting JClaw"

    local compose_dir="$JCLAW_DIR/docker-compose"

    if [ ! -d "$compose_dir" ]; then
        err "Docker compose directory not found: $compose_dir"
        exit 1
    fi

    # Source the .env file so we can read the configured provider and keys
    set -a
    source "$ENV_FILE"
    set +a

    # Read the configured provider from .env (may have been set by reconfigure)
    local configured_provider
    configured_provider=$(grep "^AI_PROVIDER=" "$ENV_FILE" | cut -d= -f2)

    # Decide whether to include Ollama based on the configured provider
    local use_ollama=true
    if [ "$configured_provider" = "ollama" ]; then
        # Explicitly configured for Ollama
        use_ollama=true
    elif [ "$configured_provider" = "anthropic" ] || [ "$configured_provider" = "openai" ] || [ "$configured_provider" = "google-genai" ]; then
        # A cloud provider is configured — skip Ollama
        use_ollama=false
        ok "Provider '$configured_provider' configured — skipping Ollama (you can start it later with: docker compose --profile ollama up -d)"
    elif has_api_key; then
        # No explicit provider but an API key exists — auto-detect
        use_ollama=false
        if [ -n "${ANTHROPIC_API_KEY:-}" ] && [ "${ANTHROPIC_API_KEY}" != "not-set" ]; then
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=anthropic|" "$ENV_FILE"
            sed -i.bak "s|^ANTHROPIC_ENABLED=.*|ANTHROPIC_ENABLED=true|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
        elif [ -n "${OPENAI_API_KEY:-}" ] && [ "${OPENAI_API_KEY}" != "not-set" ]; then
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=openai|" "$ENV_FILE"
            sed -i.bak "s|^OPENAI_ENABLED=.*|OPENAI_ENABLED=true|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
        elif [ -n "${GEMINI_API_KEY:-}" ] && [ "${GEMINI_API_KEY}" != "not-set" ]; then
            sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=google-genai|" "$ENV_FILE"
            sed -i.bak "s|^GEMINI_ENABLED=.*|GEMINI_ENABLED=true|" "$ENV_FILE"
            rm -f "$ENV_FILE.bak"
        fi
        ok "API key detected — skipping Ollama"
    else
        # No API key and no configured cloud provider — fall back to Ollama
        sed -i.bak "s|^AI_PROVIDER=.*|AI_PROVIDER=ollama|" "$ENV_FILE"
        sed -i.bak "s|^OLLAMA_ENABLED=.*|OLLAMA_ENABLED=true|" "$ENV_FILE"
        sed -i.bak "s|^ANTHROPIC_ENABLED=.*|ANTHROPIC_ENABLED=false|" "$ENV_FILE"
        rm -f "$ENV_FILE.bak"
    fi

    debug "Starting containers..."
    local start=$SECONDS
    if [ "$use_ollama" = true ]; then
        info "No API key set — starting with Ollama (local LLM). This pulls a ~3GB image on first run."
        docker compose -f "$compose_dir/docker-compose.yml" --env-file "$ENV_FILE" --profile ollama up -d
    else
        docker compose -f "$compose_dir/docker-compose.yml" --env-file "$ENV_FILE" up -d
    fi
    local elapsed=$(( SECONDS - start ))
    ok "Stack is running (${elapsed}s)"

    # Pull a model into Ollama if we started it
    if [ "$use_ollama" = true ]; then
        echo ""
        info "Pulling Ollama model (this may take a few minutes on first run)..."
        debug "Running: ollama pull llama3.2"
        start=$SECONDS
        docker compose -f "$compose_dir/docker-compose.yml" --env-file "$ENV_FILE" --profile ollama exec -T ollama ollama pull llama3.2 2>&1 | while IFS= read -r line; do
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
        sed -i.bak "s|^TELEGRAM_ENABLED=.*|TELEGRAM_ENABLED=true|" "$ENV_FILE"
        sed -i.bak "s|^TELEGRAM_BOT_TOKEN=.*|TELEGRAM_BOT_TOKEN=${tg_token}|" "$ENV_FILE"
        rm -f "$ENV_FILE.bak"
        return
    }
    local bot_username
    bot_username=$(echo "$response" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
    ok "Bot validated: @${bot_username}"

    # Write to .env
    sed -i.bak "s|^TELEGRAM_ENABLED=.*|TELEGRAM_ENABLED=true|" "$ENV_FILE"
    sed -i.bak "s|^TELEGRAM_BOT_TOKEN=.*|TELEGRAM_BOT_TOKEN=${tg_token}|" "$ENV_FILE"
    rm -f "$ENV_FILE.bak"

    TELEGRAM_BOT_USERNAME="$bot_username"
}

# ─── Done ─────────────────────────────────────────────────────────────────────

print_success() {
    header "JClaw is Running"

    resolve_api_key
    print_security_info
    echo ""
    echo "Test it:"
    echo ""
    print_api_curl_example 8080
    echo ""
    echo "Or with httpie:"
    print_api_httpie_example 8080
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
    printf "  ${BOLD}${ENV_FILE}${NC}\n"
    echo ""
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    local total_start=$SECONDS
    local launch_mode=gateway
    local do_reconfigure=false
    local with_cron_manager=false
    FORCE_BUILD=false

    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            --shell)         launch_mode=shell ;;
            --cron-manager)  with_cron_manager=true ;;
            --force-build)   FORCE_BUILD=true ;;
            --reconfigure)   do_reconfigure=true ;;
            -h|--help|help)
                echo "Usage: ./quickstart.sh [options]"
                echo ""
                echo "Docker-based zero-friction JClaw launcher."
                echo ""
                echo "Options:"
                echo "  --shell          Start the interactive CLI shell instead of the gateway"
                echo "  --cron-manager   Also build and start the cron-manager sidecar"
                echo "  --force-build    Force rebuild Docker images even if they exist"
                echo "  --reconfigure    Re-run interactive setup (provider, API keys, channels)"
                echo "  --help           Print this help"
                exit 0
                ;;
            *)
                err "Unknown option: $arg"
                echo "Run './quickstart.sh --help' for usage."
                exit 1
                ;;
        esac
    done

    header "JClaw Quickstart"
    debug "JCLAW_DIR=$JCLAW_DIR  INSIDE_REPO=$INSIDE_REPO"

    check_docker
    clone_repo

    # Resolve .env file location (prompt on first run, read ~/.jclawrc on subsequent runs)
    resolve_env_file

    # Reconfigure mode: full interactive re-setup
    if [ "$do_reconfigure" = true ]; then
        reconfigure

        local total_elapsed=$(( SECONDS - total_start ))
        local mins=$(( total_elapsed / 60 ))
        local secs=$(( total_elapsed % 60 ))
        if [ "$mins" -gt 0 ]; then
            ok "Total time: ${mins}m ${secs}s"
        else
            ok "Total time: ${secs}s"
        fi
        return
    fi

    if [ "$with_cron_manager" = true ]; then
        build_cron_manager_image
        # Enable cron-manager in .env
        sed -i.bak "s|^CRON_MANAGER_ENABLED=.*|CRON_MANAGER_ENABLED=true|" "$ENV_FILE"
        rm -f "$ENV_FILE.bak"
        ok "Cron Manager enabled"
    fi

    if [ "$launch_mode" = "shell" ]; then
        build_shell_image
        launch_shell
    else
        build_image
        setup_telegram
        start_stack
        if [ "$with_cron_manager" = true ]; then
            local compose_dir="$JCLAW_DIR/docker-compose"
            info "Starting cron-manager container..."
            docker compose -f "$compose_dir/docker-compose.yml" --env-file "$ENV_FILE" --profile cron-manager up -d cron-manager
            ok "Cron Manager running on http://localhost:${CRON_MANAGER_PORT:-8090}"
        fi
        print_success
    fi

    local total_elapsed=$(( SECONDS - total_start ))
    local mins=$(( total_elapsed / 60 ))
    local secs=$(( total_elapsed % 60 ))
    if [ "$mins" -gt 0 ]; then
        ok "Total time: ${mins}m ${secs}s"
    else
        ok "Total time: ${secs}s"
    fi
}

main "$@"
