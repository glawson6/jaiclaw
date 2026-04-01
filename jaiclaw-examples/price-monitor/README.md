# Price Monitor Example

Periodic price checker — a cron job scrapes product pages every hour and sends SMS alerts when prices drop below target.

## What This Demonstrates

- **CronJobManager** for hourly price checking
- **Browser** module integration (Playwright-based scraping, simulated in this example)
- **SMS** channel for alert delivery via Twilio
- Custom **ToolCallback** (PriceCheckTool)

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                    PRICE MONITOR APP                       │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Channels         │  [jaiclaw-channel-sms] → Twilio alerts    │
├──────────────────┼────────────────────────────────────────┤
│ Scheduling       │  [jaiclaw-cron] → hourly price checks     │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Browser          │  [jaiclaw-browser] → Playwright scraping  │
├──────────────────┼────────────────────────────────────────┤
│ Custom Tools     │  [PriceCheckTool]                       │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  CronService ──(hourly)──► AgentRuntime
                                 │
                        ┌────────┼────────┐
                        ▼        ▼        ▼
                  PriceCheckTool Browser  LLM
                        │        │        │
                        └────────┼────────┘
                                 ▼
                          Price < target?
                           ┌─────┴─────┐
                           ▼           ▼
                       SMS alert    No action
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI/Gemini API key OR Ollama running locally
- Twilio account (optional — for SMS alerts)

## Configuration

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | Anthropic API key (or use `OPENAI_API_KEY` / Ollama) |
| `TWILIO_ACCOUNT_SID` | Twilio account SID (optional) |
| `TWILIO_AUTH_TOKEN` | Twilio auth token (optional) |
| `TWILIO_FROM_NUMBER` | Twilio phone number (optional) |

## Build & Run

```bash
cd jaiclaw-examples/price-monitor
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


## Testing It

```bash
# Manually trigger a price check
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Check the price of MacBook Pro at https://apple.com/macbook-pro with a target price of $1999"}'

# Health check
curl http://localhost:8080/api/health
```
