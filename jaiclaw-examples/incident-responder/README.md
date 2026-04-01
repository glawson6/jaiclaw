# Incident Responder Example

DevOps incident triage agent — checks service health, queries logs, and proposes remediation (restart, scale), with human approval required for destructive actions and hook-based observability.

## What This Demonstrates

- **Explicit tool loop** with approval gates for destructive operations (restart, scale)
- **BEFORE_TOOL_CALL** hook logging every tool invocation with session and iteration info
- **AFTER_TOOL_CALL** hook tracking remediation actions in a persistent log
- **ToolApprovalHandler** (`ConsoleApprovalHandler`) — interactive Y/N prompt on stdin
- Four incident response tools: `check_service_health`, `query_logs`, `restart_service`, `scale_service`

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│                INCIDENT RESPONDER APP                      │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼───────────────────────────────────────┤
│ Approval         │  ConsoleApprovalHandler (stdin Y/N)      │
├──────────────────┼───────────────────────────────────────┤
│ Plugin           │  IncidentResponderPlugin                 │
│                  │  → 4 tools + observability hooks         │
├──────────────────┼───────────────────────────────────────┤
│ Agent            │  Explicit tool loop (max 15 iters)       │
│                  │  → health → logs → restart/scale         │
├──────────────────┼───────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴───────────────────────────────────────┘

Data flow:
  User ──("api-gateway is down")──► Agent
                                      │
                          ┌───────────┴───────────┐
                          ▼                       ▼
                check_service_health       query_logs
                          │                       │
                          └───────────┬───────────┘
                                      ▼
                              LLM decides action
                          ┌───────────┴───────────┐
                          ▼                       ▼
                  restart_service          scale_service
               [REQUIRES APPROVAL]      [REQUIRES APPROVAL]
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key

## Build & Run

```bash
cd jaiclaw-examples/incident-responder
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
# Report an incident
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "The user-service is reporting high error rates and timeouts. Investigate and fix."}'

# The agent will check health, query logs, then propose remediation.
# Watch the server console — type Y/N when prompted to approve restarts or scaling.

# Health check
curl http://localhost:8080/api/health
```
