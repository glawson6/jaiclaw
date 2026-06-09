#!/usr/bin/env bash
#
# JaiClaw Installer — curl-installable setup script
#
# Usage:
#   curl -fsSL https://jaiclaw.io/install.sh | bash
#
# Or from GitHub:
#   curl -sSL https://raw.githubusercontent.com/glawson6/jaiclaw/main/install.sh | bash
#
# Or from the repo:
#   ./install.sh
#
set -euo pipefail

# ─── Constants ────────────────────────────────────────────────────────────────

JAICLAW_VERSION="${JAICLAW_VERSION:-0.7.1-SNAPSHOT}"
JAICLAW_HOME="${JAICLAW_HOME:-$HOME/.jaiclaw}"
JAICLAW_REPO="glawson6/jaiclaw"
JAVA_MIN_VERSION=21

# ─── Colors ───────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()   { printf "${CYAN}▸${NC} %s\n" "$*"; }
ok()     { printf "${GREEN}✓${NC} %s\n" "$*"; }
warn()   { printf "${YELLOW}!${NC} %s\n" "$*"; }
err()    { printf "${RED}✗${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}${CYAN}── %s ──${NC}\n\n" "$*"; }

# ─── Detect platform ─────────────────────────────────────────────────────────

detect_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Darwin) PLATFORM="macos" ;;
        Linux)  PLATFORM="linux" ;;
        *)      err "Unsupported OS: $os"; exit 1 ;;
    esac

    case "$arch" in
        x86_64|amd64)   ARCH="x64" ;;
        arm64|aarch64)  ARCH="arm64" ;;
        *)              err "Unsupported architecture: $arch"; exit 1 ;;
    esac

    info "Platform: $PLATFORM ($ARCH)"
}

# ─── Check Java ──────────────────────────────────────────────────────────────

check_java() {
    header "Checking Java"

    local java_cmd=""

    # Check JAVA_HOME first
    if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
        java_cmd="$JAVA_HOME/bin/java"
    # Check SDKMAN
    elif [[ -x "$HOME/.sdkman/candidates/java/current/bin/java" ]]; then
        java_cmd="$HOME/.sdkman/candidates/java/current/bin/java"
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    # Check PATH
    elif command -v java &>/dev/null; then
        java_cmd="java"
    fi

    if [[ -n "$java_cmd" ]]; then
        local java_version
        java_version=$("$java_cmd" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/' | cut -d. -f1)
        if [[ "$java_version" -ge "$JAVA_MIN_VERSION" ]]; then
            ok "Java $java_version found ($java_cmd)"
            JAVA_BIN="$java_cmd"
            return 0
        else
            warn "Java $java_version found but $JAVA_MIN_VERSION+ required"
        fi
    fi

    # No suitable Java found
    warn "Java $JAVA_MIN_VERSION+ not found"
    echo ""
    echo "Install Java 21 via SDKMAN (recommended):"
    echo "  curl -s https://get.sdkman.io | bash"
    echo "  sdk install java 21.0.9-oracle"
    echo ""
    echo "Or install via package manager:"
    case "$PLATFORM" in
        macos) echo "  brew install --cask temurin@21" ;;
        linux) echo "  sudo apt install openjdk-21-jdk" ;;
    esac

    read -rp "Continue without Java? (JVM commands won't work) [y/N] " answer
    if [[ "${answer,,}" != "y" ]]; then
        exit 1
    fi
    JAVA_BIN=""
    return 0
}

# ─── Create directory structure ──────────────────────────────────────────────

create_directories() {
    header "Creating directory structure"

    mkdir -p "$JAICLAW_HOME/bin"
    mkdir -p "$JAICLAW_HOME/profiles/default/sessions"
    ok "Created $JAICLAW_HOME/"
}

# ─── Install launcher script ─────────────────────────────────────────────────

install_launcher() {
    header "Installing launcher"

    # Check if running from repo
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    if [[ -f "$script_dir/bin/jaiclaw" ]]; then
        # Running from repo — copy local file
        cp "$script_dir/bin/jaiclaw" "$JAICLAW_HOME/bin/jaiclaw"
        ok "Copied launcher from repo"
    else
        # Download from GitHub
        local url="https://raw.githubusercontent.com/$JAICLAW_REPO/main/bin/jaiclaw"
        if curl -sSL -o "$JAICLAW_HOME/bin/jaiclaw" "$url" 2>/dev/null; then
            ok "Downloaded launcher from GitHub"
        else
            err "Failed to download launcher. Install manually from the repo."
            return 1
        fi
    fi

    chmod +x "$JAICLAW_HOME/bin/jaiclaw"
}

# ─── Install CLI JAR ─────────────────────────────────────────────────────────

