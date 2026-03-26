# Daily Briefing Example

Scheduled morning briefing that fetches news and weather, then sends a digest via Telegram and Email.

## What This Demonstrates

- **CronJobManager** for scheduled task execution (7 AM weekdays)
- Custom **ToolCallback** implementations (WeatherTool, NewsTool)
- Multi-channel delivery (Telegram + Email)
- Custom skill definition

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                    DAILY BRIEFING APP                      │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Channels         │  [jaiclaw-channel-telegram]               │
│                  │  [jaiclaw-channel-email]                   │
├──────────────────┼────────────────────────────────────────┤
│ Scheduling       │  [jaiclaw-cron] → 7 AM weekdays           │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Custom Tools     │  [WeatherTool]  [NewsTool]              │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  CronService ──(7 AM)──► AgentRuntime
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
              WeatherTool  NewsTool    LLM
                    │         │         │
                    └─────────┼─────────┘
                              ▼
                     Briefing digest
                    ┌─────────┼─────────┐
                    ▼                   ▼
               Telegram              Email
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI/Gemini API key OR Ollama running locally

## Configuration

Required environment variables:

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | Anthropic API key (or use `OPENAI_API_KEY` / Ollama) |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token (optional — for Telegram delivery) |
| `EMAIL_SMTP_HOST` | SMTP server (optional — for Email delivery) |

## Build & Run

```bash
cd jaiclaw-examples/daily-briefing
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Trigger a briefing manually via chat
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Generate today'\''s briefing for New York"}'

# Health check
curl http://localhost:8080/api/health
```

The cron job runs automatically at 7 AM ET on weekdays. Use the chat endpoint to trigger manually.
