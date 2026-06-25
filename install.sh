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
# Source: https://github.com/glawson6/jaiclaw/blob/main/install.sh
# This is a copy of the canonical script. Update from the jaiclaw repo when the installer changes.
#
set -euo pipefail

# ─── Constants ────────────────────────────────────────────────────────────────

JAICLAW_VERSION="${JAICLAW_VERSION:-latest}"
JAICLAW_HOME="${JAICLAW_HOME:-$HOME/.jaiclaw}"
JAICLAW_REPO="glawson6/jaiclaw"
JAICLAW_CLI_BASE_URL="${JAICLAW_CLI_BASE_URL:-https://tooling.taptech.net/repository/maven-public/io/jaiclaw/jaiclaw-cli}"
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

# Probe for an executable `java` at the usual locations. Sets JAVA_BIN to the
# resolved path on success (and exports JAVA_HOME when discovered via SDKMAN).
# Returns 0 if a Java >= JAVA_MIN_VERSION is found, 1 otherwise. Quiet — does
# NOT print warnings about the wrong version, so callers can probe both before
# and after a fresh SDKMAN install without doubling up the messaging.
probe_java() {
    local java_cmd=""

    if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
        java_cmd="$JAVA_HOME/bin/java"
    elif [[ -x "$HOME/.sdkman/candidates/java/current/bin/java" ]]; then
        java_cmd="$HOME/.sdkman/candidates/java/current/bin/java"
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    elif command -v java &>/dev/null; then
        java_cmd="java"
    fi

    if [[ -z "$java_cmd" ]]; then
        return 1
    fi

    local java_version
    java_version=$("$java_cmd" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/' | cut -d. -f1)
    if [[ "$java_version" -ge "$JAVA_MIN_VERSION" ]]; then
        JAVA_BIN="$java_cmd"
        return 0
    fi
    return 1
}

# Try to install Java via SDKMAN. Sources the freshly-installed shell init so
# the rest of this script sees `sdk` and the new `java` on PATH. Returns 0 on
# success, 1 on failure — leaves a usable JAVA_BIN set when successful.
install_java_via_sdkman() {
    header "Installing Java via SDKMAN"

    # Install SDKMAN if missing. The upstream installer writes to ~/.sdkman/
    # and prints to stderr; it does NOT modify shell profiles when piped
    # through bash this way, so the user's shell is untouched.
    if [[ ! -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
        info "Bootstrapping SDKMAN"
        if ! curl -s "https://get.sdkman.io" | bash >/dev/null 2>&1; then
            err "SDKMAN bootstrap failed"
            return 1
        fi
    fi

    # Source the init script in a way that survives `set -u`. SDKMAN's init
    # references unset vars and would otherwise abort the installer.
    set +u
    # shellcheck disable=SC1090
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    set -u

    info "Installing Java 21.0.9-oracle (this may take a minute)"
    set +u
    # `<<< y` answers SDKMAN's "make default" prompt without a TTY.
    if ! sdk install java 21.0.9-oracle <<< "y" >/dev/null 2>&1; then
        set -u
        err "sdk install java 21.0.9-oracle failed"
        return 1
    fi
    set -u

    export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    if probe_java; then
        ok "Java installed: $JAVA_BIN"
        return 0
    fi
    err "SDKMAN reported success but Java still not on PATH — installation incomplete"
    return 1
}

# Print the manual-install fallback message. Used in three places: piped
# (no TTY), user declined, or SDKMAN install failed.
print_java_manual_instructions() {
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
    echo ""
}

check_java() {
    header "Checking Java"

    if probe_java; then
        ok "Java found ($JAVA_BIN)"
        return 0
    fi

    warn "Java $JAVA_MIN_VERSION+ not found"

    # Non-interactive (curl|bash with no /dev/tty, CI, or explicit opt-out):
    # print the manual instructions and continue in degraded mode.
    if [[ "${JAICLAW_NON_INTERACTIVE:-false}" == "true" ]] || [[ ! -r /dev/tty ]]; then
        print_java_manual_instructions
        warn "Continuing without Java — JVM commands (chat, setup) will not work until Java is installed."
        JAVA_BIN=""
        return 0
    fi

    # Interactive: ask via /dev/tty so we work under `curl | bash`.
    echo ""
    local answer=""
    read -rp "Install Java 21 via SDKMAN now? [Y/n] " answer </dev/tty || answer=""
    case "${answer,,}" in
        ""|y|yes)
            if install_java_via_sdkman; then
                return 0
            fi
            warn "Falling back to manual install instructions"
            print_java_manual_instructions
            JAVA_BIN=""
            return 0
            ;;
        *)
            print_java_manual_instructions
            warn "Continuing without Java — re-run 'jaiclaw doctor' once Java is installed."
            JAVA_BIN=""
            return 0
            ;;
    esac
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

    # Check for locally built JAR (Spring Boot fat jar, classifier 'exec')
    local target_dir="$script_dir/apps/jaiclaw-cli/target"
    if [[ -d "$target_dir" ]]; then
        local jar
        jar=$(find "$target_dir" -maxdepth 1 -name "jaiclaw-cli-*-exec.jar" -type f 2>/dev/null | head -1)
        if [[ -n "$jar" ]]; then
            cp "$jar" "$JAICLAW_HOME/bin/jaiclaw-cli.jar"
            ok "Installed CLI JAR from local build"
            return 0
        fi
    fi

    # Resolve 'latest' via Maven metadata
    if [[ "$JAICLAW_VERSION" == "latest" ]]; then
        info "Resolving latest version from Nexus"
        local metadata_url="${JAICLAW_CLI_BASE_URL}/maven-metadata.xml"
        local resolved
        resolved=$(curl -fsSL "$metadata_url" 2>/dev/null \
            | sed -n 's:.*<release>\(.*\)</release>.*:\1:p' | head -1)
        if [[ -z "$resolved" ]]; then
            # Fall back to <latest> if <release> is absent (snapshots-only repo)
            resolved=$(curl -fsSL "$metadata_url" 2>/dev/null \
                | sed -n 's:.*<latest>\(.*\)</latest>.*:\1:p' | head -1)
        fi
        if [[ -z "$resolved" ]]; then
            err "Failed to resolve latest version from $metadata_url"
            echo "Pin a specific version with: JAICLAW_VERSION=X.Y.Z curl -fsSL https://jaiclaw.io/install.sh | bash"
            return 1
        fi
        JAICLAW_VERSION="$resolved"
        ok "Latest version: $JAICLAW_VERSION"
    fi

    # Resolve the jar filename. For releases (e.g. 0.9.0) it's the simple name.
    # For snapshots (e.g. 0.9.1-SNAPSHOT) Nexus stores the actual file under a
    # timestamped name (e.g. 0.9.1-20260622.222750-1-exec.jar) and does NOT
    # auto-rewrite the simple URL — we have to look it up in the version-level
    # maven-metadata.xml.
    local jar_filename="jaiclaw-cli-${JAICLAW_VERSION}-exec.jar"
    if [[ "$JAICLAW_VERSION" == *-SNAPSHOT ]]; then
        info "Resolving snapshot timestamp from Nexus"
        local snap_metadata_url="${JAICLAW_CLI_BASE_URL}/${JAICLAW_VERSION}/maven-metadata.xml"
        # Pull the <value> from the snapshotVersion entry whose classifier is 'exec'.
        # Squash whitespace to a single space so a single-line sed can match
        # across what would otherwise be a multi-line block. Anchor on the
        # snapshotVersion containing <classifier>exec</classifier>.
        local snap_value
        snap_value=$(curl -fsSL "$snap_metadata_url" 2>/dev/null \
            | tr -s '[:space:]' ' ' \
            | sed -n 's:.*<snapshotVersion> <classifier>exec</classifier> <extension>jar</extension> <value>\([^<]*\)</value>.*:\1:p')
        if [[ -z "$snap_value" ]]; then
            err "Failed to resolve snapshot timestamp from $snap_metadata_url"
            return 1
        fi
        jar_filename="jaiclaw-cli-${snap_value}-exec.jar"
    fi

    # Download from Nexus (fat jar lives under the 'exec' classifier)
    local url="${JAICLAW_CLI_BASE_URL}/${JAICLAW_VERSION}/${jar_filename}"
    local dest="$JAICLAW_HOME/bin/jaiclaw-cli.jar"
    info "Downloading CLI JAR from ${url}"
    # -f: fail on HTTP errors instead of writing the error body into the jar.
    if ! curl -fsSL -o "$dest" "$url"; then
        rm -f "$dest"
        err "Failed to download CLI JAR from $url"
        echo "Build from source: ./mvnw package -pl :jaiclaw-cli -am -DskipTests"
        echo "Then copy to: $dest"
        return 1
    fi

    # Sanity check — every JAR is a ZIP and starts with the magic bytes 'PK\x03\x04'.
    # A 404 HTML page or rate-limit error written into the file would fail this check
    # and trip "Invalid or corrupt jarfile" only later when Java tries to run it.
    if ! head -c4 "$dest" | grep -q $'^PK\x03\x04'; then
        rm -f "$dest"
        err "Downloaded file is not a valid JAR (magic bytes mismatch). Aborting."
        return 1
    fi
    ok "Downloaded CLI JAR ($(wc -c <"$dest" | tr -d ' ') bytes)"
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
    if [[ "$JAICLAW_VERSION" == "latest" ]]; then
        printf "${BOLD}${CYAN}JaiClaw Installer${NC} (resolving latest)\n"
    else
        printf "${BOLD}${CYAN}JaiClaw Installer v%s${NC}\n" "$JAICLAW_VERSION"
    fi
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
