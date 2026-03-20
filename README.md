# JClaw

Java/Spring AI personal assistant framework. Embeddable agent runtime with tool execution, skills, plugins, memory, and multi-channel messaging (Telegram, Slack, Discord, Email, SMS).

Built on Java 21, Spring Boot 3.5, Spring AI 1.1, and Spring Shell 3.4.

## Quick Start

### Option 1: Docker (easiest — just needs Docker)

```bash
git clone https://github.com/jclaw/jclaw.git
cd jclaw
./quickstart.sh
```

This builds the Docker image and starts the gateway. If no API key is provided, it also starts Ollama and pulls a local LLM model (~3GB download).

To skip Ollama and use a cloud LLM provider instead:

```bash
# With Anthropic
ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh

# With OpenAI
OPENAI_API_KEY=sk-... ./quickstart.sh

# With Google Gemini
GEMINI_API_KEY=... ./quickstart.sh
```

To pre-pull the Ollama image in the background while the quickstart runs:

```bash
docker pull ollama/ollama:latest
```

Test with (the API key is auto-generated at `~/.jclaw/api-key` on first run):

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "hello"}'
```

### Option 2: start.sh (recommended for daily use)

After the initial build, use `start.sh` to run the gateway or interactive shell. It loads API keys from `docker-compose/.env` automatically.

```bash
# Edit API keys (one time)
vi docker-compose/.env

# Start the gateway locally (default) — requires Java 21
./start.sh

# Start the interactive CLI shell (local Java)
./start.sh shell

# Start the interactive CLI shell (Docker, no Java needed)
./start.sh cli

# Start gateway via Docker Compose
./start.sh docker

# Force rebuild from source (after code changes)
./start.sh --force-build

# Force rebuild Docker images
./start.sh --force-build docker
./start.sh --force-build cli

# Stop the Docker stack
./start.sh stop

# Tail gateway logs
./start.sh logs
```

### Option 3: setup.sh (first-time developer setup)

```bash
git clone https://github.com/jclaw/jclaw.git
cd jclaw
./setup.sh
```

The setup script installs Java 21 via SDKMAN (if needed), builds all modules, and launches the interactive shell. Run the onboarding wizard to configure your LLM provider:

```bash
jclaw> onboard
```

Or run the gateway instead:

```bash
./setup.sh --gateway
```

## Running the Gateway (REST API + Channels)

For HTTP/WebSocket access or to connect messaging channels:

```bash
./start.sh           # local Java (default, reads docker-compose/.env)
./start.sh docker    # Docker Compose (reads docker-compose/.env)
```

Or with environment variables directly:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-gateway-app
```

