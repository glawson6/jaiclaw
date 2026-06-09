#!/usr/bin/env bash
#
# JaiClaw E2E Test Runner
#
# Non-interactive bash script that validates JaiClaw bootstrap, scaffolding,
# build, runtime, CLI, and Docker images.
#
# Usage:
#   E2E_SCENARIOS=4 ./e2e/run-e2e-tests.sh              # CLI only
#   E2E_SCENARIOS=1,3,4 ./e2e/run-e2e-tests.sh          # bootstrap + provider + CLI
#   E2E_SCENARIOS=all ./e2e/run-e2e-tests.sh             # all 5 scenarios
#
# Environment Variables:
#   E2E_SCENARIOS       Comma-separated list (1,2,3,4,5) or "all" (default: 1,2,3,4)
#   AI_PROVIDER         Provider name: anthropic, openai, gemini (default: anthropic)
#   ANTHROPIC_API_KEY   API key for Anthropic
#   ANTHROPIC_BASE_URL  Optional base URL override (e.g., MiniMax endpoint)
#   ANTHROPIC_MODEL     Optional model override
#   OPENAI_API_KEY      API key for OpenAI
#   GOOGLE_API_KEY      API key for Google Gemini
#   E2E_SYSTEM_PROMPT   System prompt for scenarios 2 & 3
#   E2E_TIMEOUT         Startup timeout in seconds (default: 120)
#   E2E_KEEP_ARTIFACTS  Keep temp dirs on success (default: false)
#   JAICLAW_VERSION     Override version detection from pom.xml
#   PROJECT_ROOT        Override project root auto-detection
#
set -euo pipefail

# ─── Constants ────────────────────────────────────────────────────────────────

readonly E2E_DEFAULT_SYSTEM_PROMPT="You are a helpful assistant used for end-to-end testing. Follow instructions exactly. When asked to reply with a specific phrase, reply with only that phrase and nothing else."
readonly E2E_BOOTSTRAP_PORT=8080
readonly E2E_SCAFFOLD_PORT=8090
readonly FAST_PATH_TIMEOUT=5
readonly JVM_PATH_TIMEOUT=120

# ─── Colors ───────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

# ─── Logging ──────────────────────────────────────────────────────────────────

