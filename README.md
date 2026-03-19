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
```

To pre-pull the Ollama image in the background while the quickstart runs:

```bash
docker pull ollama/ollama:latest
```

Test with:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "hello"}'
```

### Option 2: start.sh (recommended for daily use)

After the initial build, use `start.sh` to run the gateway or interactive shell. It loads API keys from `docker-compose/.env` automatically.

```bash
# Edit API keys (one time)
vi docker-compose/.env

# Start the gateway (Docker) — tails logs automatically
./start.sh

# Start the interactive CLI shell (local Java)
./start.sh shell

# Start the interactive CLI shell (Docker, no Java needed)
./start.sh cli

# Start gateway locally without Docker
./start.sh local

# Force rebuild Docker images (after code changes)
./start.sh --force-build
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
./start.sh           # Docker (reads docker-compose/.env)
./start.sh local     # local Java (reads docker-compose/.env)
```

Or with environment variables directly:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-gateway-app
```

Test with curl:

```bash
# Chat
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
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
| `AI_PROVIDER` | `anthropic` | LLM provider: `anthropic`, `openai`, or `ollama` |
| `ANTHROPIC_API_KEY` | | Anthropic API key |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` | Anthropic model name |
| `OPENAI_API_KEY` | | OpenAI API key |
| `OLLAMA_ENABLED` | `false` | Enable Ollama local LLM |
| `GATEWAY_PORT` | `8080` | Gateway HTTP port |

See [docs/OPERATIONS.md](docs/OPERATIONS.md) for the full environment variable reference.

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