Test with curl (the gateway requires an `X-API-Key` header by default — see [Security](#security)):

```bash
# Chat
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "hello"}'

# Health check
curl http://localhost:8080/api/health

# List channels
curl http://localhost:8080/api/channels
```

### Connecting Channels

All messaging channels support a **local dev mode** that requires no public endpoint:

| Channel  | Local Dev Mode       | Setup Time |
|----------|---------------------|------------|
| Telegram | Long polling        | ~2 min     |
| Slack    | Socket Mode         | ~5 min     |
| Discord  | Gateway WebSocket   | ~5 min     |
| Email    | IMAP polling        | ~3 min     |
| SMS      | Twilio webhook      | ~5 min     |

Add channel tokens to `docker-compose/.env` and restart, or pass as environment variables:

```bash
# Telegram (polling mode — no webhook needed)
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app

# Slack (Socket Mode — no webhook needed)
SLACK_BOT_TOKEN=xoxb-... \
SLACK_APP_TOKEN=xapp-... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app

# Discord (Gateway mode — no webhook needed)
DISCORD_BOT_TOKEN=... \
DISCORD_USE_GATEWAY=true \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

See [docs/OPERATIONS.md](docs/OPERATIONS.md) for full channel setup instructions including Email, SMS, and production webhook configuration.

## Running the Interactive Shell

The shell provides a Spring Shell CLI for chatting with the agent directly in your terminal.

```bash
./start.sh shell       # local Java (requires Java 21)
./start.sh cli         # Docker (no Java needed)
```

Or with Maven directly:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-shell
```

## Scripts

| Script | Purpose |
|--------|---------|
| `start.sh` | **Daily driver** — start gateway (Docker or local), interactive shell (local or Docker). Reads `docker-compose/.env` for config. Use `--force-build` to rebuild Docker images. |
| `quickstart.sh` | **First-time Docker setup** — clones, builds image, starts stack, pulls Ollama if needed. Use `--force-build` to rebuild, `--reconfigure` to re-run interactive setup. |
| `setup.sh` | **First-time developer setup** — installs Java 21, builds from source, launches shell or gateway. |

## Configuration

Configuration lives in a `.env` file. By default this is `docker-compose/.env`, but you can choose `~/.jclaw/.env` (persists across projects) during first run or via `./quickstart.sh --reconfigure`. The chosen location is saved in `~/.jclawrc` and respected by all scripts.

You can also set `JCLAW_ENV_FILE` directly to point to any `.env` file.

| Variable | Default | Description |
|----------|---------|-------------|
| `JCLAW_SECURITY_MODE` | `api-key` | Security mode: `api-key`, `jwt`, or `none` |
| `JCLAW_API_KEY` | (auto-generated) | Custom API key for `api-key` mode |
| `AI_PROVIDER` | `anthropic` | LLM provider: `anthropic`, `openai`, `ollama`, or `google-genai` |
| `ANTHROPIC_API_KEY` | | Anthropic API key |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` | Anthropic model name |
| `OPENAI_API_KEY` | | OpenAI API key |
| `GEMINI_API_KEY` | | Google Gemini API key |
| `OLLAMA_ENABLED` | `false` | Enable Ollama local LLM |
| `GATEWAY_PORT` | `8080` | Gateway HTTP port |

See [docs/OPERATIONS.md](docs/OPERATIONS.md) for the full environment variable reference.

## Security

The gateway protects `/api/chat` and `/mcp/**` endpoints with API key authentication by default. On first run, a key is auto-generated at `~/.jclaw/api-key` and printed in the curl examples by the launcher scripts.

```bash
# Disable security for local development
JCLAW_SECURITY_MODE=none ./start.sh local

# Use a custom API key
JCLAW_API_KEY=my-custom-key ./start.sh local
```

The `onboard` wizard and `quickstart.sh --reconfigure` also allow configuring the security mode interactively. See [docs/OPERATIONS.md](docs/OPERATIONS.md) for full details.

## Shell Commands

| Command | Description |
|---------|-------------|
| `chat <message>` | Send a message to the agent |
| `new-session` | Start a fresh chat session |
| `sessions` | List active sessions |
| `session-history` | Show messages in the current session |
| `status` | Show system status |
| `config` | Show current configuration |
| `models` | Show configured LLM providers |
| `tools` | List available tools |
| `plugins` | List loaded plugins |
| `skills` | List loaded skills |
| `onboard` | Interactive setup wizard |

## Project Structure

```
jclaw-core              Pure Java domain model (no Spring dependency)
jclaw-channel-api       ChannelAdapter SPI, attachments, channel registry
jclaw-config            @ConfigurationProperties records
jclaw-tools             Tool registry + built-in tools + Spring AI bridge + Embabel bridge
jclaw-agent             Agent runtime, session management, prompt building
jclaw-skills            Skill loader + versioning + tenant-aware registry
jclaw-plugin-sdk        Plugin SPI, hooks, discovery
jclaw-memory            Memory search (in-memory + vector store)
jclaw-security          JWT auth, tenant resolution, SecurityContext
jclaw-documents         Document parsing (PDF, HTML, text) + chunking pipeline
jclaw-gateway           REST + WebSocket + webhook + MCP + observability (library)
jclaw-channel-telegram  Telegram adapter (polling + webhook + file attachments)
jclaw-channel-slack     Slack adapter (Socket Mode + Events API)
jclaw-channel-discord   Discord adapter (Gateway + Interactions)
jclaw-channel-email     Email adapter (IMAP polling + SMTP + MIME attachments)
jclaw-channel-sms       SMS/MMS adapter (Twilio REST API + webhook)
jclaw-media             Async media analysis SPI (vision/audio LLM pipeline)
jclaw-audit             Audit logging SPI + in-memory implementation
jclaw-spring-boot-starter  Auto-configuration for all modules
jclaw-gateway-app       Standalone gateway server (runnable)
jclaw-shell             Spring Shell CLI (runnable)
```

## Building from Source

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle

# Compile
./mvnw compile

# Run tests
./mvnw test

# Package as JARs
./mvnw package -DskipTests

# Install to local Maven repo
./mvnw install -DskipTests
```

## Docker Images

Two modules produce Docker images via [Eclipse JKube](https://eclipse.dev/jkube/):

```bash
# Build gateway image
./mvnw package k8s:build -pl jclaw-gateway-app -am -Pk8s -DskipTests

# Build shell image
./mvnw package k8s:build -pl jclaw-shell -am -Pk8s -DskipTests
```

Images use `eclipse-temurin:21-jre` base and follow `io.jclaw/<module>:<version>` naming.

## Documentation

- [Operations Guide](docs/OPERATIONS.md) — Running, configuring, deploying
- [Architecture](docs/ARCHITECTURE.md) — Module graph, message flow, k8s deployment

## License

Free for personal use and small organizations.

Commercial licensing required for SaaS, enterprise use,
or embedding in commercial products.

Contact: gregory.lawson@taptech.net