log_info()  { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
log_pass()  { printf "${GREEN}[PASS]${NC}  %s\n" "$*"; }
log_fail()  { printf "${RED}[FAIL]${NC}  %s\n" "$*" >&2; }
log_warn()  { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
log_skip()  { printf "${DIM}[SKIP]${NC}  %s\n" "$*"; }
log_header() { printf "\n${BOLD}═══ %s ═══${NC}\n\n" "$*"; }

# ─── Result tracking ─────────────────────────────────────────────────────────

declare -a RESULTS=()
declare -a PIDS_TO_KILL=()
declare -a DIRS_TO_CLEAN=()

record_result() {
    local scenario="$1"
    local status="$2"  # PASS, FAIL, SKIP
    local detail="${3:-}"
    RESULTS+=("${scenario}|${status}|${detail}")
}

# ─── Cleanup ──────────────────────────────────────────────────────────────────

cleanup() {
    local exit_code=$?
    log_info "Cleaning up..."

    # Kill tracked processes
    for pid in "${PIDS_TO_KILL[@]:-}"; do
        if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
            log_info "Killing process $pid"
            kill "$pid" 2>/dev/null || true
            wait "$pid" 2>/dev/null || true
        fi
    done

    # Also kill any Java processes we started on e2e ports
    for port in $E2E_BOOTSTRAP_PORT $E2E_SCAFFOLD_PORT; do
        local pid
        pid=$(lsof -ti:"$port" 2>/dev/null || true)
        if [[ -n "$pid" ]]; then
            log_info "Killing process on port $port (PID $pid)"
            kill "$pid" 2>/dev/null || true
        fi
    done

    # Clean temp directories
    if [[ "${E2E_KEEP_ARTIFACTS:-false}" != "true" ]]; then
        for dir in "${DIRS_TO_CLEAN[@]:-}"; do
            if [[ -n "$dir" && -d "$dir" ]]; then
                log_info "Removing $dir"
                rm -rf "$dir"
            fi
        done
    else
        log_info "Keeping artifacts (E2E_KEEP_ARTIFACTS=true)"
        for dir in "${DIRS_TO_CLEAN[@]:-}"; do
            [[ -n "$dir" ]] && log_info "  $dir"
        done
    fi

    # Clean up e2e test profile if it exists
    rm -rf "${HOME}/.jaiclaw/profiles/e2e-test" 2>/dev/null || true

    exit "$exit_code"
}

trap cleanup EXIT

# ─── Environment ──────────────────────────────────────────────────────────────

detect_project_root() {
    if [[ -n "${PROJECT_ROOT:-}" ]]; then
        echo "$PROJECT_ROOT"
        return
    fi

    # Resolve from script location
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local root
    root="$(cd "$script_dir/.." && pwd)"

    if [[ -f "$root/pom.xml" && -f "$root/mvnw" ]]; then
        echo "$root"
    else
        log_fail "Cannot detect project root from $script_dir"
        exit 1
    fi
}

detect_version() {
    if [[ -n "${JAICLAW_VERSION:-}" ]]; then
        echo "$JAICLAW_VERSION"
        return
    fi

    # Read from bin/jaiclaw (fastest — no Maven needed)
    local launcher="$PROJECT_ROOT/bin/jaiclaw"
    if [[ -f "$launcher" ]]; then
        local ver
        ver=$(grep '^JAICLAW_VERSION=' "$launcher" 2>/dev/null | head -1 | cut -d'"' -f2)
        if [[ -n "$ver" ]]; then
            echo "$ver"
            return
        fi
    fi

    # Fallback: read from pom.xml
    local ver
    ver=$(grep '<version>' "$PROJECT_ROOT/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
    if [[ -n "$ver" ]]; then
        echo "$ver"
        return
    fi

    log_fail "Cannot detect JaiClaw version"
    exit 1
}

detect_api_key() {
    local provider="${AI_PROVIDER:-anthropic}"
    case "$provider" in
        anthropic)
            if [[ -n "${ANTHROPIC_API_KEY:-}" ]]; then
                echo "ANTHROPIC_API_KEY"
                return
            fi
            ;;
        openai)
            if [[ -n "${OPENAI_API_KEY:-}" ]]; then
                echo "OPENAI_API_KEY"
                return
            fi
            ;;
        gemini)
            if [[ -n "${GOOGLE_API_KEY:-}" ]]; then
                echo "GOOGLE_API_KEY"
                return
            fi
            ;;
    esac
    return 1
}

wait_for_health() {
    local port="$1"
    local timeout="${2:-$E2E_TIMEOUT}"
    local elapsed=0

    while [[ $elapsed -lt $timeout ]]; do
        if curl -sf "http://localhost:${port}/api/health" >/dev/null 2>&1; then
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done

    return 1
}

# ─── Scenario 1 — Bootstrap Validation ────────────────────────────────────────

run_scenario_1() {
    log_header "Scenario 1 — Bootstrap Validation (quickstart.sh)"

    # Check prerequisites
    if ! detect_api_key >/dev/null 2>&1; then
        log_skip "Scenario 1: No API key available for provider '${AI_PROVIDER:-anthropic}'"
        record_result "1-Bootstrap" "SKIP" "No API key"
        return 0
    fi

    # Check port availability
    if lsof -ti:$E2E_BOOTSTRAP_PORT >/dev/null 2>&1; then
        log_fail "Port $E2E_BOOTSTRAP_PORT already in use"
        record_result "1-Bootstrap" "FAIL" "Port $E2E_BOOTSTRAP_PORT in use"
        return 1
    fi

    local work_dir
    work_dir=$(mktemp -d)/jaiclaw-e2e-bootstrap
    mkdir -p "$work_dir"
    DIRS_TO_CLEAN+=("$(dirname "$work_dir")")
    log_info "Work dir: $work_dir"

    # Copy repo (faster than clone for local testing)
    log_info "Copying project to $work_dir/jaiclaw..."
    cp -R "$PROJECT_ROOT" "$work_dir/jaiclaw"

    # Run quickstart in non-interactive mode
    log_info "Starting quickstart.sh --non-interactive --local..."
    (
        cd "$work_dir/jaiclaw"
        export AI_PROVIDER="${AI_PROVIDER:-anthropic}"
        # Export the appropriate API key
        case "$AI_PROVIDER" in
            anthropic) export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-}" ;;
            openai)    export OPENAI_API_KEY="${OPENAI_API_KEY:-}" ;;
            gemini)    export GOOGLE_API_KEY="${GOOGLE_API_KEY:-}" ;;
        esac
        ./quickstart.sh --non-interactive --local
    ) &
    local qs_pid=$!
    PIDS_TO_KILL+=("$qs_pid")

    # Wait for health endpoint
    log_info "Waiting for health endpoint on port $E2E_BOOTSTRAP_PORT (timeout: ${E2E_TIMEOUT}s)..."
    if wait_for_health $E2E_BOOTSTRAP_PORT "$E2E_TIMEOUT"; then
        log_pass "Scenario 1: Health endpoint responded on port $E2E_BOOTSTRAP_PORT"
        record_result "1-Bootstrap" "PASS" "Health OK"
    else
        log_fail "Scenario 1: Health endpoint did not respond within ${E2E_TIMEOUT}s"
        log_fail "Inspect: $work_dir"
        record_result "1-Bootstrap" "FAIL" "Health timeout"
        return 1
    fi

    # Clean shutdown
    kill "$qs_pid" 2>/dev/null || true
    wait "$qs_pid" 2>/dev/null || true

    return 0
}

