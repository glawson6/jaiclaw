# Sales Report Example

Weekly sales dashboard — a cron job triggers data collection and the agent renders an HTML report via Canvas.

## What This Demonstrates

- **CronJobManager** for weekly scheduled reports (Monday 9 AM)
- Custom **ToolCallback** (SalesFetchTool) for data retrieval
- **Canvas** module for HTML artifact rendering
- Custom skill definition

## Architecture

Where this example fits in JClaw:

```
┌───────────────────────────────────────────────────────────┐
│                    SALES REPORT APP                        │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Rendering        │  [jclaw-canvas] → HTML report artifact  │
├──────────────────┼────────────────────────────────────────┤
│ Scheduling       │  [jclaw-cron] → Monday 9 AM             │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Custom Tools     │  [SalesFetchTool]                       │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  CronService ──(Mon 9AM)──► AgentRuntime
                                  │
                         ┌────────┼────────┐
                         ▼        ▼        ▼
                   SalesFetchTool LLM    Canvas
                         │        │        │
                         └────────┼────────┘
                                  ▼
                         HTML sales report
```

## Prerequisites

- Java 21+
- JClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI API key OR Ollama running locally

## Configuration

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | Anthropic API key (or use `OPENAI_API_KEY` / Ollama) |

## Build & Run

```bash
cd jclaw-examples/sales-report
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Generate a sales report on demand
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "Generate a weekly sales report for the North America region"}'

# Health check
curl http://localhost:8080/api/health
```
