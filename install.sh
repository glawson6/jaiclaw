#!/usr/bin/env bash
#
# JaiClaw Installer — curl-installable setup script
#
# Usage:
#   curl -fsSL https://jaiclaw.io/install.sh | bash                        # default (JAR from Nexus)
#   curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --docker         # Docker-backed launcher
#   curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source    # Build from git
#
# Or from the repo:
#   ./install.sh                                                            # default (JAR)
#   ./install.sh --docker                                                   # Docker mode
#   ./install.sh --from-source                                              # From-source build
#
# Install modes:
#   default        — download the CLI fat jar from Nexus (needs Java 21 locally;
#                    offers to install via SDKMAN if missing).
#   --docker       — pull the CLI Docker image and install a shim launcher
#                    (needs Docker locally; no Java needed).
#   --from-source  — clone the repo (or use JAICLAW_SOURCE_DIR) and build the
#                    jar with the Maven wrapper (needs Java + git; 5-15 min
#                    first build, ~60 sec subsequent builds with warm cache).
#
# Environment overrides:
#   JAICLAW_VERSION            — pin an explicit version tag ("0.9.3-SNAPSHOT")
#   JAICLAW_HOME               — install root (default: ~/.jaiclaw)
#   JAICLAW_REF                — git ref for --from-source (default: main)
#   JAICLAW_SOURCE_DIR         — use an existing git checkout for --from-source
#                                (skip the clone)
#   JAICLAW_NON_INTERACTIVE    — skip TTY prompts (used for CI / curl|bash)
#   JAICLAW_DOCKER_IMAGE_BASE  — override the Docker registry/repo
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
JAICLAW_DOCKER_IMAGE_BASE="${JAICLAW_DOCKER_IMAGE_BASE:-tooling.taptech.net:5000/jaiclaw-cli}"
JAVA_MIN_VERSION=21

# Install mode: "jar" (default), "docker", or "source".
INSTALL_MODE="jar"

# --from-source knobs
JAICLAW_REF="${JAICLAW_REF:-main}"
JAICLAW_SOURCE_DIR="${JAICLAW_SOURCE_DIR:-}"
CLEAN_CACHE=0
KEEP_SOURCE=0

# ─── Parse flags ─────────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
    case "$1" in
        --docker)
            INSTALL_MODE="docker"
            shift
            ;;
        --from-source)
            INSTALL_MODE="source"
            shift
            ;;
        --clean-cache)
            CLEAN_CACHE=1
            shift
            ;;
        --keep-source)
            KEEP_SOURCE=1
            shift
            ;;
        --help|-h)
            sed -n '2,35p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "Unknown flag: $1" >&2
            echo "Run '$0 --help' for usage." >&2
            exit 1
            ;;
    esac
done

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

# ─── Docker install path ─────────────────────────────────────────────────────

# Verify Docker is on PATH and the daemon is reachable. No install offer here —
# unlike Java+SDKMAN, Docker installation is platform-specific, needs root on
# Linux, needs Docker Desktop on macOS, and can't be automated from a curl|bash.
check_docker() {
    header "Checking Docker"

    if ! command -v docker &>/dev/null; then
        err "Docker not found on PATH"
        echo ""
        echo "Install Docker:"
        case "$PLATFORM" in
            macos) echo "  https://docs.docker.com/desktop/install/mac-install/" ;;
            linux) echo "  https://docs.docker.com/engine/install/" ;;
        esac
        echo ""
        echo "Then re-run this installer with --docker."
        exit 1
    fi

    if ! docker info &>/dev/null; then
        err "Docker daemon not reachable"
        echo "  (docker CLI is installed but 'docker info' failed — daemon may be stopped)"
        exit 1
    fi

    ok "Docker $(docker version --format '{{.Client.Version}}') available"
}

# Pull the CLI image + write a shim launcher that invokes it.
install_via_docker() {
    header "Pulling CLI Docker image"

    # Resolve 'latest' to a real image tag by querying the registry — same
    # spirit as the Nexus Maven-metadata resolution in install_jar. Falls
    # back to :latest if the registry doesn't expose a tags API.
    local image_tag="$JAICLAW_VERSION"
    if [[ "$image_tag" == "latest" ]]; then
        # Just pull :latest — Docker registries always resolve this.
        image_tag="latest"
    fi

    local image="${JAICLAW_DOCKER_IMAGE_BASE}:${image_tag}"
    info "Pulling $image"
    if ! docker pull "$image"; then
        err "docker pull failed for $image"
        echo "  Registry may be unreachable, credentials may be missing, or the tag may not exist."
        echo "  Pin an explicit version:  JAICLAW_VERSION=X.Y.Z ... --docker"
        echo "  Or set a different base:  JAICLAW_DOCKER_IMAGE_BASE=<registry>/<repo> ... --docker"
        exit 1
    fi
    ok "Image pulled"

    header "Installing Docker-backed launcher"
    local shim="$JAICLAW_HOME/bin/jaiclaw"
    cat > "$shim" <<SHIM
#!/usr/bin/env bash
# JaiClaw Docker-backed launcher
# Generated by install.sh --docker. To switch to the JAR path, re-run install.sh
# without --docker.
set -euo pipefail

IMAGE="\${JAICLAW_IMAGE:-${image}}"
JAICLAW_HOME="\${JAICLAW_HOME:-\$HOME/.jaiclaw}"

# Mount ~/.jaiclaw into the container so profiles + sessions + auth persist.
# -it gives us a TTY for the REPL; --rm cleans up between invocations.
# The image entrypoint already points at /opt/jaiclaw/bin/jaiclaw so we pass
# subcommand args straight through.
exec docker run --rm -it \\
    -v "\$JAICLAW_HOME:/home/jaiclaw/.jaiclaw" \\
    "\$IMAGE" "\$@"
SHIM
    chmod +x "$shim"
    ok "Installed launcher at $shim"
}

