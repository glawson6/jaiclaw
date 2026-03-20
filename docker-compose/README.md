# JClaw Docker Compose

Run the complete JClaw stack locally with Docker Compose.

## Services

| Service | Description | Port |
|---------|-------------|------|
| `gateway` | JClaw gateway (REST API + WebSocket + channel adapters) | 8080 |
| `ollama` | Local LLM server (free, no API key needed) | 11434 |

## Quick Start

```bash
# 1. Build the Docker image with Maven (from project root)
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
./mvnw package k8s:build -pl jclaw-gateway-app -Pk8s -DskipTests

# 2. Copy and edit the env file
cd docker-compose
cp .env.example .env
# Edit .env — add API keys if using OpenAI/Anthropic, or leave blank for Ollama-only

# 3. Start the stack
docker compose up -d

# 4. Pull an Ollama model
docker compose exec ollama ollama pull llama3.2

# 5. Test (API key is auto-generated at ~/.jclaw/api-key on first run)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "hello"}'

# 6. Check health
curl http://localhost:8080/api/health
```

## Building Images

Images are built with Maven using the JKube `-Pk8s` profile, not by Docker Compose:

```bash
# Gateway image
./mvnw package k8s:build -pl jclaw-gateway-app -Pk8s -DskipTests

# Shell image (optional)
./mvnw package k8s:build -pl jclaw-shell -Pk8s -DskipTests
```

After code changes, rebuild the image and restart:

```bash
./mvnw package k8s:build -pl jclaw-gateway-app -Pk8s -DskipTests
docker compose up -d gateway
```

## Configuration

### Ollama Only (no API keys needed)

Leave `OPENAI_API_KEY` and `ANTHROPIC_API_KEY` blank in `.env`. Pull a model and the gateway uses Ollama automatically:

```bash
docker compose exec ollama ollama pull llama3.2
```

### Cloud LLM Providers

Set API keys in `.env`:

```
OPENAI_API_KEY=sk-...
ANTHROPIC_API_KEY=sk-ant-...
GEMINI_API_KEY=...
```

All four providers (OpenAI, Anthropic, Google Gemini, Ollama) can be active simultaneously.

### Channel Adapters

All channels are optional. Set the relevant tokens in `.env`:

**Telegram** (polling mode — no public endpoint needed):
```
TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
```

**Slack** (Socket Mode — no public endpoint needed):
```
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
```

**Discord** (Gateway WebSocket — no public endpoint needed):
```
DISCORD_BOT_TOKEN=...
DISCORD_USE_GATEWAY=true
```

## Commands

```bash
# Start
docker compose up -d

# View logs
docker compose logs -f gateway

# Stop
docker compose down

# Pull a different Ollama model
docker compose exec ollama ollama pull mistral
```

## Ports

Override default ports in `.env`:

```
GATEWAY_PORT=9090
OLLAMA_PORT=11435
```
