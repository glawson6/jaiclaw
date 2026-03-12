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

This builds the Docker image, starts the gateway + Ollama, and pulls a local LLM model. No Java installation needed — Docker handles everything. Test with:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "hello"}'
```

### Option 2: From Source (for developers)

```bash
git clone https://github.com/jclaw/jclaw.git
cd jclaw
./setup.sh
```

The setup script installs Java 21 via SDKMAN (if needed), builds all modules, and launches the interactive shell. Run the onboarding wizard to configure your LLM provider:

```bash
jclaw> onboard
```

Or skip the wizard and pass API keys directly:

```bash
# Ollama (free, local — install with: brew install ollama && ollama serve && ollama pull llama3.2)
./mvnw spring-boot:run -pl jclaw-shell

# OpenAI
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-shell

# Anthropic
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-shell
```

### Option 3: JBang (coming soon)

Once JClaw is published to Maven Central:

```bash
# Install JBang: curl -Ls https://sh.jbang.dev | bash
jbang shell@jclaw     # interactive shell
jbang gateway@jclaw   # gateway server
```

JBang auto-downloads Java 21 — zero prerequisites besides JBang itself.

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
| Email    | IMAP polling        | ~3 min     |
| SMS      | Twilio webhook      | ~5 min     |

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

# Email (IMAP polling + SMTP outbound)
EMAIL_IMAP_HOST=imap.gmail.com \
EMAIL_SMTP_HOST=smtp.gmail.com \
EMAIL_USERNAME=you@gmail.com \
EMAIL_PASSWORD=app-password \
OPENAI_API_KEY=sk-... \
./mvnw spring-boot:run -pl jclaw-gateway-app

# SMS (Twilio)
TWILIO_ACCOUNT_SID=AC... \
TWILIO_AUTH_TOKEN=... \
TWILIO_FROM_NUMBER=+15551234567 \
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

## Documentation

- [Operations Guide](docs/OPERATIONS.md) — Running, configuring, deploying
- [Architecture](docs/ARCHITECTURE.md) — Module graph, message flow, k8s deployment

## License

Free for personal use and small organizations.

Commercial licensing required for SaaS, enterprise use,
or embedding in commercial products.

Contact: gregory.lawson@taptech.net

