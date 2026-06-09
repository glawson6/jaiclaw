# JaiClaw E2E Test Runner

Non-interactive end-to-end test suite for JaiClaw. Validates bootstrap, scaffolding, build, runtime, CLI, and Docker images.

## Scenarios

| # | Name | What it tests | Requires |
|---|------|---------------|----------|
| 1 | Bootstrap | `quickstart.sh --non-interactive --local` builds and starts gateway | API key, port 8080 free |
| 2 | Scaffold | Maven scaffolder creates a project, builds, and starts it | API key, `mvnw install` first |
| 3 | Provider | Sends a chat message and verifies LLM responds | API key, running instance (or starts one) |
| 4 | CLI | Tests `bin/jaiclaw` fast-path and JVM-path commands | `bin/jaiclaw`, Java 21+, CLI JAR built |
| 5 | Docker CLI | Builds CLI Docker image, tests commands in container | Docker running, CLI module built |

## Running Locally (No Docker)

```bash
# Set Java (required)
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Build first (needed for most scenarios)
./mvnw install -DskipTests -o

# CLI only (no API key needed)
E2E_SCENARIOS=4 ./e2e/run-e2e-tests.sh

# Bootstrap + provider + CLI
E2E_SCENARIOS=1,3,4 \
  AI_PROVIDER=anthropic \
  ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
  ./e2e/run-e2e-tests.sh

# All scenarios
E2E_SCENARIOS=all \
  ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
  ./e2e/run-e2e-tests.sh
```

## Running in GitHub Actions

The workflow runs automatically on push to `main` (scenarios 1,3,4,5 by default) and can be triggered manually with custom scenario selection.

**Manual dispatch:** Go to Actions > E2E Tests > Run workflow. Choose scenarios and AI provider.

**Secrets required:** `ANTHROPIC_API_KEY` (or `OPENAI_API_KEY` / `GOOGLE_API_KEY` depending on provider). Scenarios 1, 2, and 3 need an API key. Scenarios 4 and 5 work without one.

## Running with Docker

Build the e2e test runner image, then run tests inside a container:

```bash
# Build the image (handles .dockerignore swap automatically)
./e2e/build.sh

# CLI only
docker run --rm \
  -e E2E_SCENARIOS=4 \
  io.jaiclaw/jaiclaw-e2e-runner:0.7.1-SNAPSHOT

# With API key
docker run --rm \
  -e E2E_SCENARIOS=1,3 \
  -e AI_PROVIDER=anthropic \
  -e ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
  io.jaiclaw/jaiclaw-e2e-runner:0.7.1-SNAPSHOT

# Docker CLI tests (needs Docker socket)
docker run --rm \
  -e E2E_SCENARIOS=5 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  io.jaiclaw/jaiclaw-e2e-runner:0.7.1-SNAPSHOT
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `E2E_SCENARIOS` | `1,2,3,4` | Comma-separated scenario numbers or `all` |
| `AI_PROVIDER` | `anthropic` | Provider: `anthropic`, `openai`, `gemini` |
| `ANTHROPIC_API_KEY` | — | Anthropic API key |
| `ANTHROPIC_BASE_URL` | — | Optional base URL override |
| `ANTHROPIC_MODEL` | — | Optional model override |
| `OPENAI_API_KEY` | — | OpenAI API key |
| `GOOGLE_API_KEY` | — | Google Gemini API key |
| `E2E_SYSTEM_PROMPT` | built-in | System prompt for scenarios 2 & 3 |
| `E2E_TIMEOUT` | `120` | Server startup timeout (seconds) |
| `E2E_KEEP_ARTIFACTS` | `false` | Keep temp directories on success |
| `JAICLAW_VERSION` | from pom.xml | Override version detection |
| `PROJECT_ROOT` | auto-detected | Override project root path |

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Port already in use | `lsof -ti:8080 \| xargs kill` |
| Quickstart hangs | Ensure `AI_PROVIDER` + API key env vars are set |
| Scaffold can't resolve deps | Run `./mvnw install -DskipTests` first |
| Build timeout (Nexus) | Add `-o` for offline: `./mvnw install -DskipTests -o` |
| Health endpoint never responds | Check logs in the temp work directory |
| Provider returns empty | Verify API key is valid and has credits |
| `bin/jaiclaw` not found | Ensure `bin/` directory exists and script is `chmod +x` |
| CLI JAR not found | `./mvnw package -pl :jaiclaw-cli -am -DskipTests` |
| Docker not available | Start Docker Desktop or `dockerd` |
| Docker build context too large | The `build.sh` script swaps `.dockerignore` automatically |
