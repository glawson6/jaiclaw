# Research Assistant Example

Multi-source research agent — searches for sources, fetches articles, saves findings, and generates structured reports, with automatic context compaction for long-running research sessions.

## What This Demonstrates

- **Explicit tool loop** with up to 30 iterations for deep multi-turn research
- **Context compaction** (`jaiclaw-compaction`) — automatically summarizes conversation history when the context window exceeds 50,000 tokens
- **BEFORE_COMPACTION / AFTER_COMPACTION** hooks for observability
- **Workspace memory** (`jaiclaw-memory`) — findings persist across tool calls within a session
- Four research tools: `search_sources`, `fetch_article`, `save_finding`, `generate_report`

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│                RESEARCH ASSISTANT APP                      │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼───────────────────────────────────────┤
│ Compaction       │  [jaiclaw-compaction]                     │
│                  │  → triggers at 50k tokens, 50% target    │
├──────────────────┼───────────────────────────────────────┤
│ Plugin           │  ResearchAssistantPlugin                 │
│                  │  → 4 tools + compaction hooks             │
├──────────────────┼───────────────────────────────────────┤
│ Agent            │  Explicit tool loop (max 30 iters)       │
│                  │  → search → fetch → save → report        │
├──────────────────┼───────────────────────────────────────┤
│ Memory           │  [jaiclaw-memory] workspace store         │
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴───────────────────────────────────────┘

Data flow:
  User ──("research quantum computing")──► Agent
                                             │
                                ┌────────────┤ (repeated)
                                ▼            ▼
                        search_sources  fetch_article
                                │            │
                                ▼            ▼
                          save_finding × N
                                │
                        [COMPACTION if needed]
                                │
                                ▼
                        generate_report
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key

## Build & Run

```bash
cd jaiclaw-examples/research-assistant
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
# Start a research session
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "Research the current state of quantum computing. Search for sources, read key articles, save your findings, and generate a report."}'

# Ask a follow-up (same session — findings accumulate)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "Now research quantum error correction specifically and add those findings to the report."}'

# Health check
curl http://localhost:8080/api/health
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `jaiclaw.compaction.trigger-threshold` | `50000` | Token count that triggers compaction |
| `jaiclaw.compaction.target-token-percent` | `50` | Target percentage of tokens after compaction |
