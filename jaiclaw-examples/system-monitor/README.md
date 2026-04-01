# System Monitor Example

Gateway with embedded cron manager — runs whitelisted Linux commands on a daily schedule, analyzes the results with an LLM, and sends a formatted health report to Telegram.

## What This Demonstrates

- **Composable app assembly** — combines `jaiclaw-starter-gateway` + `jaiclaw-starter-cron` in one app
- **Cron job scheduling** (`jaiclaw-cron-manager`) — daily trigger with H2 persistence
- **Whitelisted command execution** — 20 safe Linux commands with timeout and output limits
- **MINIMAL tool profile** — agent only sees monitoring tools, not the full tool registry
- **Telegram channel integration** — sends formatted reports via `SendTelegramTool`
- **Bundled skill filtering** — only the `system-monitor` skill is enabled

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│                  SYSTEM MONITOR APP                        │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Gateway          │  REST API + WebSocket + webhooks        │
├──────────────────┼───────────────────────────────────────┤
│ Cron             │  [jaiclaw-cron-manager]                   │
│                  │  → daily at 7 AM (configurable)          │
│                  │  → H2 persistence across restarts        │
├──────────────────┼───────────────────────────────────────┤
│ Agent            │  Spring AI tool loop (MINIMAL profile)   │
│                  │  → show_system_commands                   │
│                  │  → system_command (whitelisted)           │
│                  │  → send_telegram                          │
├──────────────────┼───────────────────────────────────────┤
│ Channel          │  [jaiclaw-channel-telegram]               │
│                  │  → Bot API polling (or webhook)           │
├──────────────────┼───────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴───────────────────────────────────────┘

Cron-triggered flow (daily at 7 AM):
  Cron Manager ──(prompt)──► Agent
                               │
                   ┌───────────┤
                   ▼           ▼
          show_system     system_command
          _commands          × N
              │           (uptime, free,
              │            df, top, etc.)
              │                │
              └────────┬───────┘
                       ▼
              LLM formats report
                       │
                       ▼
               send_telegram
            (to configured chat)
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key
- Telegram bot token + chat ID (for report delivery)

## Build & Run

```bash
cd jaiclaw-examples/system-monitor
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... \
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
TELEGRAM_CHAT_ID=your-chat-id \
../../mvnw spring-boot:run
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


## Testing It

```bash
# Trigger a health report manually via chat
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Run a system health check and send the report to Telegram"}'

# Health check
curl http://localhost:8080/api/health

# List channels
curl http://localhost:8080/api/channels
```

The cron job also fires automatically at the configured schedule (default: 7 AM daily).

## Configuration

| Property | Default | Env Var | Description |
|----------|---------|---------|-------------|
| `sysmonitor.schedule` | `0 7 * * *` | `MONITOR_SCHEDULE` | Cron expression for health reports |
| `sysmonitor.timezone` | `America/New_York` | `MONITOR_TIMEZONE` | Timezone for the cron schedule |
| `sysmonitor.telegram.chat-id` | (required) | `TELEGRAM_CHAT_ID` | Telegram chat ID for report delivery |
