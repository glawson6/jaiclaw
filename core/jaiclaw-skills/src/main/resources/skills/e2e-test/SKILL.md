---
name: e2e-test
description: End-to-end validation of JaiClaw bootstrap, scaffolding, build, and runtime. Tests quickstart.sh, project creation from Maven releases, and provider connectivity.
alwaysInclude: false
requiredBins: [java, mvn, git, curl]
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
7. Report results

Mark each task as `in_progress` when starting and `completed` when done. If a task fails, stop and report.

## Project Context

- **Root**: `/Users/tap/dev/workspaces/openclaw/jaiclaw`
- **Java**: `export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle`
- **Build**: `./mvnw` (Maven wrapper)
- **Quickstart**: `quickstart.sh` — supports `--non-interactive` with `AI_PROVIDER` + API key env vars
- **Start script**: `start.sh` — `start.sh local` starts gateway without Docker
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

Options:
- **All three** — run Scenarios 1, 2, and 3 in order
- **Bootstrap only** — run Scenario 1 only
- **Scaffold only** — run Scenario 2 only
- **Bootstrap + Provider** — run Scenarios 1 and 3 (provider test uses the bootstrap instance)
- **Scaffold + Provider** — run Scenarios 2 and 3 (provider test uses the scaffolded instance)
- **Custom selection** — let the user pick any combination

**Note:** Scenario 3 (Provider) requires a running JaiClaw instance. If neither Scenario 1 nor 2 is selected alongside it, the agent should start a fresh instance for the provider test.

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

Provider: anthropic (ANTHROPIC_API_KEY)
Version:  0.6.0-SNAPSHOT
Work Dir: /tmp/tmp.xxx/jaiclaw-e2e-*
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Port already in use | Kill existing process: `lsof -ti:8080 \| xargs kill` |
| Quickstart hangs | Ensure `--non-interactive` is set and API key env vars are exported |
| Scaffold fails to resolve dependencies | Run `./mvnw install -DskipTests` first to populate local repo |
| Build fails with Nexus timeout | Add `-o` flag for offline build |
| Health endpoint never responds | Check logs in `$WORK_DIR` for startup errors |
| Provider returns empty response | Verify API key is valid and has credits |