# ─── From-source install path ────────────────────────────────────────────────

# Verify git is on PATH. We deliberately do NOT try to install git ourselves —
# xcode-select on macOS launches a GUI installer, apt-get needs sudo, both
# surprise operators running under curl|bash. Bail with a clear message.
detect_git() {
    header "Checking git"
    if ! command -v git &>/dev/null; then
        err "git not found on PATH"
        echo ""
        echo "Install git:"
        case "$PLATFORM" in
            macos) echo "  xcode-select --install     # opens the Developer Tools installer" ;;
            linux) echo "  sudo apt install git       # or your distro's equivalent" ;;
        esac
        echo ""
        echo "Then re-run this installer with --from-source."
        exit 1
    fi
    ok "git $(git --version | awk '{print $3}') found"
}

# Clone the repo into $JAICLAW_HOME/src, or use $JAICLAW_SOURCE_DIR as-is.
# Prints elapsed seconds so the multi-second clone step isn't silent.
acquire_source() {
    header "Acquiring source"
    if [[ -n "$JAICLAW_SOURCE_DIR" ]]; then
        if [[ ! -d "$JAICLAW_SOURCE_DIR/.git" ]]; then
            err "JAICLAW_SOURCE_DIR=$JAICLAW_SOURCE_DIR is not a git checkout"
            exit 1
        fi
        SOURCE_DIR="$JAICLAW_SOURCE_DIR"
        ok "Using existing checkout at $SOURCE_DIR"
        return 0
    fi

    SOURCE_DIR="$JAICLAW_HOME/src"
    if [[ -d "$SOURCE_DIR/.git" ]]; then
        info "Existing clone at $SOURCE_DIR — updating"
        (cd "$SOURCE_DIR" && git fetch --depth 1 origin "$JAICLAW_REF" >/dev/null 2>&1 && git checkout "$JAICLAW_REF" >/dev/null 2>&1) \
            || { err "git fetch failed"; exit 1; }
        ok "Updated to $JAICLAW_REF"
        return 0
    fi

    info "Cloning $JAICLAW_REPO@$JAICLAW_REF into $SOURCE_DIR (~10 sec)"
    local start end
    start=$(date +%s)
    # --depth 1 keeps the clone small; --branch works for tags too, not just branch names.
    if ! git clone --depth 1 --branch "$JAICLAW_REF" \
            "https://github.com/$JAICLAW_REPO.git" "$SOURCE_DIR" >/dev/null 2>&1; then
        err "git clone failed"
        echo "  Network / auth / bad ref '$JAICLAW_REF'?"
        exit 1
    fi
    end=$(date +%s)
    ok "Cloned ($((end - start))s)"
}

# Purge only JaiClaw's own artifacts from ~/.m2. Third-party deps are left alone
# so the next build stays warm. Called for --clean-cache before build_from_source.
purge_jaiclaw_m2() {
    local m2_root="${HOME}/.m2/repository/io/jaiclaw"
    if [[ -d "$m2_root" ]]; then
        info "Purging Maven cache for io/jaiclaw"
        rm -rf "$m2_root"
    fi
}

