# JaiClaw Operations Guide

## Prerequisites

- Java 21 (Oracle or Temurin) — for local builds and the shell
- Docker — for `quickstart.sh` and `start.sh docker`
- At least one LLM provider API key (Anthropic, OpenAI, Google Gemini, or Ollama)

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
```

---

## start.sh — Daily Driver

`start.sh` is the recommended way to run JaiClaw after initial setup. It reads API keys and configuration from `$JAICLAW_ENV_FILE` (default: `docker-compose/.env`). If `~/.jaiclawrc` exists (written by `quickstart.sh`), it is sourced automatically to set `JAICLAW_ENV_FILE`.

```bash
./start.sh              # start gateway locally (default, requires Java 21)
./start.sh shell        # start interactive CLI shell (local Java)
./start.sh cli          # start interactive CLI shell (Docker, no Java needed)
./start.sh docker       # start gateway via Docker Compose
./start.sh local        # start gateway locally (same as default)
./start.sh login        # list available OAuth providers
./start.sh login chutes # run OAuth login for a provider
./start.sh auth         # show auth profile status (colored table)
./start.sh auth json    # show auth profile status (JSON)
./start.sh stop         # stop Docker Compose stack
./start.sh logs         # tail gateway container logs
./start.sh --force-build          # rebuild from source, then start gateway locally
./start.sh --force-build docker   # rebuild Docker image, then start gateway
./start.sh --force-build cli      # rebuild shell Docker image, then start CLI
./start.sh help         # show all commands
```

### Configuration

Edit your `.env` file (location shown by `~/.jaiclawrc`, default: `docker-compose/.env`) to set your API keys and preferences:

```bash
AI_PROVIDER=anthropic
ANTHROPIC_ENABLED=true
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-4-5
OPENAI_ENABLED=false
OLLAMA_ENABLED=false
```

Both the gateway (Docker and local) and the shell read from this file. Environment variables set in your shell override `.env` values. Run `./quickstart.sh --reconfigure` to change the config location, re-enter API keys, or change the security mode.

### Auth & OAuth

JaiClaw supports OAuth authentication for upstream LLM providers (Chutes, OpenAI Codex, Google Gemini CLI, Qwen, MiniMax) in addition to API keys. OAuth tokens are stored in `~/.jaiclaw/agents/default/agent/auth-profiles.json`.

**Check auth status:**

```bash
./start.sh auth              # colored table of all profiles + external CLI credentials
./start.sh auth json         # machine-readable JSON output
./scripts/auth-status.sh simple  # one-line: OK|EXPIRING|EXPIRED|MISSING (exit codes: 0/1/2/3)
```

**OAuth login:**

```bash
./start.sh login             # list available OAuth providers
./start.sh login chutes      # browser-based OAuth for Chutes AI
./start.sh login qwen-portal # device code flow for Qwen
```

Available providers: `chutes`, `openai-codex`, `google-gemini-cli`, `qwen-portal`, `minimax-portal`.

Login uses JBang (`JaiClawAuth.java`) if available, otherwise falls back to Maven + Spring Shell.

**Startup warnings:** All launch commands (`local`, `shell`, `cli`, `docker`) automatically check auth status and print a non-blocking warning if any OAuth tokens are expiring or expired.

**Auth monitoring (cron):**

```bash
./scripts/setup-auth-monitor.sh       # interactive wizard to install cron/systemd timer
./scripts/auth-monitor.sh             # manual check (cron-compatible)
```

Supports ntfy.sh and email notifications. Configure via `JAICLAW_NOTIFY_NTFY`, `JAICLAW_NOTIFY_EMAIL`, and `JAICLAW_AUTH_WARN_HOURS` env vars.

**Docker auth volume mounting:** When running in Docker mode, credential directories (`~/.jaiclaw`, `~/.claude`, `~/.codex`, `~/.qwen`, `~/.minimax`) are automatically mounted read-only into containers. Control with `JAICLAW_DOCKER_AUTH_DIRS` (values: `auto`, `all`, `none`, or comma-separated provider keys).

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
jaiclaw> chat hello                  # send a message to the agent
jaiclaw> chat what time is it?       # multi-word messages work naturally
jaiclaw> new-session                 # start a fresh conversation
jaiclaw> sessions                    # list all sessions
jaiclaw> session-history             # show messages in current session
```

Other commands:

```
jaiclaw> status                      # system status (identity, tools, sessions)
jaiclaw> config                      # current configuration
jaiclaw> models                      # configured LLM providers
jaiclaw> tools                       # available tools
jaiclaw> skills                      # loaded skills
jaiclaw> plugins                     # loaded plugins
jaiclaw> onboard                     # interactive setup wizard
jaiclaw> help                        # all available commands
```

---

## quickstart.sh — First-Time Docker Setup

For a fresh clone with no Java installed:

```bash
git clone https://github.com/jaiclaw/jaiclaw.git && cd jaiclaw
ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh
```

