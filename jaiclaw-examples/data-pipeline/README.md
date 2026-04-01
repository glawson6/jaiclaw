# Data Pipeline Example

ETL pipeline orchestrator — an explicit tool loop agent that validates schemas, runs transformations, previews data, and loads results, with a full audit trail via hooks and human-in-the-loop approval for destructive operations.

## What This Demonstrates

- **Explicit tool loop** with human approval gates for destructive operations
- **BEFORE_TOOL_CALL / AFTER_TOOL_CALL** hooks building a timed audit trail
- **AGENT_END** hook printing a pipeline execution summary
- **ToolApprovalHandler** (`ConsoleApprovalHandler`) — interactive Y/N prompt on stdin
- Four ETL tools: `validate_schema`, `run_transform`, `preview_data`, `load_data`

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│                  DATA PIPELINE APP                         │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼───────────────────────────────────────┤
│ Approval         │  ConsoleApprovalHandler (stdin Y/N)      │
├──────────────────┼───────────────────────────────────────┤
│ Plugin           │  DataPipelinePlugin                      │
│                  │  → 4 tools + audit hooks + summary       │
├──────────────────┼───────────────────────────────────────┤
│ Agent            │  Explicit tool loop (max 20 iters)       │
│                  │  → validate → transform → preview → load │
├──────────────────┼───────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴───────────────────────────────────────┘

Data flow:
  User ──("migrate users table")──► Agent
                                      │
                          ┌───────────┴───────────┐
                          ▼                       ▼
                  validate_schema          run_transform
                          │                       │
                          ▼                       ▼
                    preview_data            load_data
                                         [REQUIRES APPROVAL]
                                               │
                                               ▼
                                    AUDIT SUMMARY printed
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key

## Build & Run

```bash
cd jaiclaw-examples/data-pipeline
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
# Run an ETL pipeline
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "Migrate the users dataset to the new users_v2 table. Validate schema, clean the data, preview it, then load."}'

# The agent will pause and prompt on stdin for approval before load_data runs.
# Check the server console and type Y/N when prompted.

# Health check
curl http://localhost:8080/api/health
```
