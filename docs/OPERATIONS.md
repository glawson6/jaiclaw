# JClaw Operations Guide

## Prerequisites

- Java 21 (Oracle or Temurin)
- Maven (via `./mvnw` wrapper)
- At least one LLM provider configured

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
```

---

## Quick Start with the Setup Wizard

The fastest way to get JClaw running is the interactive **onboarding wizard**. It walks you through LLM provider selection, API key entry, optional channel configuration, and writes all config files for you.

```bash
# 1. Build
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw install -DskipTests

# 2. Launch the shell
./mvnw spring-boot:run -pl jclaw-shell

# 3. Run the wizard
jclaw> onboard
```

The wizard will prompt you to:

1. **Acknowledge security** — API keys are stored in a local `.env` file (never commit it)
2. **Choose a setup mode** — QuickStart (LLM + optional Telegram) or Manual (full control)
3. **Handle existing config** — Keep, modify, or reset if `~/.jclaw/application-local.yml` exists
4. **Select an LLM provider** — OpenAI, Anthropic, or Ollama (local, no key needed)
5. **Enter API key** — masked input (skipped for Ollama)
6. **Pick a model** — provider-specific list (e.g., `gpt-4o`, `claude-sonnet-4-6`, `llama3`)
7. **Test connectivity** — optional ping to verify the key works
8. **Configure channels** — Telegram (QuickStart + Manual), Slack & Discord (Manual only)
9. **Choose config location** — `~/.jclaw/` (default, persists) or current working directory
10. **Review and write** — preview settings, confirm, write `application-local.yml` + `.env`

After the wizard finishes:

```bash
# Source your secrets
source ~/.jclaw/.env

# Restart JClaw to pick up the new config
./mvnw spring-boot:run -pl jclaw-shell

# Verify
jclaw> status
jclaw> models
jclaw> config
```

### Generated Files

| File | Contents | Location |
|------|----------|----------|
| `application-local.yml` | JClaw + Spring AI config (references env vars for secrets) | `~/.jclaw/` or cwd |
| `.env` | `export` statements for API keys and bot tokens | Same directory |

The shell auto-imports `~/.jclaw/application-local.yml` via `spring.config.import` — no extra CLI flags needed. Secrets stay in `.env` and are referenced as `${ENV_VAR}` placeholders in the YAML.

### Re-running the Wizard

Run `onboard` again at any time. If existing config is detected, you'll be offered three choices:
- **Keep** — exit the wizard, use what you have
- **Modify** — walk through setup again, overwrite with new values
- **Reset** — start from scratch

---

## Running Locally (Manual Configuration)

If you prefer to configure manually instead of using the wizard, set environment variables directly.

### Option 1: Spring Shell (simplest)

No external services needed beyond an LLM provider.

```bash
# With Ollama (free, local)
./mvnw spring-boot:run -pl jclaw-shell

# With OpenAI
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-shell

# With Anthropic
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-shell
```

Once running:
```
jclaw> chat hello
jclaw> new-session
```

### Option 2: REST API (gateway)

Run the gateway for HTTP/WebSocket access:

```bash
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-gateway-app
```

Test with curl:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "hello"}'
```

Check health:
```bash
curl http://localhost:8080/api/health
```

List registered channels:
```bash
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
OPENAI_API_KEY=sk-... \
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
OPENAI_API_KEY=sk-... \
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
OPENAI_API_KEY=sk-... \
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
OPENAI_API_KEY=sk-... \
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
OPENAI_API_KEY=sk-... \
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
OPENAI_API_KEY=sk-... \
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
OPENAI_API_KEY=sk-... \
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

**Endpoints:**
```bash
# List available MCP servers
curl http://localhost:8080/mcp

# List tools for a server
curl http://localhost:8080/mcp/{serverName}/tools

# Execute a tool
curl -X POST http://localhost:8080/mcp/{serverName}/tools/{toolName} \
  -H "Content-Type: application/json" \
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

### Ollama (local, free)

```bash
# Install Ollama
brew install ollama

# Start server
ollama serve

# Pull a model
ollama pull llama3.2

# JClaw connects automatically to http://localhost:11434
./mvnw spring-boot:run -pl jclaw-shell
```

### OpenAI

```bash
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jclaw-shell
```

### Anthropic

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jclaw-shell
```

### Multiple Providers

All three can be configured simultaneously. Set all API keys and Spring AI will auto-configure each:

```bash
OPENAI_API_KEY=sk-... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jclaw-shell
```

Ollama is always available if running locally on port 11434.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `JAVA_HOME` | Yes | Path to Java 21 JDK |
| `JCLAW_HOME` | No | Config directory override (default: `~/.jclaw/`) |
| `OPENAI_API_KEY` | One of these | OpenAI API key |
| `ANTHROPIC_API_KEY` | One of these | Anthropic API key |
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

**Offline mode**: Use `-o` flag after initial dependency download. The Nexus repo at 10.92.7.128:8081 causes timeouts when unreachable. When adding new external dependencies, run once without `-o`.

---

## Production Deployment (k8s)

See `ARCHITECTURE.md` for the full k8s deployment view.

### Docker Image Build (JKube)

Two modules produce Docker images: `jclaw-gateway-app` (production server) and `jclaw-shell` (CLI).

```bash
# Build gateway image
./mvnw package k8s:build -pl jclaw-gateway-app -Pk8s -DskipTests

# Build shell image
./mvnw package k8s:build -pl jclaw-shell -Pk8s -DskipTests

# Push to registry
./mvnw k8s:push -pl jclaw-gateway-app -Pk8s

# Deploy
./mvnw k8s:resource k8s:apply -pl jclaw-gateway-app -Pk8s
```

Images use `eclipse-temurin:21-jre` as the base. The image name follows `io.jclaw/<module>:<version>` convention.

### Required Secrets

```bash
kubectl create secret generic jclaw-secrets \
  --from-literal=OPENAI_API_KEY=sk-... \
  --from-literal=ANTHROPIC_API_KEY=sk-ant-... \
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
If no ChatClient.Builder bean is available (no API keys set, no Ollama running), `AgentRuntime` won't be created and chat commands will be disabled. Check logs for:
```
ConditionalOnBean(ChatClient.Builder) did not match
```

### Port conflicts
Default ports:
- Shell: no port (interactive terminal)
- Gateway: 8080
- Ollama: 11434
