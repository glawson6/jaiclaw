# JClaw

Java/Spring AI personal assistant framework. Embeddable agent runtime with tool execution, skills, plugins, memory, and multi-channel messaging (Telegram, Slack, Discord).

Built on Java 21, Spring Boot 3.5, Spring AI 1.1, and Spring Shell 3.4.

## Prerequisites

- **Java 21** (Oracle or Eclipse Temurin)
- **Git**
- At least one LLM provider: [Ollama](https://ollama.com) (free, local), OpenAI, or Anthropic

## Quick Start

```bash
# 1. Clone
git clone https://github.com/jclaw/jclaw.git
cd jclaw

# 2. Set JAVA_HOME
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle  # adjust to your JDK path

# 3. Build
./mvnw install -DskipTests

# 4. Run the shell
./mvnw spring-boot:run -pl jclaw-shell

# 5. Run the setup wizard (inside the shell)
jclaw> onboard
```

The `onboard` wizard walks you through LLM provider selection, API key entry, channel configuration, and writes all config files. After it finishes:

```bash
# Source your secrets
source ~/.jclaw/.env

# Restart to pick up config
./mvnw spring-boot:run -pl jclaw-shell

# Verify
jclaw> status
jclaw> chat hello
```

### Without the Wizard

If you prefer manual setup, pass API keys directly:

```bash
# Ollama (free, local — install with: brew install ollama && ollama serve && ollama pull llama3.2)
./mvnw spring-boot:run -pl jclaw-shell

# OpenAI
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-shell

# Anthropic
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-shell
```

## Running the Gateway (REST API + Channels)

For HTTP/WebSocket access or to connect messaging channels:

```bash
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-gateway-app
```

Test with curl:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "hello"}'
```

### Connecting Channels

All messaging channels support a **local dev mode** that requires no public endpoint:

| Channel  | Local Dev Mode       | Setup Time |
|----------|---------------------|------------|
| Telegram | Long polling        | ~2 min     |
| Slack    | Socket Mode         | ~5 min     |
| Discord  | Gateway WebSocket   | ~5 min     |

```bash
# Telegram (polling mode — no webhook needed)
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
OPENAI_API_KEY=sk-... \
./mvnw spring-boot:run -pl jclaw-gateway-app

# Slack (Socket Mode — no webhook needed)
SLACK_BOT_TOKEN=xoxb-... \
SLACK_APP_TOKEN=xapp-... \
OPENAI_API_KEY=sk-... \
./mvnw spring-boot:run -pl jclaw-gateway-app

# Discord (Gateway mode — no webhook needed)
DISCORD_BOT_TOKEN=... \
DISCORD_USE_GATEWAY=true \
OPENAI_API_KEY=sk-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

See [docs/OPERATIONS.md](docs/OPERATIONS.md) for full channel setup instructions including production webhook configuration.

## Running Multiple Instances

Each JClaw instance stores its config in a **config directory** (default: `~/.jclaw/`). To run multiple independent instances, point each one to a different config directory using the `JCLAW_HOME` environment variable:

```bash
# Instance 1: personal assistant with OpenAI
JCLAW_HOME=~/.jclaw-personal ./mvnw spring-boot:run -pl jclaw-shell
# In shell: onboard → configure OpenAI + Telegram

# Instance 2: work assistant with Anthropic
JCLAW_HOME=~/.jclaw-work ./mvnw spring-boot:run -pl jclaw-shell
# In shell: onboard → configure Anthropic + Slack

# Instance 3: dev/test instance with Ollama
JCLAW_HOME=~/.jclaw-dev ./mvnw spring-boot:run -pl jclaw-shell
# In shell: onboard → configure Ollama
```

Each instance gets its own `application-local.yml` and `.env` file in its config directory. Sessions, LLM providers, and channel connections are fully isolated.

For gateway instances, also set different ports:

```bash
# Gateway instance 1 on port 8080
JCLAW_HOME=~/.jclaw-prod SERVER_PORT=8080 ./mvnw spring-boot:run -pl jclaw-gateway-app

# Gateway instance 2 on port 8081
JCLAW_HOME=~/.jclaw-staging SERVER_PORT=8081 ./mvnw spring-boot:run -pl jclaw-gateway-app
```

## Shell Commands

| Command | Description |
|---------|-------------|
| `onboard` | Interactive setup wizard |
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

## Project Structure

```
jclaw-core              Pure Java domain model (no Spring dependency)
jclaw-channel-api       ChannelAdapter SPI for messaging platforms
jclaw-config            @ConfigurationProperties records
jclaw-tools             Tool registry + 5 built-in tools + Spring AI bridge
jclaw-agent             Agent runtime, session management, prompt building
jclaw-skills            Skill loader + 5 bundled skills
jclaw-plugin-sdk        Plugin SPI, hooks, discovery
jclaw-memory            Memory search (in-memory + vector store)
jclaw-gateway           REST + WebSocket + webhook gateway (library)
jclaw-channel-telegram  Telegram adapter (polling + webhook)
jclaw-channel-slack     Slack adapter (Socket Mode + Events API)
jclaw-channel-discord   Discord adapter (Gateway + Interactions)
jclaw-spring-boot-starter  Auto-configuration
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

## Documentation

- [Operations Guide](docs/OPERATIONS.md) — Running, configuring, deploying
- [Architecture](docs/ARCHITECTURE.md) — Module graph, message flow, k8s deployment

## License

Free for personal use and small organizations.

Commercial licensing required for SaaS, enterprise use,
or embedding in commercial products.

Contact: your-email@example.com