install_jar() {
    header "Installing CLI JAR"

    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    # Check for locally built JAR
    local target_dir="$script_dir/apps/jaiclaw-cli/target"
    if [[ -d "$target_dir" ]]; then
        local jar
        jar=$(find "$target_dir" -maxdepth 1 -name "jaiclaw-cli-*.jar" ! -name "*-original*" -type f 2>/dev/null | head -1)
        if [[ -n "$jar" ]]; then
            cp "$jar" "$JAICLAW_HOME/bin/jaiclaw-cli.jar"
            ok "Installed CLI JAR from local build"
            return 0
        fi
    fi

    # Try to download from GitHub releases
    local url="https://github.com/$JAICLAW_REPO/releases/download/v${JAICLAW_VERSION}/jaiclaw-cli-${JAICLAW_VERSION}.jar"
    info "Downloading CLI JAR from GitHub releases..."
    if curl -sSL -o "$JAICLAW_HOME/bin/jaiclaw-cli.jar" "$url" 2>/dev/null; then
        ok "Downloaded CLI JAR"
    else
        warn "CLI JAR not available for download"
        echo "Build from source: ./mvnw package -pl :jaiclaw-cli -am -DskipTests"
        echo "Then copy to: $JAICLAW_HOME/bin/jaiclaw-cli.jar"
    fi
}

# ─── Create default profile ─────────────────────────────────────────────────

create_default_profile() {
    header "Setting up default profile"

    local profile_dir="$JAICLAW_HOME/profiles/default"

    if [[ ! -f "$profile_dir/application-local.yml" ]]; then
        cat > "$profile_dir/application-local.yml" <<'YAML'
# JaiClaw configuration — profile: default
# Edit this file to configure your LLM provider and other settings.
# Run 'jaiclaw setup' for interactive configuration.
jaiclaw:
  identity:
    name: JaiClaw
  security:
    mode: none

spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:not-set}
YAML
        ok "Created default config"
    else
        info "Default config already exists"
    fi

    if [[ ! -f "$profile_dir/.env" ]]; then
        cat > "$profile_dir/.env" <<ENV
# JaiClaw secrets — profile: default
# This file is sourced automatically by the jaiclaw launcher.

# Uncomment and set your API key:
# export ANTHROPIC_API_KEY=sk-ant-...
# export OPENAI_API_KEY=sk-...
# export GEMINI_API_KEY=...
ENV
        ok "Created default .env"
    else
        info "Default .env already exists"
    fi

    # Create global config
    if [[ ! -f "$JAICLAW_HOME/config.yaml" ]]; then
        cat > "$JAICLAW_HOME/config.yaml" <<'YAML'
# JaiClaw CLI config
active-profile: default
YAML
        ok "Created global config"
    fi
}

# ─── Add to PATH ─────────────────────────────────────────────────────────────

setup_path() {
    header "Setting up PATH"

    local bin_dir="$JAICLAW_HOME/bin"
    local symlink_dir="$HOME/.local/bin"

    # Check if already on PATH
    if echo "$PATH" | tr ':' '\n' | grep -q "$bin_dir"; then
        ok "Already on PATH: $bin_dir"
        return
    fi

    # Try symlink to ~/.local/bin (common on modern systems)
    if [[ -d "$symlink_dir" ]] && echo "$PATH" | tr ':' '\n' | grep -q "$symlink_dir"; then
        ln -sf "$bin_dir/jaiclaw" "$symlink_dir/jaiclaw"
        ok "Symlinked to $symlink_dir/jaiclaw"
        return
    fi

    # Suggest adding to shell profile
    warn "Add to your shell profile:"
    echo ""

    local shell_profile=""
    case "${SHELL:-/bin/bash}" in
        */zsh)  shell_profile="$HOME/.zshrc" ;;
        */bash)
            if [[ -f "$HOME/.bash_profile" ]]; then
                shell_profile="$HOME/.bash_profile"
            else
                shell_profile="$HOME/.bashrc"
            fi
            ;;
        */fish) shell_profile="$HOME/.config/fish/config.fish" ;;
    esac

    if [[ -n "$shell_profile" ]]; then
        echo "  echo 'export PATH=\"$bin_dir:\$PATH\"' >> $shell_profile"
        echo "  source $shell_profile"
    else
        echo "  export PATH=\"$bin_dir:\$PATH\""
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    echo ""
    printf "${BOLD}${CYAN}JaiClaw Installer v%s${NC}\n" "$JAICLAW_VERSION"
    echo ""

    detect_platform
    check_java
    create_directories
    install_launcher
    install_jar
    create_default_profile
    setup_path

    header "Installation complete"
    echo "Run 'jaiclaw doctor' to check your environment."
    echo "Run 'jaiclaw setup' to configure your LLM provider."
    echo "Run 'jaiclaw chat \"hello\"' to start chatting."
    echo ""
}

main "$@"