This detects Java, builds the Docker image via Maven + JKube, starts Docker Compose, and optionally pulls Ollama if no API key is set.

On first run (no existing `.env`), quickstart prompts where to save configuration:
- `~/.jaiclaw/.env` — recommended, persists across project clones
- `docker-compose/.env` — project-local (original behavior)

The chosen location is written to `~/.jaiclawrc` and respected by all scripts.

To force a rebuild of the Docker image (e.g. after code changes):

```bash
./quickstart.sh --force-build
```

To re-run the full interactive setup (change provider, API keys, channels, config location):

```bash
./quickstart.sh --reconfigure
```

The reconfigure wizard now includes OAuth providers (Chutes, OpenAI Codex, Gemini CLI, Qwen, MiniMax) in addition to API key providers.

**Non-interactive mode** (for CI/CD or scripted deployments):

```bash
AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh --non-interactive --docker
```

Required env vars: `AI_PROVIDER` (or a provider API key for auto-detection). OAuth providers in non-interactive mode are skipped (assumed pre-authenticated).

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
jaiclaw> onboard
```

The wizard covers:
- LLM provider + API key + model
- **Security mode** (API key / JWT / none) and optional custom API key
- Gateway settings (port, bind address, assistant name — manual mode)
- Channel setup (Telegram, Slack, Discord)
- Skills and MCP server connections

The wizard writes `application-local.yml` + `.env` to `~/.jaiclaw/` (or current directory). After the wizard finishes, restart the shell to activate.

---

## Running Locally (Manual Configuration)

If you prefer to pass environment variables directly instead of using `docker-compose/.env`:

### Shell

```bash
# With Anthropic (default)
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jaiclaw-shell

# With OpenAI
AI_PROVIDER=openai OPENAI_ENABLED=true OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl jaiclaw-shell

# With Ollama (free, local)
AI_PROVIDER=ollama OLLAMA_ENABLED=true ./mvnw spring-boot:run -pl jaiclaw-shell

# With Google Gemini
AI_PROVIDER=google-genai GEMINI_ENABLED=true GEMINI_API_KEY=... ./mvnw spring-boot:run -pl jaiclaw-shell
```

### Gateway

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jaiclaw-gateway-app
```

