# Telegram DocStore Example

Telegram document management bot — users send documents via Telegram, the bot ingests and indexes them for semantic search, and answers questions using the stored knowledge base.

## What This Demonstrates

- **DocStore integration** (`jaiclaw-docstore` + `jaiclaw-docstore-telegram`) — document ingestion, indexing, search, and analysis
- **Telegram channel adapter** — receives documents and messages via Bot API polling (or webhook)
- **Plugin-based tool registration** — `TelegramDocStorePlugin` auto-registers `docstore_ingest`, `docstore_search`, `docstore_analyze`
- **JSON file persistence** — documents stored in `~/.jaiclaw/docstore/`
- **Explicit tool loop** with up to 10 iterations

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│                 TELEGRAM DOCSTORE APP                      │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Gateway          │  REST API + Telegram webhook             │
├──────────────────┼───────────────────────────────────────┤
│ Channel          │  [jaiclaw-channel-telegram]               │
│                  │  → Bot API polling (or webhook)           │
├──────────────────┼───────────────────────────────────────┤
│ Plugin           │  TelegramDocStorePlugin                  │
│                  │  → docstore_ingest                        │
│                  │  → docstore_search                        │
│                  │  → docstore_analyze                       │
├──────────────────┼───────────────────────────────────────┤
│ Agent            │  Explicit tool loop (max 10 iters)       │
├──────────────────┼───────────────────────────────────────┤
│ Storage          │  JsonFileDocStoreRepository               │
│                  │  → ~/.jaiclaw/docstore/                    │
├──────────────────┼───────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴───────────────────────────────────────┘

Data flow:
  Telegram User
       │
       ├──(sends document)──► Channel Adapter
       │                          │
       │                          ▼
       │                    docstore_ingest
       │                    (parse + index)
       │                          │
       │                          ▼
       │                  JSON file storage
       │
       ├──("search for X")──► Agent
       │                         │
       │                         ▼
       │                  docstore_search
       │                  (semantic match)
       │                         │
       │◄──(results)─────────────┘
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key
- Telegram bot token (from [@BotFather](https://t.me/BotFather))

## Build & Run

```bash
cd jaiclaw-examples/telegram-docstore
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... \
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
../../mvnw spring-boot:run
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


## Testing It

1. Open Telegram and find your bot
2. Send a document (PDF, text file, etc.) — the bot ingests and indexes it
3. Ask questions about the document — the bot searches and answers from the stored content

```bash
# Or test via REST API
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "Search the docstore for information about quarterly revenue"}'

# Health check
curl http://localhost:8080/api/health
```

## Configuration

| Property | Default | Env Var | Description |
|----------|---------|---------|-------------|
| `jaiclaw.channels.telegram.bot-token` | (required) | `TELEGRAM_BOT_TOKEN` | Telegram bot token |
| `jaiclaw.channels.telegram.webhook-url` | (blank = polling) | `TELEGRAM_WEBHOOK_URL` | Public webhook URL for production |
| `jaiclaw.channels.telegram.allowed-users` | (all) | `TELEGRAM_ALLOWED_USERS` | Comma-separated allowed user IDs |