# Build via the Maven wrapper. Streams full output to $JAICLAW_HOME/install.log
# and shows a heartbeat every 30 sec so multi-minute builds don't look hung.
build_from_source() {
    header "Building jaiclaw-cli from source"

    if [[ "$CLEAN_CACHE" -eq 1 ]]; then
        purge_jaiclaw_m2
    fi

    local m2_size_hint=""
    if [[ ! -d "${HOME}/.m2/repository" ]]; then
        m2_size_hint="First-time build: downloading ~500 MB of Maven dependencies. This typically takes 5–15 minutes."
    else
        m2_size_hint="Warm Maven cache detected. Build typically takes ~60 seconds."
    fi
    info "$m2_size_hint"

    local log="$JAICLAW_HOME/install.log"
    info "Progress is streamed to $log; tail it in another terminal:"
    echo "    tail -f $log"
    echo ""

    # Start the build in the background so we can emit a heartbeat.
    cd "$SOURCE_DIR"
    (
        # `-o` (offline) is intentionally NOT set — cold cache needs the network.
        # `-am` builds jaiclaw-cli's Maven dependencies within the reactor.
        ./mvnw package -pl :jaiclaw-cli -am -DskipTests >"$log" 2>&1
    ) &
    local mvn_pid=$!

    # Heartbeat: emit an elapsed-seconds line every 30 sec while Maven runs.
    local start
    start=$(date +%s)
    while kill -0 "$mvn_pid" 2>/dev/null; do
        sleep 30
        # kill -0 again to avoid printing a heartbeat immediately after exit.
        if kill -0 "$mvn_pid" 2>/dev/null; then
            local now=$(date +%s)
            local elapsed=$((now - start))
            local mins=$((elapsed / 60))
            local secs=$((elapsed % 60))
            info "Building... (${mins}m${secs}s elapsed)"
        fi
    done

    wait "$mvn_pid" || {
        err "Maven build failed"
        echo ""
        echo "Last 50 lines of $log:"
        echo "─────────────────────────────────────────────────────────────"
        tail -n 50 "$log"
        echo "─────────────────────────────────────────────────────────────"
        exit 1
    }
    local end
    end=$(date +%s)
    local elapsed=$((end - start))
    local mins=$((elapsed / 60))
    local secs=$((elapsed % 60))

    # Locate the produced exec.jar.
    local built_jar
    built_jar=$(find "$SOURCE_DIR/apps/jaiclaw-cli/target" -maxdepth 1 \
                    -name "jaiclaw-cli-*-exec.jar" -type f 2>/dev/null | head -1)
    if [[ -z "$built_jar" ]]; then
        err "Build succeeded but no jaiclaw-cli-*-exec.jar found in target/"
        echo "  This is a bug in install.sh — please report."
        exit 1
    fi

    ok "Build complete ($(basename "$built_jar"), $(wc -c <"$built_jar" | tr -d ' ') bytes, ${mins}m${secs}s)"
    BUILT_JAR="$built_jar"
}

# Install the from-source-built jar + the launcher script. Reuses install_launcher's
# copy-from-repo path by pointing at $SOURCE_DIR/bin/jaiclaw.
install_from_source() {
    detect_git
    check_java
    if [[ -z "${JAVA_BIN:-}" ]]; then
        err "--from-source needs Java 21+ — cannot build without it"
        exit 1
    fi

    acquire_source
    build_from_source

    header "Installing built artifacts"
    cp "$BUILT_JAR" "$JAICLAW_HOME/bin/jaiclaw-cli.jar"
    ok "Installed CLI JAR to $JAICLAW_HOME/bin/jaiclaw-cli.jar"

    if [[ -f "$SOURCE_DIR/bin/jaiclaw" ]]; then
        cp "$SOURCE_DIR/bin/jaiclaw" "$JAICLAW_HOME/bin/jaiclaw"
        chmod +x "$JAICLAW_HOME/bin/jaiclaw"
        ok "Installed launcher from source"
    fi

    # By default, remove the ~500 MB source tree — the jar is what runs, and
    # the Maven cache in ~/.m2 stays warm for the next --from-source install.
    # Preserve when --keep-source or when the operator pointed us at their
    # own checkout (JAICLAW_SOURCE_DIR).
    if [[ "$KEEP_SOURCE" -eq 0 ]] && [[ -z "$JAICLAW_SOURCE_DIR" ]]; then
        info "Cleaning up $SOURCE_DIR (pass --keep-source to preserve it)"
        rm -rf "$SOURCE_DIR"
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
    if [[ "$JAICLAW_VERSION" == "latest" ]]; then
        printf "${BOLD}${CYAN}JaiClaw Installer${NC} (resolving latest, mode=%s)\n" "$INSTALL_MODE"
    else
        printf "${BOLD}${CYAN}JaiClaw Installer v%s${NC} (mode=%s)\n" "$JAICLAW_VERSION" "$INSTALL_MODE"
    fi
    echo ""

    detect_platform

    case "$INSTALL_MODE" in
        docker)
            check_docker
            create_directories
            install_via_docker
            create_default_profile
            setup_path
            ;;
        source)
            create_directories
            install_from_source
            create_default_profile
            setup_path
            ;;
        jar)
            check_java
            create_directories
            install_launcher
            install_jar
            create_default_profile
            setup_path
            ;;
        *)
            err "Unknown install mode: $INSTALL_MODE"
            exit 1
            ;;
    esac

    header "Installation complete"
    echo "Run 'jaiclaw doctor' to check your environment."
    echo "Run 'jaiclaw setup' to configure your LLM provider."
    echo "Run 'jaiclaw chat \"hello\"' to start chatting."
    echo ""
}

main "$@"
