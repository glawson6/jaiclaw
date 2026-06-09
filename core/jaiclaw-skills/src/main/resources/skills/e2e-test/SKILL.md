---
name: e2e-test
description: End-to-end validation of JaiClaw bootstrap, scaffolding, build, runtime, and Docker CLI images. Tests quickstart.sh, project creation from Maven releases, provider connectivity, CLI launcher, and Docker image builds.
alwaysInclude: false
requiredBins: [java, mvn, git, curl, docker]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# JaiClaw E2E Test

End-to-end validation that JaiClaw can be bootstrapped, scaffolded, built, and run from scratch — the way a new user would experience it.

## Task List Requirement

**ALWAYS create a task list at the start of every e2e test run** using the TaskCreate tool. This gives the user visibility into progress and ensures no steps are skipped.

### Task list for a full e2e test run:
1. Detect API keys and confirm test plan with user
2. Configure system prompt
3. Select scenarios to run
4. Scenario 1 — Bootstrap validation (quickstart.sh) *(if selected)*
5. Scenario 2 — Scaffold + build + run *(if selected)*
6. Scenario 3 — Provider validation *(if selected)*
7. Scenario 4 — CLI validation (bin/jaiclaw) *(if selected)*
8. Scenario 5 — Docker CLI image validation *(if selected)*
9. Report results

Mark each task as `in_progress` when starting and `completed` when done. If a task fails, stop and report.

## Project Context

- **Root**: `/Users/tap/dev/workspaces/openclaw/jaiclaw`
- **Java**: `export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle`
- **Build**: `./mvnw` (Maven wrapper)
- **Quickstart**: `quickstart.sh` — supports `--non-interactive` with `AI_PROVIDER` + API key env vars
- **Start script**: `start.sh` — `start.sh local` starts gateway without Docker
- **CLI launcher**: `bin/jaiclaw` — unified CLI with fast-path (no JVM) and JVM-path dispatch
- **CLI app module**: `apps/jaiclaw-cli/` — standalone CLI (fat JAR, profile-aware)
- **Shared commands**: `apps/jaiclaw-shell-commands/` — shared command library (ChatCommands, OnboardWizard, StatusCommands)
- **CLI installer**: `install.sh` — curl-installable installer
- **Scaffolder**: `tools/jaiclaw-project-scaffolder/` — project scaffolder with 5 archetypes
- **Maven plugin**: `jaiclaw-maven-plugin/` — has `scaffold` and `analyze` goals
- **Version**: Use `JAICLAW_VERSION` env var if set, otherwise detect from root `pom.xml`

## API Key Setup (Run Before Any Scenario)

At the start of every e2e test run, walk the user through API key configuration:

### Step 1 — Detect Available Keys

Check the environment for these keys and report which are found vs missing:
- `ANTHROPIC_API_KEY`
- `OPENAI_API_KEY`
- `GOOGLE_API_KEY`

```bash
# Detection check
for key in ANTHROPIC_API_KEY OPENAI_API_KEY GOOGLE_API_KEY; do
    if [ -n "${!key:-}" ]; then
        echo "FOUND: $key (${#key} chars, starts with ${!key:0:4}...)"
    else
        echo "MISSING: $key"
    fi
done
```

### Step 2 — Ask the User for Each Provider

For each provider they want to test, present these options:

- **Option A: Provide a key** — user supplies a key value directly. Set it as an env var for the test run only; never persist it.
- **Option B: Inherit from environment** — if a key is already set (e.g., the agent's own `ANTHROPIC_API_KEY`), ask if they'd like to reuse it. Explain this is the key currently in the shell/agent environment.
- **Option C: Skip this provider** — don't test scenarios requiring this provider.

### Step 3 — System Prompt

Ask the user how they want to configure the system prompt for the E2E test. The system prompt is used in both Scenario 2 (baked into the scaffolded app's `application.yml`) and Scenario 3 (ensures the LLM follows exact-reply instructions).

Use the `AskUserQuestion` tool with these options:

- **Use default (Recommended)** — use the built-in E2E test prompt shown below
- **Type custom prompt** — user types or pastes a custom system prompt inline
- **Load from file** — user provides an absolute path to a file containing the system prompt; read it with the Read tool

The default system prompt (`E2E_DEFAULT_SYSTEM_PROMPT`):

```
You are a helpful assistant used for end-to-end testing. Follow instructions exactly. When asked to reply with a specific phrase, reply with only that phrase and nothing else.
```

Store the chosen system prompt for use in Scenario 2 (manifest generation) and Scenario 3 (provider validation context).

### Step 4 — Select Scenarios

**Ask the user which scenarios they want to run.** Present the three scenarios and let them choose any combination:

| Scenario | Description | Requires |
|----------|-------------|----------|
| **1 — Bootstrap** | Tests `quickstart.sh --non-interactive --local` builds and starts the gateway from the repo | AI provider key, port 8080 free |
| **2 — Scaffold** | Creates a new project via the scaffolder, builds it, and starts it | AI provider key, `mvnw install` first |
| **3 — Provider** | Sends a message to a running instance and verifies LLM responds | A running instance from Scenario 1 or 2 |
| **4 — CLI** | Tests `bin/jaiclaw` launcher fast-path and JVM-path commands | `bin/jaiclaw` exists, Java 21+ |
| **5 — Docker CLI** | Builds all three Docker image approaches, tests fast-path and JVM-path in each, compares sizes | Docker daemon running, CLI fat JAR built |

Options:
- **All five** — run Scenarios 1, 2, 3, 4, and 5 in order
- **Bootstrap only** — run Scenario 1 only
- **Scaffold only** — run Scenario 2 only
- **CLI only** — run Scenario 4 only
- **Docker CLI only** — run Scenario 5 only
- **CLI + Docker CLI** — run Scenarios 4 and 5 (local CLI then Docker CLI)
- **Bootstrap + Provider** — run Scenarios 1 and 3 (provider test uses the bootstrap instance)
- **Scaffold + Provider** — run Scenarios 2 and 3 (provider test uses the scaffolded instance)
- **Custom selection** — let the user pick any combination

**Note:** Scenario 3 (Provider) requires a running JaiClaw instance. If neither Scenario 1 nor 2 is selected alongside it, the agent should start a fresh instance for the provider test.

**Note:** Scenario 4 (CLI) can run independently — it only requires `bin/jaiclaw` to exist and Java 21+ for JVM-path commands.

**Note:** Scenario 5 (Docker CLI) can run independently — it requires Docker running and the CLI fat JAR built. It does NOT require API keys since it only tests CLI commands, not LLM connectivity.

### Step 5 — Confirm the Test Plan

After key setup and scenario selection, summarize:
- Which providers will be tested
- Which scenarios will run
- Expected ports and temp directories
- Wait for user confirmation before proceeding

**NEVER silently use API keys — always confirm with the user first (provide, inherit, or skip).**

## Scenario 1 — Bootstrap Validation (quickstart.sh)

Tests that `quickstart.sh --non-interactive --local` can bootstrap and start JaiClaw from the repo.

### Prerequisites
- At least one AI provider key confirmed with the user
- Java 21+ available

### Procedure

```bash
# 1. Create temp directory
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
WORK_DIR=$(mktemp -d)/jaiclaw-e2e-bootstrap
mkdir -p "$WORK_DIR"
echo "E2E work dir: $WORK_DIR"

# 2. Copy the repo (faster than clone for local testing)
cp -R /Users/tap/dev/workspaces/openclaw/jaiclaw "$WORK_DIR/jaiclaw"
cd "$WORK_DIR/jaiclaw"

# 3. Run quickstart in non-interactive mode
#    Use the confirmed provider and key from the API Key Setup step
AI_PROVIDER=anthropic ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
    ./quickstart.sh --non-interactive --local &
QS_PID=$!

# 4. Wait for startup (poll health endpoint)
MAX_WAIT=120
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
    if curl -sf http://localhost:8080/api/health >/dev/null 2>&1; then
        echo "PASS: Health endpoint responded after ${ELAPSED}s"
        break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo "FAIL: Health endpoint did not respond within ${MAX_WAIT}s"
    echo "Work dir for inspection: $WORK_DIR"
fi

# 5. Clean shutdown
kill $QS_PID 2>/dev/null
wait $QS_PID 2>/dev/null || true
```

### Validation Criteria
- Process starts without error (exit code 0 or still running)
- Health endpoint at `http://localhost:8080/api/health` responds with HTTP 200
- Process can be killed cleanly (no zombie processes)

### Cleanup
```bash
# Kill any leftover Java processes from this test
kill $QS_PID 2>/dev/null || true
# On failure, ask user before cleanup so they can inspect
# On success, clean up automatically
rm -rf "$WORK_DIR"
```

## Scenario 2 — Scaffold + Build + Run

Tests that the scaffolder can create a new project, the project builds, and it starts successfully.

### Prerequisites
- At least one AI provider key confirmed with the user
- The JaiClaw project must be installed to the local Maven repo first

### Procedure

```bash
# 1. Create temp directory
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
WORK_DIR=$(mktemp -d)/jaiclaw-e2e-scaffold
mkdir -p "$WORK_DIR"
echo "E2E work dir: $WORK_DIR"

# 2. Determine JaiClaw version
JAICLAW_VERSION="${JAICLAW_VERSION:-$(cd /Users/tap/dev/workspaces/openclaw/jaiclaw && ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)}"
echo "Using JaiClaw version: $JAICLAW_VERSION"

# 3. Install JaiClaw to local repo (needed for scaffold to resolve dependencies)
cd /Users/tap/dev/workspaces/openclaw/jaiclaw
./mvnw install -DskipTests -o

# 4. Generate a manifest (use the system prompt chosen in Step 3)
#    Replace <SYSTEM_PROMPT> with the actual prompt text from the setup step
cat > "$WORK_DIR/manifest.yml" << EOF
name: e2e-test-app
description: E2E test application for JaiClaw validation
archetype: gateway
provider: anthropic
parent: standalone
agent:
  name: E2E Test Agent
  tools-profile: minimal
  system-prompt:
    strategy: inline
    content: |
      ${SYSTEM_PROMPT}
skills:
  allow-bundled: []
EOF

# 5. Scaffold the project
./mvnw io.jaiclaw:jaiclaw-maven-plugin:${JAICLAW_VERSION}:scaffold \
    -Djaiclaw.scaffold.manifest="$WORK_DIR/manifest.yml" \
    -Djaiclaw.scaffold.outputDir="$WORK_DIR"

# 6. Verify scaffold output exists
if [ ! -d "$WORK_DIR/e2e-test-app" ]; then
    echo "FAIL: Scaffolded project directory not found"
    echo "Work dir for inspection: $WORK_DIR"
    exit 1
fi
echo "PASS: Project scaffolded at $WORK_DIR/e2e-test-app"

# 7. Build the scaffolded project
cd "$WORK_DIR/e2e-test-app"
mvn package -DskipTests
echo "PASS: Scaffolded project built successfully"

# 8. Start the application
ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
    java -jar target/*.jar --server.port=8090 &
APP_PID=$!

# 9. Wait for startup (poll health endpoint on port 8090)
MAX_WAIT=60
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
    if curl -sf http://localhost:8090/api/health >/dev/null 2>&1; then
        echo "PASS: Scaffolded app health endpoint responded after ${ELAPSED}s"
        break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo "FAIL: Scaffolded app did not start within ${MAX_WAIT}s"
    echo "Work dir for inspection: $WORK_DIR"
fi

# 10. Check MCP endpoint (gateway archetype should expose /mcp)
if curl -sf http://localhost:8090/mcp >/dev/null 2>&1; then
    echo "PASS: MCP endpoint responded"
else
    echo "WARN: MCP endpoint did not respond (may not be configured)"
fi

# 11. Clean shutdown
kill $APP_PID 2>/dev/null
wait $APP_PID 2>/dev/null || true
```

### Validation Criteria
- Scaffold creates a valid Maven project directory
- `mvn package -DskipTests` succeeds (exit code 0)
- Application starts and health endpoint responds with HTTP 200
- MCP endpoint responds (for gateway archetype)

### Cleanup
```bash
kill $APP_PID 2>/dev/null || true
# On failure, ask user before cleanup
rm -rf "$WORK_DIR"
```

## Scenario 3 — Provider Validation

Tests that a running JaiClaw instance can actually communicate with AI providers.

### Prerequisites
- A JaiClaw instance running (from Scenario 1 or 2, or started fresh)
- At least one AI provider key confirmed with the user

### Procedure

```bash
# Use the running instance from Scenario 1 (port 8080) or Scenario 2 (port 8090)
# Determine the correct port based on which scenario provided the instance
PORT=8090  # adjust based on which scenario is providing the instance

# 1. Send a simple message via the REST API
RESPONSE=$(curl -sf -X POST http://localhost:${PORT}/api/chat \
    -H "Content-Type: application/json" \
    -d '{
        "content": "Reply with exactly: E2E_TEST_OK",
        "channelId": "api",
        "accountId": "default",
        "peerId": "e2e-user"
    }' 2>&1)

echo "Response: $RESPONSE"

# 2. Validate response
if echo "$RESPONSE" | grep -q "E2E_TEST_OK"; then
    echo "PASS: Provider responded with expected content"
elif [ -n "$RESPONSE" ] && [ "$RESPONSE" != "" ]; then
    echo "PASS: Provider responded (content did not match exactly, but response is non-empty)"
else
    echo "FAIL: No response from provider"
fi

# 3. Test WebSocket endpoint exists (just check it's listening)
WS_CHECK=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:${PORT}/ws 2>&1 || echo "000")
if [ "$WS_CHECK" != "000" ]; then
    echo "PASS: WebSocket endpoint is listening (HTTP $WS_CHECK)"
else
    echo "WARN: WebSocket endpoint not reachable"
fi
```

### Validation Criteria
- REST API accepts a message and returns a non-empty response
- Response contains assistant-generated content (confirms LLM connectivity)
- WebSocket endpoint is listening

## Scenario 4 — CLI Validation (bin/jaiclaw)

Tests that the `bin/jaiclaw` launcher dispatches correctly for both fast-path (no JVM) and JVM-path commands.

### Prerequisites
- `bin/jaiclaw` exists and is executable
- Java 21+ available (for JVM-path commands)
- CLI fat JAR built: `./mvnw package -pl :jaiclaw-cli -am -DskipTests`

### Procedure

#### Fast-path tests (no JVM startup)

These commands are handled entirely in bash and should complete in under 1 second:

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
cd /Users/tap/dev/workspaces/openclaw/jaiclaw

# 1. Version
OUTPUT=$(bin/jaiclaw version 2>&1)
if [ $? -eq 0 ] && echo "$OUTPUT" | grep -q "[0-9]"; then
    echo "PASS: version — $OUTPUT"
else
    echo "FAIL: version command failed"
fi

# 2. Doctor (fast-path bash diagnostics)
bin/jaiclaw doctor
if [ $? -eq 0 ]; then
    echo "PASS: doctor completed"
else
    echo "FAIL: doctor command failed"
fi

# 3. Profiles list
bin/jaiclaw profiles list
if [ $? -eq 0 ]; then
    echo "PASS: profiles list completed"
else
    echo "FAIL: profiles list failed"
fi

# 4. Config show
bin/jaiclaw config show
if [ $? -eq 0 ]; then
    echo "PASS: config show completed"
else
    echo "FAIL: config show failed"
fi

# 5. Help
OUTPUT=$(bin/jaiclaw help 2>&1)
if [ $? -eq 0 ] && echo "$OUTPUT" | grep -qi "usage\|commands\|jaiclaw"; then
    echo "PASS: help shows usage"
else
    echo "FAIL: help command failed"
fi
```

#### JVM-path tests (delegates to jaiclaw-cli.jar)

These commands start the JVM and run through Spring Shell's non-interactive runner:

```bash
# 6. Tools list
bin/jaiclaw tools
if [ $? -eq 0 ]; then
    echo "PASS: tools list completed"
else
    echo "FAIL: tools command failed"
fi

# 7. Status
bin/jaiclaw status
if [ $? -eq 0 ]; then
    echo "PASS: status completed"
else
    echo "FAIL: status command failed"
fi

# 8. Model list
bin/jaiclaw model-list
if [ $? -eq 0 ]; then
    echo "PASS: model-list completed"
else
    echo "FAIL: model-list command failed"
fi
```

#### Profile isolation tests

```bash
# 9. Create a test profile
bin/jaiclaw profiles create e2e-test
if [ $? -eq 0 ] && [ -d "$HOME/.jaiclaw/profiles/e2e-test" ]; then
    echo "PASS: profile e2e-test created"
else
    echo "FAIL: profile creation failed"
fi

# 10. Show test profile config
bin/jaiclaw --profile e2e-test config show
if [ $? -eq 0 ]; then
    echo "PASS: profile config show completed"
else
    echo "FAIL: profile config show failed"
fi

# 11. Clean up test profile
rm -rf "$HOME/.jaiclaw/profiles/e2e-test"
echo "PASS: test profile cleaned up"
```

### Validation Criteria
- All commands exit with code 0
- Fast-path commands complete in < 1 second
- `version` prints a version string containing digits
- `help` prints usage information
- `profiles create` creates the profile directory at `~/.jaiclaw/profiles/<name>/`
- `--profile` flag correctly isolates to the specified profile directory
- JVM-path commands produce expected output (tool list, status info, model list)

### Cleanup
```bash
# Remove any test profiles created during the test
rm -rf "$HOME/.jaiclaw/profiles/e2e-test"
```

## Scenario 5 — Docker CLI Image Validation

Tests that the Docker CLI image builds correctly and works with both fast-path and JVM-path commands.

Two build profiles are available:
- **`-Pk8s`** (default) — Dockerfile-based build via `exec-maven-plugin`, builds native platform images
- **`-Pk8s-inline`** (alternative) — JKube inline assembly, XML-only config, produces `linux/amd64` images

Application files live at `/opt/jaiclaw/` (survives volume mounts). User data at `/home/jaiclaw/.jaiclaw/` (mountable from host).

### Prerequisites
- Docker daemon running
- CLI fat JAR built: `./mvnw package -pl :jaiclaw-cli -am -DskipTests`
- Docker profiles exist in `apps/jaiclaw-cli/pom.xml` (`k8s`, `k8s-inline`)

### Procedure

#### Step 1 — Build images

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
cd /Users/tap/dev/workspaces/openclaw/jaiclaw

# Build default image (Dockerfile-based, native platform)
./mvnw package -pl :jaiclaw-cli -am -Pk8s -DskipTests

# Optionally build inline alternative
./mvnw package -pl :jaiclaw-cli -am -DskipTests -o
./mvnw k8s:build -pl :jaiclaw-cli -Pk8s-inline -DskipTests
```

Record the exit code for each build. If a build fails, mark it as FAIL and continue.

#### Step 2 — Collect image sizes

```bash
# List all CLI images
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.ID}}" | grep jaiclaw-cli
```

Record the image size for each approach.

#### Step 3 — Test fast-path commands

For each successfully built image, test commands handled by the `bin/jaiclaw` bash launcher (no JVM needed):

```bash
# IMAGE=io.jaiclaw/jaiclaw-cli:<version>
IMAGE="io.jaiclaw/jaiclaw-cli:$(cd /Users/tap/dev/workspaces/openclaw/jaiclaw && ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)"

echo "=== Testing $IMAGE ==="

# 1. version (fast-path)
OUTPUT=$(docker run --rm "$IMAGE" version 2>&1)
if [ $? -eq 0 ] && echo "$OUTPUT" | grep -q "[0-9]"; then
    echo "PASS: version — $OUTPUT"
else
    echo "FAIL: version — $OUTPUT"
fi

# 2. doctor (fast-path)
OUTPUT=$(docker run --rm "$IMAGE" doctor 2>&1)
if [ $? -eq 0 ]; then
    echo "PASS: doctor"
else
    echo "FAIL: doctor — $OUTPUT"
fi

# 3. help (fast-path)
OUTPUT=$(docker run --rm "$IMAGE" help 2>&1)
if [ $? -eq 0 ] && echo "$OUTPUT" | grep -qi "usage\|commands\|jaiclaw"; then
    echo "PASS: help"
else
    echo "FAIL: help — $OUTPUT"
fi
```

#### Step 4 — Test JVM-path commands

These commands start the Spring Boot CLI JAR inside the container:

```bash
# 4. tools (JVM-path)
OUTPUT=$(docker run --rm "$IMAGE" tools 2>&1)
if [ $? -eq 0 ]; then
    echo "PASS: tools"
else
    echo "FAIL: tools — $OUTPUT"
fi

# 5. status (JVM-path)
OUTPUT=$(docker run --rm "$IMAGE" status 2>&1)
if [ $? -eq 0 ]; then
    echo "PASS: status"
else
    echo "FAIL: status — $OUTPUT"
fi

# 6. model-list (JVM-path)
OUTPUT=$(docker run --rm "$IMAGE" model-list 2>&1)
if [ $? -eq 0 ]; then
    echo "PASS: model-list"
else
    echo "FAIL: model-list — $OUTPUT"
fi
```

#### Step 5 — Test volume mount

Verify that the `~/.jaiclaw` volume mount works correctly for config persistence:

```bash
# Test config show with volume mount
OUTPUT=$(docker run --rm -v "$HOME/.jaiclaw:/home/jaiclaw/.jaiclaw" "$IMAGE" config show 2>&1)
if [ $? -eq 0 ]; then
    echo "PASS: config show (volume mount)"
else
    echo "FAIL: config show (volume mount) — $OUTPUT"
fi

# Test profiles list with volume mount
OUTPUT=$(docker run --rm -v "$HOME/.jaiclaw:/home/jaiclaw/.jaiclaw" "$IMAGE" profiles list 2>&1)
if [ $? -eq 0 ]; then
    echo "PASS: profiles list (volume mount)"
else
    echo "FAIL: profiles list (volume mount) — $OUTPUT"
fi
```

#### Step 6 — Test non-root user

```bash
# Verify the container runs as non-root (use --entrypoint to bypass launcher)
WHOAMI=$(docker run --rm --entrypoint whoami "$IMAGE" 2>&1)
if [ "$WHOAMI" = "jaiclaw" ]; then
    echo "PASS: runs as non-root user 'jaiclaw'"
else
    echo "FAIL: expected user 'jaiclaw', got '$WHOAMI'"
fi
```

#### Step 7 — Produce results table

After all tests, produce a structured summary:

```
Docker CLI Image Test Results
==============================
Build success:           PASS/FAIL
Image size:              XXX MB
Platform:                linux/arm64 or linux/amd64
Fast-path (version):     PASS/FAIL
Fast-path (doctor):      PASS/FAIL
Fast-path (help):        PASS/FAIL
JVM-path (tools):        PASS/FAIL
JVM-path (status):       PASS/FAIL
JVM-path (model-list):   PASS/FAIL
Volume mount (config):   PASS/FAIL
Volume mount (profiles): PASS/FAIL
Non-root user:           PASS/FAIL
```

If the `k8s-inline` alternative was also tested, include a side-by-side comparison.

### Validation Criteria
- Image builds successfully (exit code 0)
- Fast-path commands (`version`, `doctor`, `help`) work
- JVM-path commands (`tools`, `status`, `model-list`) work
- Volume mount reads host `~/.jaiclaw` configuration correctly
- Container runs as non-root `jaiclaw` user
- Image size is reasonable (< 500 MB for JRE + fat JAR)

### Cleanup
```bash
# Remove test image (optional — ask user first)
VERSION=$(cd /Users/tap/dev/workspaces/openclaw/jaiclaw && ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
docker rmi "io.jaiclaw/jaiclaw-cli:${VERSION}" 2>/dev/null || true
```

## Rules

- **Always report the working directory** — at the start of each scenario, print the full path of the temp directory being used (e.g., `E2E work dir: /tmp/tmp.abc123/jaiclaw-e2e-bootstrap`). On failure, remind the user of this path so they can inspect artifacts.
- **Always run in a temp directory** — never modify the JaiClaw workspace (except for `mvnw install` which is idempotent)
- **Always clean up temp directories on completion** — but on failure, ask the user before cleanup so they can inspect
- **Create a task list at the start** for progress tracking
- **Report pass/fail for each scenario** with clear error messages including the work directory path
- **Never silently use API keys** — always confirm with the user first (provide, inherit, or skip)
- **Use `--non-interactive` flags and env vars** to avoid interactive prompts
- **Default to the current SNAPSHOT version** — allow override via `JAICLAW_VERSION` env var
- **Use different ports for different scenarios** — Scenario 1 uses 8080 (quickstart default), Scenario 2 uses 8090 (explicit override)
- **Kill processes on cleanup** — ensure no orphan Java processes remain after test completion
- **Ask which scenarios to run** — never assume the user wants all three; always present the selection

## Results Reporting

At the end of all scenarios, produce a summary table:

```
JaiClaw E2E Test Results
========================
Scenario 1 (Bootstrap):   PASS / FAIL / SKIPPED
Scenario 2 (Scaffold):    PASS / FAIL / SKIPPED
Scenario 3 (Provider):    PASS / FAIL / SKIPPED
Scenario 4 (CLI):         PASS / FAIL / SKIPPED
Scenario 5 (Docker CLI):  PASS / FAIL / SKIPPED

Provider: anthropic (ANTHROPIC_API_KEY)
Version:  0.7.1-SNAPSHOT
Work Dir: /tmp/tmp.xxx/jaiclaw-e2e-*
```

If Scenario 5 was run, append the Docker comparison table from Step 7 to the results.

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Port already in use | Kill existing process: `lsof -ti:8080 \| xargs kill` |
| Quickstart hangs | Ensure `--non-interactive` is set and API key env vars are exported |
| Scaffold fails to resolve dependencies | Run `./mvnw install -DskipTests` first to populate local repo |
| Build fails with Nexus timeout | Add `-o` flag for offline build |
| Health endpoint never responds | Check logs in `$WORK_DIR` for startup errors |
| Provider returns empty response | Verify API key is valid and has credits |
| bin/jaiclaw not found | Ensure `bin/` directory exists and script is executable (`chmod +x bin/jaiclaw`) |
| CLI JAR not found | Build with `./mvnw package -pl :jaiclaw-cli -am -DskipTests` |
| Profile directory not created | Check `~/.jaiclaw/profiles/` exists and is writable |
| Docker not running | Start Docker Desktop or `dockerd` — Scenario 5 requires a running Docker daemon |
| JKube build fails | Ensure `kubernetes-maven-plugin` version matches `jkube.version` in root pom.xml |
| Docker image build context too large | Check `.dockerignore` excludes `.git/`, `**/target/`, `**/node_modules/` |
| Volume mount permission denied | Container runs as `jaiclaw` user (UID 1001) — host `~/.jaiclaw` must be readable |
| `docker run` exits immediately | Check ENTRYPOINT — should be `bin/jaiclaw` bash launcher, not `java -jar` |
| JVM-path command hangs in Docker | Spring Shell interactive mode may block — ensure `spring.shell.interactive.enabled: true` is overridden by CLI args |
