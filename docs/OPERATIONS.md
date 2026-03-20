# JClaw Operations Guide

## Prerequisites

- Java 21 (Oracle or Temurin) — for local builds and the shell
- Docker — for `quickstart.sh` and `start.sh docker`
- At least one LLM provider API key (Anthropic, OpenAI, Google Gemini, or Ollama)

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
```

---

## start.sh — Daily Driver

`start.sh` is the recommended way to run JClaw after initial setup. It reads API keys and configuration from `$JCLAW_ENV_FILE` (default: `docker-compose/.env`). If `~/.jclawrc` exists (written by `quickstart.sh`), it is sourced automatically to set `JCLAW_ENV_FILE`.

```bash
./start.sh              # start gateway locally (default, requires Java 21)
./start.sh shell        # start interactive CLI shell (local Java)
./start.sh cli          # start interactive CLI shell (Docker, no Java needed)
./start.sh docker       # start gateway via Docker Compose
./start.sh local        # start gateway locally (same as default)
./start.sh stop         # stop Docker Compose stack
./start.sh logs         # tail gateway container logs
./start.sh --force-build          # rebuild from source, then start gateway locally
./start.sh --force-build docker   # rebuild Docker image, then start gateway
./start.sh --force-build cli      # rebuild shell Docker image, then start CLI
./start.sh help         # show all commands
```

### Configuration

Edit your `.env` file (location shown by `~/.jclawrc`, default: `docker-compose/.env`) to set your API keys and preferences:

```bash
AI_PROVIDER=anthropic
ANTHROPIC_ENABLED=true
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-4-5
OPENAI_ENABLED=false
OLLAMA_ENABLED=false
```

Both the gateway (Docker and local) and the shell read from this file. Environment variables set in your shell override `.env` values. Run `./quickstart.sh --reconfigure` to change the config location, re-enter API keys, or change the security mode.

### Gateway (Local — default)

```bash
./start.sh
```

Runs the gateway as a local Java process (no Docker). This is the default because code tools (file editing, search) operate on your local filesystem. Useful for development and debugging. The gateway serves:

- `POST /api/chat` — synchronous chat (requires `X-API-Key` header)
- `GET /api/health` — health check
- `GET /api/channels` — list registered channels
- `POST /webhook/{channel}` — inbound webhooks
- `WS /ws/session/{key}` — streaming WebSocket
- `/mcp/**` — MCP server hosting (requires `X-API-Key` header)

### Gateway (Docker)

```bash
./start.sh docker
```

Starts the Docker container, prints test commands (including your API key), then tails logs. Press Ctrl+C to detach (the container keeps running).

### Interactive Shell

```bash
./start.sh shell        # local Java (requires Java 21)
./start.sh cli          # Docker (no Java needed)
```

Starts the Spring Shell CLI. Chat commands:

```
jclaw> chat hello                  # send a message to the agent
jclaw> chat what time is it?       # multi-word messages work naturally
jclaw> new-session                 # start a fresh conversation
jclaw> sessions                    # list all sessions
jclaw> session-history             # show messages in current session
```

Other commands:

```
jclaw> status                      # system status (identity, tools, sessions)
jclaw> config                      # current configuration
jclaw> models                      # configured LLM providers
jclaw> tools                       # available tools
jclaw> skills                      # loaded skills
jclaw> plugins                     # loaded plugins
jclaw> onboard                     # interactive setup wizard
jclaw> help                        # all available commands
```

---

## quickstart.sh — First-Time Docker Setup

For a fresh clone with no Java installed:

```bash
git clone https://github.com/jclaw/jclaw.git && cd jclaw
ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh
```

This detects Java, builds the Docker image via Maven + JKube, starts Docker Compose, and optionally pulls Ollama if no API key is set.

On first run (no existing `.env`), quickstart prompts where to save configuration:
- `~/.jclaw/.env` — recommended, persists across project clones
- `docker-compose/.env` — project-local (original behavior)

The chosen location is written to `~/.jclawrc` and respected by all scripts.

To force a rebuild of the Docker image (e.g. after code changes):

```bash
./quickstart.sh --force-build
```

To re-run the full interactive setup (change provider, API keys, channels, config location):

```bash
./quickstart.sh --reconfigure
```

After quickstart completes, use `./start.sh` for subsequent runs.

---

## setup.sh — First-Time Developer Setup

For developers who want to build from source:

```bash
./setup.sh              # build + launch shell
./setup.sh --gateway    # build + launch gateway
./setup.sh --build-only # build only
```

Installs Java 21 via SDKMAN if needed, builds all modules, and launches the chosen target.

### Onboarding Wizard

The shell includes an interactive wizard that walks through LLM provider selection, API key entry, security mode, and channel configuration:

```bash
./start.sh shell
jclaw> onboard
```

The wizard covers:
- LLM provider + API key + model
- **Security mode** (API key / JWT / none) and optional custom API key
- Gateway settings (port, bind address, assistant name — manual mode)
- Channel setup (Telegram, Slack, Discord)
- Skills and MCP server connections

The wizard writes `application-local.yml` + `.env` to `~/.jclaw/` (or current directory). After the wizard finishes, restart the shell to activate.

---

## Running Locally (Manual Configuration)

If you prefer to pass environment variables directly instead of using `docker-compose/.env`:

### Shell

```bash
# With Anthropic (default)
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-shell

# With OpenAI
AI_PROVIDER=openai OPENAI_ENABLED=true OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-shell

# With Ollama (free, local)
AI_PROVIDER=ollama OLLAMA_ENABLED=true ./mvnw spring-boot:run -pl jclaw-shell

# With Google Gemini
AI_PROVIDER=google-genai GEMINI_ENABLED=true GEMINI_API_KEY=... ./mvnw spring-boot:run -pl jclaw-shell
```

### Gateway

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-gateway-app
```

Test (the API key is auto-generated at `~/.jclaw/api-key` on first run — see [Security](#security) below):
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "hello"}'

curl http://localhost:8080/api/health

curl http://localhost:8080/api/channels
```

### Option 3: Telegram (local dev — no public endpoint needed)

The Telegram adapter supports **long polling mode** for local development. It calls Telegram's `getUpdates` API in a loop — no webhook, no public URL, no ngrok required.

**Setup:**

1. Create a bot via [@BotFather](https://t.me/BotFather) on Telegram
2. Copy the bot token
3. Run with the token (leave `TELEGRAM_WEBHOOK_URL` unset for polling mode):

```bash
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

4. Open Telegram, find your bot, send it a message
5. The bot replies via the agent runtime

**How it works:**
- `webhookUrl` is blank → adapter automatically uses polling mode
- Calls `getUpdates` with long-polling (30s timeout by default)
- Deletes any existing webhook on startup (Telegram only allows one mode at a time)
- Outbound responses use `sendMessage` API (same in both modes)

**Switching to webhook mode (production):**
```bash
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
TELEGRAM_WEBHOOK_URL=https://jclaw.taptech.net/webhook/telegram \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

### Option 4: Slack (local dev — no public endpoint needed)

The Slack adapter supports **Socket Mode** for local development. It connects to Slack via WebSocket — no webhook, no public URL, no ngrok required.

**Setup:**

1. Create a Slack app at [api.slack.com/apps](https://api.slack.com/apps)
2. Enable Socket Mode in app settings, copy the **App-Level Token** (starts with `xapp-`)
3. Install the app to a workspace, copy the **Bot Token** (starts with `xoxb-`)
4. Run with both tokens:

```bash
SLACK_BOT_TOKEN=xoxb-... \
SLACK_APP_TOKEN=xapp-... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

5. Invite the bot to a channel, send it a message

**How it works:**
- `appToken` is set → adapter uses Socket Mode (WebSocket connection to Slack)
- Calls `apps.connections.open` to get a `wss://` URL, then connects
- Receives events as JSON envelopes over WebSocket, ACKs each envelope
- Auto-reconnects on disconnect
- Outbound responses use `chat.postMessage` API (same in both modes)

**Switching to Events API webhook mode (production):**
```bash
SLACK_BOT_TOKEN=xoxb-... \
SLACK_SIGNING_SECRET=... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```
Leave `SLACK_APP_TOKEN` unset. Slack will POST events to `POST /webhook/slack`. The adapter handles `url_verification` challenges automatically.

### Option 5: Discord (local dev — no public endpoint needed)

The Discord adapter supports **Gateway WebSocket** mode for local development. It connects to Discord's Gateway — no webhook, no public URL, no ngrok required.

**Setup:**

1. Create a Discord application at [discord.com/developers](https://discord.com/developers/applications)
2. Create a bot, copy the **Bot Token**
3. Enable **Message Content Intent** in the Bot settings
4. Invite the bot to a server using OAuth2 URL generator (scopes: `bot`, permissions: `Send Messages`)
5. Run with the token and gateway flag:

```bash
DISCORD_BOT_TOKEN=... \
DISCORD_USE_GATEWAY=true \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

6. Send a message in a channel where the bot has access

**How it works:**
- `DISCORD_USE_GATEWAY=true` → adapter connects to Discord Gateway WebSocket
- Fetches gateway URL via `GET /gateway/bot`, connects with `?v=10&encoding=json`
- Handles HELLO, heartbeat, IDENTIFY, and READY handshake
- Listens for `MESSAGE_CREATE` events (ignores bot messages)
- Auto-reconnects on disconnect
- Outbound responses use REST API `channels/{id}/messages` (same in both modes)

**Switching to Interactions webhook mode (production):**
```bash
DISCORD_BOT_TOKEN=... \
DISCORD_APPLICATION_ID=... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```
Leave `DISCORD_USE_GATEWAY` unset. Discord will POST interactions to `POST /webhook/discord`. The adapter handles `PING` verification automatically.

### Option 6: Email (IMAP polling — works anywhere)

The Email adapter polls an IMAP mailbox for new messages and replies via SMTP. Supports Gmail, Outlook, or any IMAP/SMTP provider.

**Setup:**

1. Enable IMAP access on your email account
2. For Gmail: generate an [App Password](https://myaccount.google.com/apppasswords) (2FA must be enabled)
3. Run with email credentials:

```bash
EMAIL_IMAP_HOST=imap.gmail.com \
EMAIL_SMTP_HOST=smtp.gmail.com \
EMAIL_USERNAME=you@gmail.com \
EMAIL_PASSWORD=your-app-password \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

4. Send an email to the configured address — the agent replies to the sender

**How it works:**
- Polls IMAP folders (default: INBOX) at a configurable interval (default: 60s)
- Marks processed messages as SEEN to avoid re-processing
- Parses MIME multipart messages — extracts text content and file attachments
- Replies via SMTP with STARTTLS

**Configuration options:**
- `EMAIL_PROVIDER` — `imap` (default), `gmail`, `outlook`
- `EMAIL_IMAP_PORT` — default 993
- `EMAIL_SMTP_PORT` — default 587
- `EMAIL_POLL_INTERVAL` — polling interval in seconds (default 60)

### Option 7: SMS (Twilio — webhook-based)

The SMS adapter uses Twilio's Messages API for outbound and receives inbound messages via Twilio webhooks.

**Setup:**

1. Create a Twilio account at [twilio.com](https://www.twilio.com)
2. Get a phone number, copy Account SID, Auth Token, and phone number
3. Run with Twilio credentials:

```bash
TWILIO_ACCOUNT_SID=AC... \
TWILIO_AUTH_TOKEN=... \
TWILIO_FROM_NUMBER=+15551234567 \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-gateway-app
```

4. Configure your Twilio phone number's webhook URL to point to `POST /webhooks/sms` on your gateway
5. Send an SMS to the Twilio number — the agent replies

**How it works:**
- Inbound: Twilio POSTs form-encoded webhook to `/webhooks/sms` with `From`, `Body`, `MessageSid`
- MMS attachments: `NumMedia`, `MediaUrl0`, `MediaContentType0` fields parsed into attachments
- Outbound: POST to Twilio Messages API with Basic auth (Account SID + Auth Token)

**For local development**, use ngrok to expose the webhook:
```bash
ngrok http 8080
# Then set Twilio webhook URL to: https://your-ngrok.ngrok.io/webhooks/sms
```

---

## MCP Server Hosting

JClaw can host MCP (Model Context Protocol) tool servers, making JClaw's tools available to external AI clients.

**Endpoints** (all require `X-API-Key` header in `api-key` security mode):
```bash
# List available MCP servers
curl http://localhost:8080/mcp \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)"

# List tools for a server
curl http://localhost:8080/mcp/{serverName}/tools \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)"

# Execute a tool
curl -X POST http://localhost:8080/mcp/{serverName}/tools/{toolName} \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"arg1": "value1"}'
```

MCP tool providers are registered via the `McpToolProvider` SPI. Any Spring bean implementing `McpToolProvider` is automatically discovered and registered.

---

## Shell Commands Reference

Once the shell is running, the following commands are available:

| Command | Description |
|---------|-------------|
| `onboard` | Interactive setup wizard — configures LLM, channels, writes config files |
| `chat <message>` | Send a message to the agent |
| `new-session` | Start a fresh chat session |
| `sessions` | List all active sessions |
| `session-history` | Show messages in the current (or specified) session |
| `status` | Show system status (identity, tools, plugins, sessions) |
| `config` | Show current JClaw configuration |
| `models` | Show configured LLM providers |
| `tools` | List available tools |
| `plugins` | List loaded plugins |
| `skills` | List loaded skills |

---

## LLM Provider Configuration

The default provider is **Anthropic** (`claude-sonnet-4-5`). Set `AI_PROVIDER` to switch the primary provider.

### Anthropic (default)

```bash
ANTHROPIC_API_KEY=sk-ant-... ./start.sh shell
```

Override the model:
```bash
ANTHROPIC_MODEL=claude-opus-4-5 ANTHROPIC_API_KEY=sk-ant-... ./start.sh shell
```

### OpenAI

```bash
AI_PROVIDER=openai OPENAI_ENABLED=true OPENAI_API_KEY=sk-... ./start.sh shell
```

### Google Gemini

```bash
AI_PROVIDER=google-genai GEMINI_ENABLED=true GEMINI_API_KEY=... ./start.sh shell
```

Override the model (default: `gemini-2.0-flash`):
```bash
GEMINI_MODEL=gemini-2.0-flash-lite GEMINI_ENABLED=true GEMINI_API_KEY=... ./start.sh shell
```

Get an API key from [Google AI Studio](https://aistudio.google.com/apikey).

### Ollama (local, free)

**Native install:**

```bash
brew install ollama && ollama serve
ollama pull llama3.2

AI_PROVIDER=ollama OLLAMA_ENABLED=true ./start.sh shell
```

**Docker (used by quickstart.sh):**

```bash
docker pull ollama/ollama:latest
docker compose --profile ollama up -d
docker compose --profile ollama exec ollama ollama pull llama3.2
```

> **Note:** The quickstart script starts Ollama automatically when no `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` is set. To skip Ollama and use a cloud provider: `ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh`

### Multiple Providers

All four can be configured simultaneously in `docker-compose/.env`:

```bash
AI_PROVIDER=anthropic
ANTHROPIC_ENABLED=true
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-...
GEMINI_ENABLED=true
GEMINI_API_KEY=...
OLLAMA_ENABLED=true
```

`AI_PROVIDER` selects which one Spring AI uses as the primary `ChatClient`.

---

## Security

The gateway protects `/api/chat` and `/mcp/**` endpoints with API key authentication by default. The security mode is controlled by `JCLAW_SECURITY_MODE`.

### Security Modes

| Mode | Description |
|------|-------------|
| `api-key` (default) | Requests must include `X-API-Key` header with a valid key |
| `jwt` | Requests must include a valid JWT `Authorization: Bearer <token>` header |
| `none` | No authentication — **development only** |

### API Key Resolution

When `JCLAW_SECURITY_MODE=api-key` (the default), the API key is resolved in this order:

1. `JCLAW_API_KEY` environment variable
2. Key file at `JCLAW_API_KEY_FILE` (default: `~/.jclaw/api-key`)
3. Auto-generate a key and write it to `~/.jclaw/api-key`

The launcher scripts (`start.sh`, `quickstart.sh`, `setup.sh`) resolve the API key before the JVM starts and print it in curl examples. The JVM's `ApiKeyProvider` follows the same resolution order, so both see the same key.

```bash
# View your current API key
cat ~/.jclaw/api-key

# Use a custom key
JCLAW_API_KEY=my-custom-key ./start.sh local

# Disable security (development only)
JCLAW_SECURITY_MODE=none ./start.sh local
```

### Configuring via the Onboard Wizard

The `onboard` command in the shell includes a security step:
- **Quickstart mode**: Defaults to `api-key` with auto-generation (no prompt)
- **Manual mode**: Prompts for security mode and optional custom API key

The `--reconfigure` flag in `quickstart.sh` also includes a security step.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `JAVA_HOME` | Yes | Path to Java 21 JDK |
| `JCLAW_HOME` | No | Config directory override (default: `~/.jclaw/`) |
| `JCLAW_ENV_FILE` | No | Path to `.env` file (default: `docker-compose/.env`). Auto-set by `~/.jclawrc`. |
| `JCLAW_SECURITY_MODE` | No | Security mode: `api-key` (default), `jwt`, or `none` |
| `JCLAW_API_KEY` | No | Custom API key (auto-generated if not set) |
| `JCLAW_API_KEY_FILE` | No | Path to API key file (default: `~/.jclaw/api-key`) |
| `AI_PROVIDER` | No | Primary LLM provider: `anthropic` (default), `openai`, `google-genai`, or `ollama` |
| `ANTHROPIC_API_KEY` | One of these | Anthropic API key |
| `ANTHROPIC_ENABLED` | No | Enable Anthropic provider (default: `true`) |
| `ANTHROPIC_MODEL` | No | Anthropic model name (default: `claude-sonnet-4-5`) |
| `OPENAI_API_KEY` | One of these | OpenAI API key |
| `OPENAI_ENABLED` | No | Enable OpenAI provider (default: `false`) |
| `GEMINI_API_KEY` | One of these | Google Gemini API key |
| `GEMINI_ENABLED` | No | Enable Google Gemini provider (default: `false`) |
| `GEMINI_MODEL` | No | Gemini model name (default: `gemini-2.0-flash`) |
| `OLLAMA_ENABLED` | No | Enable Ollama provider (default: `false`) |
| `OLLAMA_BASE_URL` | No | Ollama API URL (default: `http://localhost:11434`) |
| `GATEWAY_PORT` | No | Gateway HTTP port (default: `8080`) |
| `TELEGRAM_BOT_TOKEN` | For Telegram | Telegram bot token from @BotFather |
| `TELEGRAM_WEBHOOK_URL` | No (polling if blank) | Public webhook URL for production |
| `SLACK_BOT_TOKEN` | For Slack | Slack bot OAuth token |
| `SLACK_APP_TOKEN` | No (Socket Mode) | App-level token (xapp-...) for Socket Mode |
| `SLACK_SIGNING_SECRET` | For Slack webhook | Slack app signing secret (webhook mode only) |
| `DISCORD_BOT_TOKEN` | For Discord | Discord bot token |
| `DISCORD_APPLICATION_ID` | For Discord webhook | Discord application ID (webhook mode only) |
| `DISCORD_USE_GATEWAY` | No | Set to `true` for Gateway WebSocket mode |
| `EMAIL_IMAP_HOST` | For Email | IMAP server hostname |
| `EMAIL_SMTP_HOST` | For Email | SMTP server hostname |
| `EMAIL_USERNAME` | For Email | Email account username |
| `EMAIL_PASSWORD` | For Email | Email account password or app password |
| `EMAIL_PROVIDER` | No | Email provider: `imap` (default), `gmail`, `outlook` |
| `EMAIL_IMAP_PORT` | No | IMAP port (default: 993) |
| `EMAIL_SMTP_PORT` | No | SMTP port (default: 587) |
| `EMAIL_POLL_INTERVAL` | No | Polling interval in seconds (default: 60) |
| `TWILIO_ACCOUNT_SID` | For SMS | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | For SMS | Twilio Auth Token |
| `TWILIO_FROM_NUMBER` | For SMS | Twilio phone number for outbound messages |

---

## Channel Comparison for Local Dev

| Channel | Public endpoint needed? | Setup effort | Best for |
|---------|------------------------|--------------|----------|
| Spring Shell | No | None | Quick testing, scripting |
| REST API (`/api/chat`) | No | None | API integration testing |
| Telegram (polling) | **No** | Create bot via BotFather (~2 min) | Mobile testing, real chat UX |
| Telegram (webhook) | Yes | Bot + ngrok/public URL | Production |
| Slack (Socket Mode) | **No** | Create app + enable Socket Mode (~5 min) | Team testing, real Slack UX |
| Slack (Events API) | Yes | Slack app + ngrok/public URL | Production |
| Discord (Gateway) | **No** | Create app + bot + Message Content Intent (~5 min) | Community testing |
| Discord (webhook) | Yes | Discord app + interactions endpoint | Production |
| Email (IMAP) | **No** | Enable IMAP + app password (~3 min) | Async communication, document intake |
| SMS (Twilio) | Yes | Twilio account + ngrok for webhook | Mobile outreach, 2-way SMS |
| WebSocket | No | Connect to `ws://localhost:8080/ws/session/{key}` | Real-time/streaming |

**Recommendation for local dev**: Start with REST API for quick validation, then Telegram polling or Slack Socket Mode for real chat UX testing. All three work without any public endpoints.

---

## Running Multiple Instances

Each JClaw instance stores configuration in a **config directory** (default: `~/.jclaw/`). To run multiple independent instances — for example, separate personal/work assistants, or a dev/test instance alongside production — point each to a different config directory using the `JCLAW_HOME` environment variable.

### Shell Instances

```bash
# Instance 1: personal assistant
JCLAW_HOME=~/.jclaw-personal ./mvnw spring-boot:run -pl jclaw-shell
# Run onboard wizard → configures OpenAI + Telegram in ~/.jclaw-personal/

# Instance 2: work assistant
JCLAW_HOME=~/.jclaw-work ./mvnw spring-boot:run -pl jclaw-shell
# Run onboard wizard → configures Anthropic + Slack in ~/.jclaw-work/

# Instance 3: dev/test (free, local LLM)
JCLAW_HOME=~/.jclaw-dev ./mvnw spring-boot:run -pl jclaw-shell
# Run onboard wizard → configures Ollama in ~/.jclaw-dev/
```

Each config directory contains its own `application-local.yml` and `.env`, so LLM providers, channel connections, and all settings are fully isolated.

After the wizard writes config, source the correct `.env` for each instance:

```bash
source ~/.jclaw-personal/.env && JCLAW_HOME=~/.jclaw-personal ./mvnw spring-boot:run -pl jclaw-shell
```

### Gateway Instances

For multiple gateway instances, also assign different ports:

```bash
# Production gateway on port 8080
source ~/.jclaw-prod/.env
JCLAW_HOME=~/.jclaw-prod SERVER_PORT=8080 ./mvnw spring-boot:run -pl jclaw-gateway-app

# Staging gateway on port 8081
source ~/.jclaw-staging/.env
JCLAW_HOME=~/.jclaw-staging SERVER_PORT=8081 ./mvnw spring-boot:run -pl jclaw-gateway-app
```

### How It Works

- `JCLAW_HOME` overrides the default `~/.jclaw/` config directory
- The shell auto-imports `${JCLAW_HOME}/application-local.yml` via `spring.config.import`
- Each instance's `.env` file contains `export` statements for its API keys and tokens
- Sessions are in-memory and isolated per JVM process
- Each instance can use different LLM providers, models, and channel connections

---

## Building

```bash
# Compile everything
./mvnw compile

# Run all tests
./mvnw test -o

# Run tests for one module
./mvnw test -pl jclaw-channel-telegram -o

# Install to local Maven repo (needed for offline single-module builds)
./mvnw install -DskipTests

# Package as JARs
./mvnw package -DskipTests
```

**Offline mode**: Use `-o` flag after initial dependency download. The Nexus repo at tooling.taptech.net:8081 causes timeouts when unreachable. When adding new external dependencies, run once without `-o`.

---

## Production Deployment (k8s)

See `ARCHITECTURE.md` for the full k8s deployment view.

### Docker Image Build (JKube)

Two modules produce Docker images: `jclaw-gateway-app` (production server) and `jclaw-shell` (CLI).

```bash
# Build gateway image (includes all dependencies with -am)
./mvnw package k8s:build -pl jclaw-gateway-app -am -Pk8s -DskipTests

# Build shell image
./mvnw package k8s:build -pl jclaw-shell -am -Pk8s -DskipTests

# Build both at once
./mvnw package k8s:build -pl jclaw-gateway-app,jclaw-shell -am -Pk8s -DskipTests

# Push to registry
./mvnw k8s:push -pl jclaw-gateway-app -Pk8s

# Deploy
./mvnw k8s:resource k8s:apply -pl jclaw-gateway-app -Pk8s
```

Images use `eclipse-temurin:21-jre` as the base. The image name follows `io.jclaw/<module>:<version>` convention.

The shell Docker image can be run interactively via Docker Compose:
```bash
./start.sh cli
# or directly:
docker compose -f docker-compose/docker-compose.yml --profile cli run --rm cli
```

### Required Secrets

```bash
kubectl create secret generic jclaw-secrets \
  --from-literal=JCLAW_API_KEY=jclaw_ak_... \
  --from-literal=OPENAI_API_KEY=sk-... \
  --from-literal=ANTHROPIC_API_KEY=sk-ant-... \
  --from-literal=GEMINI_API_KEY=... \
  --from-literal=TELEGRAM_BOT_TOKEN=123456:ABC... \
  --from-literal=SLACK_BOT_TOKEN=xoxb-... \
  --from-literal=SLACK_SIGNING_SECRET=... \
  --from-literal=DISCORD_BOT_TOKEN=... \
  --from-literal=EMAIL_USERNAME=bot@example.com \
  --from-literal=EMAIL_PASSWORD=... \
  --from-literal=TWILIO_ACCOUNT_SID=AC... \
  --from-literal=TWILIO_AUTH_TOKEN=... \
  --from-literal=TWILIO_FROM_NUMBER=+15551234567
```

### Production Telegram Config

For production, set the webhook URL so Telegram pushes updates directly instead of polling:

```bash
TELEGRAM_WEBHOOK_URL=https://jclaw.taptech.net/webhook/telegram
```

The adapter will call `setWebhook` on startup to register with Telegram.

---

## Troubleshooting

### Nexus timeouts during build
```bash
# Use offline mode after initial download
./mvnw compile -o
```

### Telegram bot not responding
- Verify bot token: `curl https://api.telegram.org/bot<TOKEN>/getMe`
- Check if webhook is set when using polling: `curl https://api.telegram.org/bot<TOKEN>/getWebhookInfo`
- Delete webhook to switch to polling: `curl https://api.telegram.org/bot<TOKEN>/deleteWebhook`

### No LLM configured
If no ChatClient.Builder bean is available (no API keys set, no Ollama running), `AgentRuntime` won't be created. The `chat` command will return: "No LLM configured. Set ANTHROPIC_API_KEY, OPENAI_API_KEY, GEMINI_API_KEY, or enable Ollama."

### Port conflicts
Default ports:
- Shell: no port (interactive terminal)
- Gateway: 8080
- Ollama: 11434