# ─── Scenario 2 — Scaffold + Build + Run ─────────────────────────────────────

run_scenario_2() {
    log_header "Scenario 2 — Scaffold + Build + Run"

    # Check prerequisites
    if ! detect_api_key >/dev/null 2>&1; then
        log_skip "Scenario 2: No API key available for provider '${AI_PROVIDER:-anthropic}'"
        record_result "2-Scaffold" "SKIP" "No API key"
        return 0
    fi

    # Check port availability
    if lsof -ti:$E2E_SCAFFOLD_PORT >/dev/null 2>&1; then
        log_fail "Port $E2E_SCAFFOLD_PORT already in use"
        record_result "2-Scaffold" "FAIL" "Port $E2E_SCAFFOLD_PORT in use"
        return 1
    fi

    local work_dir
    work_dir=$(mktemp -d)/jaiclaw-e2e-scaffold
    mkdir -p "$work_dir"
    DIRS_TO_CLEAN+=("$(dirname "$work_dir")")
    log_info "Work dir: $work_dir"

    local version
    version=$(detect_version)
    log_info "JaiClaw version: $version"

    local system_prompt="${E2E_SYSTEM_PROMPT:-$E2E_DEFAULT_SYSTEM_PROMPT}"

    # Generate manifest
    log_info "Generating scaffold manifest..."
    cat > "$work_dir/manifest.yml" <<EOF
name: e2e-test-app
description: E2E test application for JaiClaw validation
archetype: gateway
provider: ${AI_PROVIDER:-anthropic}
parent: standalone
agent:
  name: E2E Test Agent
  tools-profile: minimal
  system-prompt:
    strategy: inline
    content: |
      ${system_prompt}
skills:
  allow-bundled: []
EOF

    # Scaffold the project
    log_info "Scaffolding project..."
    (cd "$PROJECT_ROOT" && ./mvnw -B io.jaiclaw:jaiclaw-maven-plugin:"${version}":scaffold \
        -Djaiclaw.scaffold.manifest="$work_dir/manifest.yml" \
        -Djaiclaw.scaffold.outputDir="$work_dir")

    if [[ ! -d "$work_dir/e2e-test-app" ]]; then
        log_fail "Scenario 2: Scaffolded project directory not found"
        record_result "2-Scaffold" "FAIL" "Scaffold output missing"
        return 1
    fi
    log_pass "Project scaffolded at $work_dir/e2e-test-app"

    # Build the scaffolded project
    log_info "Building scaffolded project..."
    (cd "$work_dir/e2e-test-app" && mvn package -DskipTests -B)
    log_pass "Scaffolded project built successfully"

    # Start the application
    log_info "Starting scaffolded application on port $E2E_SCAFFOLD_PORT..."
    local api_key_var
    api_key_var=$(detect_api_key)
    (
        cd "$work_dir/e2e-test-app"
        export "${api_key_var}=${!api_key_var}"
        java -jar target/*.jar --server.port=$E2E_SCAFFOLD_PORT
    ) &
    local app_pid=$!
    PIDS_TO_KILL+=("$app_pid")

    # Wait for health endpoint
    log_info "Waiting for health endpoint on port $E2E_SCAFFOLD_PORT (timeout: ${E2E_TIMEOUT}s)..."
    if wait_for_health $E2E_SCAFFOLD_PORT "$E2E_TIMEOUT"; then
        log_pass "Scenario 2: Scaffolded app health endpoint responded"
        record_result "2-Scaffold" "PASS" "Health OK"
    else
        log_fail "Scenario 2: Scaffolded app did not start within ${E2E_TIMEOUT}s"
        log_fail "Inspect: $work_dir"
        record_result "2-Scaffold" "FAIL" "Health timeout"
        return 1
    fi

    # Check MCP endpoint (optional)
    if curl -sf "http://localhost:$E2E_SCAFFOLD_PORT/mcp" >/dev/null 2>&1; then
        log_pass "MCP endpoint responded"
    else
        log_warn "MCP endpoint did not respond (may not be configured)"
    fi

    # Clean shutdown
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true

    return 0
}

# ─── Scenario 3 — Provider Validation ─────────────────────────────────────────

run_scenario_3() {
    log_header "Scenario 3 — Provider Validation"

    # Check prerequisites
    if ! detect_api_key >/dev/null 2>&1; then
        log_skip "Scenario 3: No API key available for provider '${AI_PROVIDER:-anthropic}'"
        record_result "3-Provider" "SKIP" "No API key"
        return 0
    fi

    # Detect running instance
    local port=""
    if curl -sf "http://localhost:$E2E_SCAFFOLD_PORT/api/health" >/dev/null 2>&1; then
        port=$E2E_SCAFFOLD_PORT
        log_info "Using existing instance on port $port (from Scenario 2)"
    elif curl -sf "http://localhost:$E2E_BOOTSTRAP_PORT/api/health" >/dev/null 2>&1; then
        port=$E2E_BOOTSTRAP_PORT
        log_info "Using existing instance on port $port (from Scenario 1)"
    fi

    # If no running instance, start the gateway
    local started_gateway=false
    local gw_pid=""
    if [[ -z "$port" ]]; then
        port=$E2E_SCAFFOLD_PORT
        log_info "No running instance found. Starting gateway on port $port..."

        # Check if gateway JAR exists
        local gw_jar="$PROJECT_ROOT/apps/jaiclaw-gateway-app/target"
        local jar_file
        jar_file=$(find "$gw_jar" -maxdepth 1 -name "jaiclaw-gateway-app-*.jar" ! -name "*-original*" -type f 2>/dev/null | head -1)

        if [[ -z "$jar_file" ]]; then
            log_fail "Gateway JAR not found. Build with: ./mvnw package -pl :jaiclaw-gateway-app -am -DskipTests"
            record_result "3-Provider" "FAIL" "No gateway JAR"
            return 1
        fi

        local api_key_var
        api_key_var=$(detect_api_key)
        (
            export "${api_key_var}=${!api_key_var}"
            java -jar "$jar_file" --server.port=$port
        ) &
        gw_pid=$!
        PIDS_TO_KILL+=("$gw_pid")
        started_gateway=true

        log_info "Waiting for gateway health on port $port..."
        if ! wait_for_health "$port" "$E2E_TIMEOUT"; then
            log_fail "Scenario 3: Gateway did not start within ${E2E_TIMEOUT}s"
            record_result "3-Provider" "FAIL" "Gateway timeout"
            return 1
        fi
    fi

    # Send test message
    log_info "Sending test message to /api/chat..."
    local response
    response=$(curl -sf -X POST "http://localhost:${port}/api/chat" \
        -H "Content-Type: application/json" \
        -d '{
            "content": "Reply with exactly: E2E_TEST_OK",
            "channelId": "api",
            "accountId": "default",
            "peerId": "e2e-user"
        }' 2>&1) || true

    if echo "$response" | grep -q "E2E_TEST_OK"; then
        log_pass "Scenario 3: Provider responded with expected content"
        record_result "3-Provider" "PASS" "Exact match"
    elif [[ -n "$response" ]]; then
        log_pass "Scenario 3: Provider responded (non-empty, content: ${response:0:80}...)"
        record_result "3-Provider" "PASS" "Non-empty response"
    else
        log_fail "Scenario 3: No response from provider"
        record_result "3-Provider" "FAIL" "Empty response"
        # Clean up gateway if we started it
        if [[ "$started_gateway" == "true" && -n "$gw_pid" ]]; then
            kill "$gw_pid" 2>/dev/null || true
        fi
        return 1
    fi

    # Check WebSocket endpoint
    local ws_code
    ws_code=$(curl -sf -o /dev/null -w "%{http_code}" "http://localhost:${port}/ws" 2>&1 || echo "000")
    if [[ "$ws_code" != "000" ]]; then
        log_pass "WebSocket endpoint is listening (HTTP $ws_code)"
    else
        log_warn "WebSocket endpoint not reachable"
    fi

    # Clean up gateway if we started it
    if [[ "$started_gateway" == "true" && -n "$gw_pid" ]]; then
        kill "$gw_pid" 2>/dev/null || true
        wait "$gw_pid" 2>/dev/null || true
    fi

    return 0
}

# ─── Scenario 4 — CLI Validation ──────────────────────────────────────────────

run_scenario_4() {
    log_header "Scenario 4 — CLI Validation (bin/jaiclaw)"

    local launcher="$PROJECT_ROOT/bin/jaiclaw"
    if [[ ! -x "$launcher" ]]; then
        if [[ -f "$launcher" ]]; then
            chmod +x "$launcher"
        else
            log_fail "Scenario 4: bin/jaiclaw not found at $launcher"
            record_result "4-CLI" "FAIL" "Launcher missing"
            return 1
        fi
    fi

    local pass_count=0
    local fail_count=0
    local total=0

    run_cli_test() {
        local label="$1"
        local timeout="$2"
        shift 2
        total=$((total + 1))

        local output
        local exit_code=0
        output=$(timeout "$timeout" "$launcher" "$@" 2>&1) || exit_code=$?

        if [[ $exit_code -eq 0 ]]; then
            log_pass "$label"
            pass_count=$((pass_count + 1))
            return 0
        elif [[ $exit_code -eq 124 ]]; then
            log_fail "$label (timed out after ${timeout}s)"
            fail_count=$((fail_count + 1))
            return 1
        else
            log_fail "$label (exit code: $exit_code)"
            [[ -n "$output" ]] && printf "  ${DIM}%s${NC}\n" "${output:0:200}"
            fail_count=$((fail_count + 1))
            return 1
        fi
    }

    # Fast-path tests (no JVM)
    log_info "Fast-path tests (no JVM startup)..."

    # version
    total=$((total + 1))
    local ver_output
    ver_output=$(timeout $FAST_PATH_TIMEOUT "$launcher" version 2>&1) || true
    if echo "$ver_output" | grep -q "[0-9]"; then
        log_pass "version — $ver_output"
        pass_count=$((pass_count + 1))
    else
        log_fail "version — $ver_output"
        fail_count=$((fail_count + 1))
    fi

    run_cli_test "doctor" $FAST_PATH_TIMEOUT doctor
    run_cli_test "help" $FAST_PATH_TIMEOUT help
    run_cli_test "config show" $FAST_PATH_TIMEOUT config show
    run_cli_test "profiles list" $FAST_PATH_TIMEOUT profiles list

    # JVM-path tests (delegates to jaiclaw-cli.jar)
    # Check if CLI JAR exists first
    local cli_jar_exists=false
    local cli_jar
    cli_jar=$(find "$PROJECT_ROOT/apps/jaiclaw-cli/target" -maxdepth 1 -name "jaiclaw-cli-*.jar" ! -name "*-original*" -type f 2>/dev/null | head -1)
    if [[ -n "$cli_jar" ]]; then
        cli_jar_exists=true
    fi

    if [[ "$cli_jar_exists" == "true" ]]; then
        log_info "JVM-path tests (delegates to jaiclaw-cli.jar)..."
        run_cli_test "tools" $JVM_PATH_TIMEOUT tools
        run_cli_test "status" $JVM_PATH_TIMEOUT status
        run_cli_test "model-list" $JVM_PATH_TIMEOUT model-list
    else
        log_warn "CLI JAR not found — skipping JVM-path tests"
        log_warn "Build with: ./mvnw package -pl :jaiclaw-cli -am -DskipTests"
    fi

    # Profile isolation tests
    log_info "Profile isolation tests..."

    # Create test profile
    total=$((total + 1))
    local profile_output
    profile_output=$(timeout $FAST_PATH_TIMEOUT "$launcher" profiles create e2e-test 2>&1) || true
    if [[ -d "$HOME/.jaiclaw/profiles/e2e-test" ]]; then
        log_pass "profiles create e2e-test"
        pass_count=$((pass_count + 1))
    else
        log_fail "profiles create e2e-test"
        fail_count=$((fail_count + 1))
    fi

    # Show test profile config
    run_cli_test "--profile e2e-test config show" $FAST_PATH_TIMEOUT --profile e2e-test config show

    # Cleanup test profile
    rm -rf "$HOME/.jaiclaw/profiles/e2e-test" 2>/dev/null || true
    log_pass "test profile cleaned up"

    # Record results
    if [[ $fail_count -eq 0 ]]; then
        log_pass "Scenario 4: All $pass_count/$total CLI tests passed"
        record_result "4-CLI" "PASS" "$pass_count/$total passed"
    else
        log_fail "Scenario 4: $fail_count/$total CLI tests failed"
        record_result "4-CLI" "FAIL" "$fail_count/$total failed"
        return 1
    fi

    return 0
}

# ─── Scenario 5 — Docker CLI Image Validation ────────────────────────────────

run_scenario_5() {
    log_header "Scenario 5 — Docker CLI Image Validation"

    # Check Docker is available
    if ! command -v docker &>/dev/null; then
        log_skip "Scenario 5: Docker not available"
        record_result "5-Docker" "SKIP" "Docker not found"
        return 0
    fi

    if ! docker info >/dev/null 2>&1; then
        log_skip "Scenario 5: Docker daemon not running"
        record_result "5-Docker" "SKIP" "Docker not running"
        return 0
    fi

    local version
    version=$(detect_version)
    local image="io.jaiclaw/jaiclaw-cli:${version}"

    # Build the Docker image
    log_info "Building Docker CLI image..."
    (cd "$PROJECT_ROOT" && ./mvnw package -pl :jaiclaw-cli -am -Pk8s -DskipTests -B)

    if ! docker image inspect "$image" >/dev/null 2>&1; then
        log_fail "Scenario 5: Docker image $image not found after build"
        record_result "5-Docker" "FAIL" "Image not built"
        return 1
    fi
    log_pass "Docker image built: $image"

    # Collect image size
    local image_size
    image_size=$(docker images --format "{{.Size}}" "$image" 2>/dev/null | head -1)
    log_info "Image size: $image_size"

    local pass_count=0
    local fail_count=0
    local total=0

    run_docker_test() {
        local label="$1"
        shift
        total=$((total + 1))

        local output
        local exit_code=0
        output=$(timeout 120 docker run --rm "$@" 2>&1) || exit_code=$?

        if [[ $exit_code -eq 0 ]]; then
            log_pass "$label"
            pass_count=$((pass_count + 1))
            return 0
        else
            log_fail "$label (exit code: $exit_code)"
            [[ -n "$output" ]] && printf "  ${DIM}%s${NC}\n" "${output:0:200}"
            fail_count=$((fail_count + 1))
            return 1
        fi
    }

    # Fast-path tests
    log_info "Fast-path tests (in container)..."

    # version
    total=$((total + 1))
    local ver_output
    ver_output=$(timeout 30 docker run --rm "$image" version 2>&1) || true
    if echo "$ver_output" | grep -q "[0-9]"; then
        log_pass "version — $ver_output"
        pass_count=$((pass_count + 1))
    else
        log_fail "version — $ver_output"
        fail_count=$((fail_count + 1))
    fi

    run_docker_test "doctor" "$image" doctor
    run_docker_test "help" "$image" help

    # JVM-path tests
    log_info "JVM-path tests (in container)..."
    run_docker_test "tools" "$image" tools
    run_docker_test "status" "$image" status
    run_docker_test "model-list" "$image" model-list

    # Volume mount tests
    log_info "Volume mount tests..."
    if [[ -d "$HOME/.jaiclaw" ]]; then
        run_docker_test "config show (volume)" -v "$HOME/.jaiclaw:/home/jaiclaw/.jaiclaw" "$image" config show
        run_docker_test "profiles list (volume)" -v "$HOME/.jaiclaw:/home/jaiclaw/.jaiclaw" "$image" profiles list
    else
        log_skip "Volume mount tests: ~/.jaiclaw does not exist"
    fi

    # Non-root user test
    total=$((total + 1))
    local whoami_output
    whoami_output=$(docker run --rm --entrypoint whoami "$image" 2>&1) || true
    if [[ "$whoami_output" == "jaiclaw" ]]; then
        log_pass "runs as non-root user 'jaiclaw'"
        pass_count=$((pass_count + 1))
    else
        log_fail "expected user 'jaiclaw', got '$whoami_output'"
        fail_count=$((fail_count + 1))
    fi

    # Results table
    printf "\n${BOLD}Docker CLI Image Test Results${NC}\n"
    printf "══════════════════════════════\n"
    printf "  Image:             %s\n" "$image"
    printf "  Size:              %s\n" "$image_size"
    printf "  Tests passed:      %s/%s\n" "$pass_count" "$total"

    if [[ $fail_count -eq 0 ]]; then
        log_pass "Scenario 5: All $pass_count/$total Docker CLI tests passed"
        record_result "5-Docker" "PASS" "$pass_count/$total passed, size=$image_size"
    else
        log_fail "Scenario 5: $fail_count/$total Docker CLI tests failed"
        record_result "5-Docker" "FAIL" "$fail_count/$total failed"
        return 1
    fi

    return 0
}

# ─── Report ───────────────────────────────────────────────────────────────────

print_report() {
    local version
    version=$(detect_version)

    printf "\n${BOLD}══════════════════════════════════════${NC}\n"
    printf "${BOLD}  JaiClaw E2E Test Results${NC}\n"
    printf "${BOLD}══════════════════════════════════════${NC}\n\n"

    local any_fail=false

    for entry in "${RESULTS[@]}"; do
        local scenario status detail
        scenario=$(echo "$entry" | cut -d'|' -f1)
        status=$(echo "$entry" | cut -d'|' -f2)
        detail=$(echo "$entry" | cut -d'|' -f3)

        local color
        case "$status" in
            PASS) color="$GREEN" ;;
            FAIL) color="$RED"; any_fail=true ;;
            SKIP) color="$DIM" ;;
        esac

        printf "  %-20s ${color}%-6s${NC} %s\n" "$scenario" "$status" "$detail"
    done

    printf "\n"
    printf "  Provider:  %s\n" "${AI_PROVIDER:-anthropic}"
    printf "  Version:   %s\n" "$version"
    printf "  Java:      %s\n" "${JAVA_HOME:-not set}"
    printf "\n"

    if [[ "$any_fail" == "true" ]]; then
        printf "  ${RED}${BOLD}RESULT: FAILED${NC}\n\n"
        return 1
    else
        printf "  ${GREEN}${BOLD}RESULT: PASSED${NC}\n\n"
        return 0
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
    # Resolve project root
    PROJECT_ROOT=$(detect_project_root)
    export PROJECT_ROOT
    log_info "Project root: $PROJECT_ROOT"

    # Set JAVA_HOME if not set
    if [[ -z "${JAVA_HOME:-}" ]]; then
        if [[ -d "/Users/tap/.sdkman/candidates/java/21.0.9-oracle" ]]; then
            export JAVA_HOME="/Users/tap/.sdkman/candidates/java/21.0.9-oracle"
        fi
    fi
    export PATH="${JAVA_HOME:-}/bin:$PATH"

    # Resolve timeout
    E2E_TIMEOUT="${E2E_TIMEOUT:-120}"

    # Resolve version
    local version
    version=$(detect_version)
    log_info "JaiClaw version: $version"

    # Parse scenarios
    local scenarios_str="${E2E_SCENARIOS:-1,2,3,4}"
    if [[ "$scenarios_str" == "all" ]]; then
        scenarios_str="1,2,3,4,5"
    fi

    IFS=',' read -ra SCENARIOS <<< "$scenarios_str"
    log_info "Scenarios to run: ${SCENARIOS[*]}"

    # Detect API key availability
    if detect_api_key >/dev/null 2>&1; then
        local key_var
        key_var=$(detect_api_key)
        local key_val="${!key_var}"
        log_info "API key: $key_var (${#key_val} chars, starts with ${key_val:0:7}...)"
    else
        log_warn "No API key detected for '${AI_PROVIDER:-anthropic}'"
        log_warn "Scenarios requiring LLM connectivity will be skipped"
    fi

    # Run selected scenarios
    for scenario in "${SCENARIOS[@]}"; do
        scenario=$(echo "$scenario" | tr -d ' ')
        case "$scenario" in
            1) run_scenario_1 || true ;;
            2) run_scenario_2 || true ;;
            3) run_scenario_3 || true ;;
            4) run_scenario_4 || true ;;
            5) run_scenario_5 || true ;;
            *)
                log_warn "Unknown scenario: $scenario"
                record_result "?-Unknown($scenario)" "SKIP" "Unknown scenario"
                ;;
        esac
    done

    # Print report
    if print_report; then
        exit 0
    else
        exit 1
    fi
}

main "$@"