Test (the API key is auto-generated at `~/.jaiclaw/api-key` on first run — see [Security](#security) below):
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
TELEGRAM_WEBHOOK_URL=https://jaiclaw.taptech.net/webhook/telegram \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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
./mvnw spring-boot:run -pl jaiclaw-gateway-app
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

JaiClaw can host MCP (Model Context Protocol) tool servers, making JaiClaw's tools available to external AI clients.

**Endpoints** (all require `X-API-Key` header in `api-key` security mode):
```bash
# List available MCP servers
curl http://localhost:8080/mcp \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)"

# List tools for a server
curl http://localhost:8080/mcp/{serverName}/tools \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)"

# Execute a tool
curl -X POST http://localhost:8080/mcp/{serverName}/tools/{toolName} \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
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
| `config` | Show current JaiClaw configuration |
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

See the [Gemma 4 Hardware Guide](GEMMA4-HARDWARE-GUIDE.md) for model selection, VRAM requirements, and performance benchmarks when running Gemma 4 variants.

**Docker (used by quickstart.sh):**

```bash
docker pull ollama/ollama:latest
docker compose --profile ollama up -d
docker compose --profile ollama exec ollama ollama pull llama3.2
```

> **Note:** The quickstart script starts Ollama automatically when no `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` is set. To skip Ollama and use a cloud provider: `ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh`

### Multiple Providers

Multiple providers can be configured simultaneously in `docker-compose/.env`:

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

### Wizard Model Lists & Fallbacks

The onboard wizard's model selector and the generated config's fallback models are defined in `application.yml` under `jaiclaw.models.providers.*`. This allows updating model lists without recompilation.

**Default configuration:**

```yaml
jaiclaw:
  models:
    providers:
      openai:
        display-name: OpenAI
        fallback-model: gpt-4o-mini
        wizard-models:
          - gpt-4o
          - gpt-4o-mini
          - gpt-4.1
          - gpt-4.1-mini
          - o3-mini
      anthropic:
        display-name: Anthropic
        fallback-model: claude-haiku-4-5-20251001
        wizard-models:
          - claude-sonnet-4-6
          - claude-opus-4-6
          - claude-haiku-4-5-20251001
      bedrock:
        display-name: AWS Bedrock
        fallback-model: us.anthropic.claude-3-haiku-20240307-v1:0
        wizard-models:
          - us.anthropic.claude-3-5-sonnet-20241022-v2:0
          - us.anthropic.claude-3-haiku-20240307-v1:0
          - us.meta.llama3-1-70b-instruct-v1:0
          - amazon.nova-pro-v1:0
      ollama:
        display-name: Ollama
        wizard-models:
          - llama3
          - "llama3:70b"
          - mistral
          - codellama
          - gemma2
```

**Override via environment variables** (Spring Boot relaxed binding):

| What you want | Env var | YAML equivalent |
|---|---|---|
| Replace first OpenAI model | `JAICLAW_MODELS_PROVIDERS_OPENAI_WIZARD_MODELS_0=gpt-5` | `jaiclaw.models.providers.openai.wizard-models[0]: gpt-5` |
| Replace entire OpenAI list | `JAICLAW_MODELS_PROVIDERS_OPENAI_WIZARD_MODELS_0=gpt-5`<br>`JAICLAW_MODELS_PROVIDERS_OPENAI_WIZARD_MODELS_1=gpt-5-mini` | `jaiclaw.models.providers.openai.wizard-models: [gpt-5, gpt-5-mini]` |
| Change fallback model | `JAICLAW_MODELS_PROVIDERS_OPENAI_FALLBACK_MODEL=gpt-5-mini` | `jaiclaw.models.providers.openai.fallback-model: gpt-5-mini` |
| Change display name | `JAICLAW_MODELS_PROVIDERS_BEDROCK_DISPLAY_NAME=Amazon Bedrock` | `jaiclaw.models.providers.bedrock.display-name: Amazon Bedrock` |
| Add a new provider | `JAICLAW_MODELS_PROVIDERS_DEEPSEEK_DISPLAY_NAME=DeepSeek`<br>`JAICLAW_MODELS_PROVIDERS_DEEPSEEK_FALLBACK_MODEL=deepseek-chat`<br>`JAICLAW_MODELS_PROVIDERS_DEEPSEEK_WIZARD_MODELS_0=deepseek-chat`<br>`JAICLAW_MODELS_PROVIDERS_DEEPSEEK_WIZARD_MODELS_1=deepseek-coder` | See below |

**Adding a new provider via YAML** (in `application-local.yml` or `application.yml`):

```yaml
jaiclaw:
  models:
    providers:
      deepseek:
        display-name: DeepSeek
        fallback-model: deepseek-chat
        wizard-models:
          - deepseek-chat
          - deepseek-coder
```

**Override in Docker Compose** (`.env` file):

```bash
JAICLAW_MODELS_PROVIDERS_ANTHROPIC_WIZARD_MODELS_0=claude-next
JAICLAW_MODELS_PROVIDERS_ANTHROPIC_WIZARD_MODELS_1=claude-haiku-next
JAICLAW_MODELS_PROVIDERS_ANTHROPIC_FALLBACK_MODEL=claude-haiku-next
```

> **Note:** When any indexed env var is set for a list (e.g., `_WIZARD_MODELS_0`), Spring Boot replaces the entire YAML list with the env-var-defined entries. Always define all list entries when overriding via env vars.

---

### AI Model Provider Reference

JaiClaw ships a starter module for each supported AI model provider. Each starter is a POM-only module that bundles `jaiclaw-spring-boot-starter` with the corresponding Spring AI model starter.

#### Provider Credentials Index

| Provider | JaiClaw Starter | Spring AI Property Prefix | API Key Env Var | Enable Env Var | Default |
|----------|----------------|--------------------------|-----------------|----------------|---------|
| Anthropic | `jaiclaw-starter-anthropic` | `spring.ai.anthropic` | `ANTHROPIC_API_KEY` | `ANTHROPIC_ENABLED` | **On** |
| OpenAI | `jaiclaw-starter-openai` | `spring.ai.openai` | `OPENAI_API_KEY` | `OPENAI_ENABLED` | Off |
| Ollama | `jaiclaw-starter-ollama` | `spring.ai.ollama` | *(none — local)* | `OLLAMA_ENABLED` | Off |
| Google Gemini | `jaiclaw-starter-gemini` | `spring.ai.google.genai` | `GEMINI_API_KEY` | `GEMINI_ENABLED` | Off |
| AWS Bedrock | `jaiclaw-starter-bedrock` | `spring.ai.bedrock` | *(AWS credentials)* | `BEDROCK_ENABLED` | Off |
| Azure OpenAI | `jaiclaw-starter-azure-openai` | `spring.ai.azure.openai` | `AZURE_OPENAI_API_KEY` | `AZURE_OPENAI_ENABLED` | Off |
| DeepSeek | `jaiclaw-starter-deepseek` | `spring.ai.deepseek` | `DEEPSEEK_API_KEY` | `DEEPSEEK_ENABLED` | Off |
| Mistral AI | `jaiclaw-starter-mistral` | `spring.ai.mistralai` | `MISTRAL_API_KEY` | `MISTRAL_ENABLED` | Off |
| MiniMax | `jaiclaw-starter-minimax` | `spring.ai.minimax` | `MINIMAX_API_KEY` | `MINIMAX_ENABLED` | Off |
| Vertex AI | `jaiclaw-starter-vertex-ai` | `spring.ai.vertex.ai.gemini` | *(GCP credentials)* | `VERTEX_AI_ENABLED` | Off |
| OCI GenAI | `jaiclaw-starter-oci-genai` | `spring.ai.oci.genai` | *(OCI credentials)* | `OCI_GENAI_ENABLED` | Off |

**Core providers** (bundled in gateway-app and shell): Anthropic, OpenAI, Ollama, Gemini, Bedrock.
**Extended providers** (opt-in via starter dependency): Azure OpenAI, DeepSeek, Mistral, MiniMax, Vertex AI, OCI GenAI.

#### Provider Setup Details

**Azure OpenAI** — Requires an Azure OpenAI Service resource. Get credentials from the Azure Portal.

```bash
AI_PROVIDER=azure-openai \
  AZURE_OPENAI_ENABLED=true \
  AZURE_OPENAI_API_KEY=your-key \
  AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com \
  ./start.sh shell
```

```yaml
spring.ai.azure.openai:
  enabled: ${AZURE_OPENAI_ENABLED:false}
  api-key: ${AZURE_OPENAI_API_KEY:}
  endpoint: ${AZURE_OPENAI_ENDPOINT:}
```

**DeepSeek** — Get an API key from [platform.deepseek.com](https://platform.deepseek.com/).

```bash
AI_PROVIDER=deepseek \
  DEEPSEEK_ENABLED=true \
  DEEPSEEK_API_KEY=sk-... \
  ./start.sh shell
```

```yaml
spring.ai.deepseek:
  enabled: ${DEEPSEEK_ENABLED:false}
  api-key: ${DEEPSEEK_API_KEY:}
  chat.options.model: ${DEEPSEEK_MODEL:deepseek-chat}
```

**Mistral AI** — Get an API key from [console.mistral.ai](https://console.mistral.ai/).

```bash
AI_PROVIDER=mistral-ai \
  MISTRAL_ENABLED=true \
  MISTRAL_API_KEY=your-key \
  ./start.sh shell
```

```yaml
spring.ai.mistralai:
  enabled: ${MISTRAL_ENABLED:false}
  api-key: ${MISTRAL_API_KEY:}
  chat.options.model: ${MISTRAL_MODEL:mistral-large-latest}
```

**MiniMax** — Get an API key from [platform.minimaxi.com](https://platform.minimaxi.com/).

```bash
AI_PROVIDER=minimax \
  MINIMAX_ENABLED=true \
  MINIMAX_API_KEY=your-key \
  ./start.sh shell
```

```yaml
spring.ai.minimax:
  enabled: ${MINIMAX_ENABLED:false}
  api-key: ${MINIMAX_API_KEY:}
  chat.options.model: ${MINIMAX_MODEL:M2-her}
```

**MiniMax via Anthropic-compatible endpoint** — MiniMax exposes an Anthropic-compatible API at `https://api.minimax.io/anthropic`. This lets you use MiniMax models through the standard Anthropic provider without enabling the native MiniMax Spring AI starter.

```bash
ANTHROPIC_API_KEY=your-minimax-key \
  ANTHROPIC_BASE_URL=https://api.minimax.io/anthropic \
  ANTHROPIC_MODEL=M2-her \
  ./start.sh shell
```

No additional dependencies or provider flags are needed — JaiClaw treats it as a standard Anthropic endpoint.

**Vertex AI** — Requires a GCP project with Vertex AI enabled. Uses Application Default Credentials (ADC).

```bash
gcloud auth application-default login
AI_PROVIDER=vertex-ai \
  VERTEX_AI_ENABLED=true \
  VERTEX_AI_PROJECT_ID=your-project \
  VERTEX_AI_LOCATION=us-central1 \
  ./start.sh shell
```

```yaml
spring.ai.vertex.ai.gemini:
  enabled: ${VERTEX_AI_ENABLED:false}
  project-id: ${VERTEX_AI_PROJECT_ID:}
  location: ${VERTEX_AI_LOCATION:us-central1}
  chat.options.model: ${VERTEX_AI_MODEL:gemini-2.0-flash}
```

**OCI GenAI** — Requires an Oracle Cloud account with OCI GenAI service. Uses OCI config file credentials.

```bash
AI_PROVIDER=oci-genai \
  OCI_GENAI_ENABLED=true \
  OCI_GENAI_COMPARTMENT_ID=ocid1.compartment... \
  ./start.sh shell
```

```yaml
spring.ai.oci.genai:
  enabled: ${OCI_GENAI_ENABLED:false}
  compartment-id: ${OCI_GENAI_COMPARTMENT_ID:}
  chat.options.model: ${OCI_GENAI_MODEL:cohere.command-r-plus}
```

#### Using an Extended Provider

Extended providers are not on the classpath by default. To use one, replace the model provider starter in your `pom.xml`:

```xml
<!-- Replace jaiclaw-starter-anthropic with your provider -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-deepseek</artifactId>
    <type>pom</type>
</dependency>
```

Or add it alongside existing providers for multi-provider setups.

---

## Security

The gateway protects `/api/chat` and `/mcp/**` endpoints with API key authentication by default. The security mode is controlled by `JAICLAW_SECURITY_MODE`.

### Security Modes

| Mode | Description |
|------|-------------|
| `api-key` (default) | Requests must include `X-API-Key` header with a valid key |
| `jwt` | Requests must include a valid JWT `Authorization: Bearer <token>` header |
| `none` | No authentication — **development only** |

### API Key Resolution

When `JAICLAW_SECURITY_MODE=api-key` (the default), the API key is resolved in this order:

1. `JAICLAW_API_KEY` environment variable
2. Key file at `JAICLAW_API_KEY_FILE` (default: `~/.jaiclaw/api-key`)
3. Auto-generate a key and write it to `~/.jaiclaw/api-key`

The launcher scripts (`start.sh`, `quickstart.sh`, `setup.sh`) resolve the API key before the JVM starts and print it in curl examples. The JVM's `ApiKeyProvider` follows the same resolution order, so both see the same key.

```bash
# View your current API key
cat ~/.jaiclaw/api-key

# Use a custom key
JAICLAW_API_KEY=my-custom-key ./start.sh local

# Disable security (development only)
JAICLAW_SECURITY_MODE=none ./start.sh local
```

### Configuring via the Onboard Wizard

The `onboard` command in the shell includes a security step:
- **Quickstart mode**: Defaults to `api-key` with auto-generation (no prompt)
- **Manual mode**: Prompts for security mode and optional custom API key

The `--reconfigure` flag in `quickstart.sh` also includes a security step.

### Security Hardening

JaiClaw provides opt-in security hardening flags that are all **disabled by default**. Enable them individually or activate the `security-hardened` Spring profile to enable all at once:

```bash
SPRING_PROFILES_ACTIVE=security-hardened ./start.sh local
```

| Flag | Default | Description |
|------|---------|-------------|
| `jaiclaw.channels.slack.verify-signature` | `false` | HMAC-SHA256 verification of Slack webhook signatures with replay protection |
| `jaiclaw.channels.telegram.verify-webhook` | `false` | `X-Telegram-Bot-Api-Secret-Token` header verification on inbound webhooks |
| `jaiclaw.channels.telegram.mask-bot-token` | `false` | Use SHA-256 hash prefix of bot token as accountId in session keys |
| `jaiclaw.tools.web.ssrf-protection` | `false` | Block WebFetchTool requests to private/internal/link-local IPs |
| `jaiclaw.tools.code.workspace-boundary` | `false` | Path traversal protection in FileEditTool, GrepTool, GlobTool |
| `jaiclaw.security.timing-safe-api-key` | `false` | Constant-time API key comparison via `MessageDigest.isEqual()` |

---

## Skills Configuration

JaiClaw ships with a library of bundled skills — Markdown-based behavioral instructions loaded into the LLM's system prompt. By default, **all bundled skills are loaded** (`jaiclaw.skills.allow-bundled: ["*"]`).

### The Problem: Token Bloat

The bundled skill library contains 59 skills totaling ~160KB of text. On a typical developer machine, roughly 27 pass platform eligibility checks and are injected verbatim into the system prompt on every LLM request. This adds **~26,000 tokens** of context that may be entirely irrelevant to your application.

**Side effects of not configuring skills:**

| Impact | Description |
|--------|-------------|
| **Cost** | ~26,000 extra input tokens per request. At typical API pricing ($3/M input tokens), this adds ~$0.08 per request — a 60x increase for a simple query that would otherwise use ~500 tokens. |
| **Latency** | More input tokens means longer time-to-first-token. The LLM must process all skill content before generating a response. |
| **Quality** | Irrelevant skills (e.g., kubectl commands injected into a travel planner) can confuse the model and degrade response quality. The model may attempt to use skills that have nothing to do with the task. |
| **Context window** | Skill content consumes context window capacity that could be used for conversation history, tool results, or user content. Long conversations will hit compaction thresholds sooner. |

### Configuration

```yaml
jaiclaw:
  skills:
    # Default — loads ALL bundled skills (dangerous for production)
    allow-bundled: ["*"]

    # Recommended — disable all bundled skills for focused applications
    allow-bundled: []

    # Selective — whitelist only the skills you need
    allow-bundled:
      - conversation
      - web-research
      - summarize

    # Watch workspace for custom SKILL.md files (default: true)
    watch-workspace: true

    # Directory for custom workspace skills
    workspace-dir: /path/to/skills
```

### Recommendations

- **Custom applications and examples**: Set `allow-bundled: []` and rely on your own tools and plugins. Bundled skills are designed for the general-purpose JaiClaw shell, not specialized apps.
- **General-purpose shell**: The default `["*"]` is appropriate — the shell benefits from coding, system admin, and web research skills.
- **Production deployments**: Always whitelist specific skills. Never deploy with `["*"]` unless you have verified the token impact and are comfortable with the cost.

### Diagnosing Token Bloat

If you see unexpectedly high input token counts in the `AgentRuntime` INFO log, check your skills configuration:

```bash
# Check how many skills are loaded
jaiclaw> skills

# Check token usage per request
# (logged automatically at INFO level)
INFO AgentRuntime - LLM usage — request: 32,951 tokens, response: 133 tokens, total: 33,084 tokens
# ^^^ 33K input tokens for a simple query = bundled skills are loaded
```

### Pre-Flight Token Check (prompt-analyzer)

Use the `jaiclaw-prompt-analyzer` CLI tool to estimate token usage **before running the app**:

```bash
# Build the standalone analyzer
./mvnw package -pl :jaiclaw-prompt-analyzer -Pstandalone -DskipTests

# Analyze a project
java -jar tools/jaiclaw-prompt-analyzer/target/jaiclaw-prompt-analyzer-*.jar \
  prompt-analyze --path /path/to/my-app

# CI gate — fail if estimated tokens exceed threshold
java -jar tools/jaiclaw-prompt-analyzer/target/jaiclaw-prompt-analyzer-*.jar \
  prompt-check --path /path/to/my-app --threshold 5000
```

The analyzer scans `application.yml`, resolves skills and tools, and produces a per-component token breakdown. It catches common misconfigurations like missing `allow-bundled` (which defaults to loading all ~59 skills at ~26K tokens).

See [CLI Tools Reference](../../../dev/docs/jaiclaw/dev-guide/cli-tools.md#jaiclaw-prompt-analyzer) for full documentation.

### Maven Plugin (CI/CD Integration)

The `jaiclaw-maven-plugin` runs the same analysis during the Maven build lifecycle, failing the build if a token threshold is exceeded. All JaiClaw examples include this plugin by default.

**Add to your project's `pom.xml`:**

```xml
<plugin>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <goals><goal>analyze</goal></goals>
        </execution>
    </executions>
    <configuration>
        <threshold>5000</threshold>          <!-- fail if tokens exceed this -->
        <failOnWarning>true</failOnWarning>  <!-- fail on missing allow-bundled -->
    </configuration>
</plugin>
```

**On-demand analysis** (no POM changes needed):

```bash
./mvnw io.jaiclaw:jaiclaw-maven-plugin:analyze -pl :my-agent-app
./mvnw io.jaiclaw:jaiclaw-maven-plugin:analyze -pl :my-agent-app -Djaiclaw.analyze.threshold=5000
```

**Parameters:**

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `threshold` | `jaiclaw.analyze.threshold` | `0` (disabled) | Fail if tokens exceed this |
| `failOnWarning` | `jaiclaw.analyze.failOnWarning` | `false` | Fail if warnings present |
| `skip` | `jaiclaw.analyze.skip` | `false` | Skip analysis entirely |

The plugin runs during the `verify` phase and gracefully skips modules without `application.yml`.

See [Token Usage Logging](#token-usage-logging) for how to enable detailed request/response tracing.

---

## Token Usage Logging

JaiClaw logs token usage after every LLM call. Two loggers provide different levels of detail:

### INFO: Token Count Summary (default: on)

Logger: `io.jaiclaw.agent.AgentRuntime`

```
INFO  AgentRuntime - LLM usage — request: 1,247 tokens, response: 583 tokens, total: 1,830 tokens
INFO  AgentRuntime - LLM cache — read: 1,024 tokens, write: 223 tokens
```

The cache line only appears when values are non-zero (Anthropic prompt caching).

### TRACE: Full Request/Response Content (default: off)

Logger: `io.jaiclaw.agent.LlmTraceLogger`

When enabled, logs the full LLM request broken down into three components — system prompt, tools, and conversation — each with an estimated token count (~chars/4). The provider's actual total is shown for comparison.

```
TRACE LlmTraceLogger - LLM request — system prompt (~148 tokens):
You are Travel Planner, a specialized AI travel planning assistant...

TRACE LlmTraceLogger - LLM request — tools (4 tools, ~1,872 tokens):
search_flights: Search for flights between cities
  schema: {"type":"object","properties":{"origin":{"type":"string"},...}}
search_hotels: Search for hotel accommodations
  schema: {"type":"object","properties":{"destination":{"type":"string"},...}}
...

TRACE LlmTraceLogger - LLM request — conversation (~5 tokens):
[user] hello

TRACE LlmTraceLogger - LLM request — token breakdown: system ~148 + tools ~1,872 + conversation ~5 = ~2,025 estimated, provider total: 2,317

TRACE LlmTraceLogger - LLM response (133 tokens):
I'm Travel Planner, a specialized AI assistant designed to help you plan trips!
```

The estimated counts are approximations (~chars/4). The provider total includes API framing overhead not captured in the estimate. The gap between the estimate and provider total is typically 10-15% and accounts for tokenizer differences and protocol overhead.

### Configuration

```yaml
logging:
  level:
    # Token count summary (default: INFO = on, set WARN to disable)
    io.jaiclaw.agent.AgentRuntime: INFO

    # Full request/response content (default: WARN = off, set TRACE to enable)
    io.jaiclaw.agent.LlmTraceLogger: WARN
```

The two loggers are independent — you can have token summaries without full content, or both.

### Token Usage in AssistantMessage

Token usage is also recorded on each `AssistantMessage` in the session via the `usage` field (`TokenUsage` record with `inputTokens`, `outputTokens`, `cacheReadTokens`, `cacheWriteTokens`). This is available programmatically for billing, analytics, or rate limiting.

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `HTTPS_PROXY` | No | Proxy URL for HTTPS traffic (e.g. `http://user:pass@proxy:8080`) — auto-detected |
| `HTTP_PROXY` | No | Proxy URL for HTTP traffic (fallback if `HTTPS_PROXY` not set) — auto-detected |
| `NO_PROXY` | No | Comma-separated hosts that bypass the proxy (e.g. `localhost,127.0.0.1`) |
| `JAVA_HOME` | Yes | Path to Java 21 JDK |
| `JAICLAW_HOME` | No | Config directory override (default: `~/.jaiclaw/`) |
| `JAICLAW_ENV_FILE` | No | Path to `.env` file (default: `docker-compose/.env`). Auto-set by `~/.jaiclawrc`. |
| `JAICLAW_SECURITY_MODE` | No | Security mode: `api-key` (default), `jwt`, or `none` |
| `JAICLAW_API_KEY` | No | Custom API key (auto-generated if not set) |
| `JAICLAW_API_KEY_FILE` | No | Path to API key file (default: `~/.jaiclaw/api-key`) |
| `AI_PROVIDER` | No | Primary LLM provider: `anthropic` (default), `openai`, `ollama`, `google-genai`, `bedrock`, `azure-openai`, `deepseek`, `mistral-ai`, `minimax`, `vertex-ai`, `oci-genai` |
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
| `BEDROCK_ENABLED` | No | Enable AWS Bedrock provider (default: `false`) |
| `BEDROCK_MODEL` | No | Bedrock model ID (default: `us.anthropic.claude-3-5-sonnet-20241022-v2:0`) |
| `AWS_REGION` | For Bedrock | AWS region (default: `us-east-1`) |
| `AZURE_OPENAI_API_KEY` | For Azure | Azure OpenAI API key |
| `AZURE_OPENAI_ENABLED` | No | Enable Azure OpenAI provider (default: `false`) |
| `AZURE_OPENAI_ENDPOINT` | For Azure | Azure OpenAI endpoint URL |
| `DEEPSEEK_API_KEY` | For DeepSeek | DeepSeek API key |
| `DEEPSEEK_ENABLED` | No | Enable DeepSeek provider (default: `false`) |
| `DEEPSEEK_MODEL` | No | DeepSeek model name (default: `deepseek-chat`) |
| `MISTRAL_API_KEY` | For Mistral | Mistral AI API key |
| `MISTRAL_ENABLED` | No | Enable Mistral AI provider (default: `false`) |
| `MISTRAL_MODEL` | No | Mistral model name (default: `mistral-large-latest`) |
| `MINIMAX_API_KEY` | For MiniMax | MiniMax API key |
| `MINIMAX_ENABLED` | No | Enable MiniMax provider (default: `false`) |
| `MINIMAX_MODEL` | No | MiniMax model name (default: `M2-her`) |
| `VERTEX_AI_ENABLED` | No | Enable Google Vertex AI provider (default: `false`) |
| `VERTEX_AI_PROJECT_ID` | For Vertex | GCP project ID |
| `VERTEX_AI_LOCATION` | No | GCP location (default: `us-central1`) |
| `VERTEX_AI_MODEL` | No | Vertex AI model name (default: `gemini-2.0-flash`) |
| `OCI_GENAI_ENABLED` | No | Enable OCI GenAI provider (default: `false`) |
| `OCI_GENAI_COMPARTMENT_ID` | For OCI | OCI compartment OCID |
| `OCI_GENAI_MODEL` | No | OCI GenAI model name (default: `cohere.command-r-plus`) |
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

Each JaiClaw instance stores configuration in a **config directory** (default: `~/.jaiclaw/`). To run multiple independent instances — for example, separate personal/work assistants, or a dev/test instance alongside production — point each to a different config directory using the `JAICLAW_HOME` environment variable.

### Shell Instances

```bash
# Instance 1: personal assistant
JAICLAW_HOME=~/.jaiclaw-personal ./mvnw spring-boot:run -pl jaiclaw-shell
# Run onboard wizard → configures OpenAI + Telegram in ~/.jaiclaw-personal/

# Instance 2: work assistant
JAICLAW_HOME=~/.jaiclaw-work ./mvnw spring-boot:run -pl jaiclaw-shell
# Run onboard wizard → configures Anthropic + Slack in ~/.jaiclaw-work/

# Instance 3: dev/test (free, local LLM)
JAICLAW_HOME=~/.jaiclaw-dev ./mvnw spring-boot:run -pl jaiclaw-shell
# Run onboard wizard → configures Ollama in ~/.jaiclaw-dev/
```

Each config directory contains its own `application-local.yml` and `.env`, so LLM providers, channel connections, and all settings are fully isolated.

After the wizard writes config, source the correct `.env` for each instance:

```bash
source ~/.jaiclaw-personal/.env && JAICLAW_HOME=~/.jaiclaw-personal ./mvnw spring-boot:run -pl jaiclaw-shell
```

### Gateway Instances

For multiple gateway instances, also assign different ports:

```bash
# Production gateway on port 8080
source ~/.jaiclaw-prod/.env
JAICLAW_HOME=~/.jaiclaw-prod SERVER_PORT=8080 ./mvnw spring-boot:run -pl jaiclaw-gateway-app

# Staging gateway on port 8081
source ~/.jaiclaw-staging/.env
JAICLAW_HOME=~/.jaiclaw-staging SERVER_PORT=8081 ./mvnw spring-boot:run -pl jaiclaw-gateway-app
```

### How It Works

- `JAICLAW_HOME` overrides the default `~/.jaiclaw/` config directory
- The shell auto-imports `${JAICLAW_HOME}/application-local.yml` via `spring.config.import`
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
./mvnw test -pl jaiclaw-channel-telegram -o

# Install to local Maven repo (needed for offline single-module builds)
./mvnw install -DskipTests

# Package as JARs
./mvnw package -DskipTests
```

**Offline mode**: Use `-o` flag after initial dependency download. The Nexus repo at tooling.taptech.net causes timeouts when unreachable. When adding new external dependencies, run once without `-o`.

---

## Publishing Artifacts to Nexus

Deploy JARs to the Nexus repository. Repository URLs are configured via environment variables in `docker-compose/.env`:

```
DEPLOY_RELEASES_URL=<nexus-releases-url>
DEPLOY_SNAPSHOTS_URL=<nexus-snapshots-url>
```

Server credentials are in `~/.m2/settings.xml` under server ID `taptech-repo`.

```bash
# Source the env file to load DEPLOY_*_URL variables
source docker-compose/.env

# Deploy all modules (SNAPSHOT versions go to snapshots repo, releases to releases repo)
./mvnw deploy -DskipTests \
  -Ddeploy.snapshots.url=$DEPLOY_SNAPSHOTS_URL \
  -Ddeploy.releases.url=$DEPLOY_RELEASES_URL

# Deploy a single module
./mvnw deploy -pl :jaiclaw-documents -DskipTests \
  -Ddeploy.snapshots.url=$DEPLOY_SNAPSHOTS_URL \
  -Ddeploy.releases.url=$DEPLOY_RELEASES_URL

# Deploy a module and all its dependencies (-am)
./mvnw deploy -pl :jaiclaw-spring-boot-starter -am -DskipTests \
  -Ddeploy.snapshots.url=$DEPLOY_SNAPSHOTS_URL \
  -Ddeploy.releases.url=$DEPLOY_RELEASES_URL
```

The root POM's `<distributionManagement>` uses `${deploy.releases.url}` and `${deploy.snapshots.url}` properties, so the URLs are never hardcoded in source control.

---

## Production Deployment (k8s)

See `ARCHITECTURE.md` for the full k8s deployment view.

### Docker Image Build (JKube)

Two modules produce Docker images: `jaiclaw-gateway-app` (production server) and `jaiclaw-shell` (CLI).

```bash
# Build gateway image (includes all dependencies with -am)
./mvnw package k8s:build -pl jaiclaw-gateway-app -am -Pk8s -DskipTests

# Build shell image
./mvnw package k8s:build -pl jaiclaw-shell -am -Pk8s -DskipTests

# Build both at once
./mvnw package k8s:build -pl jaiclaw-gateway-app,jaiclaw-shell -am -Pk8s -DskipTests

# Push to registry
./mvnw k8s:push -pl jaiclaw-gateway-app -Pk8s

# Deploy
./mvnw k8s:resource k8s:apply -pl jaiclaw-gateway-app -Pk8s
```

Images use `eclipse-temurin:21-jre` as the base. The image name follows `io.jaiclaw/<module>:<version>` convention.

The shell Docker image can be run interactively via Docker Compose:
```bash
./start.sh cli
# or directly:
docker compose -f docker-compose/docker-compose.yml --profile cli run --rm cli
```

### Required Secrets

```bash
kubectl create secret generic jaiclaw-secrets \
  --from-literal=JAICLAW_API_KEY=jaiclaw_ak_... \
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
TELEGRAM_WEBHOOK_URL=https://jaiclaw.taptech.net/webhook/telegram
```

The adapter will call `setWebhook` on startup to register with Telegram.

---

## Troubleshooting

### HTTP proxy not working
- Verify env vars: `echo $HTTPS_PROXY` — must be a full URL like `http://proxy:8080`
- Check startup log for `HTTP proxy configured: host:port` message
- Explicit YAML config (`jaiclaw.http.proxy.*`) takes precedence over env vars
- Spring AI providers use `RestClientCustomizer`; built-in tools and MCP clients use `ProxyAwareHttpClientFactory`

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
