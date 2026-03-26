# Helpdesk Bot Example

Multi-tenant support bot with JWT/API-key authentication and per-tenant session isolation.

## What This Demonstrates

- **Gateway** REST API for chat integration
- **Security** module with API key and JWT authentication
- **Multi-tenancy** with per-tenant session isolation
- Custom **ToolCallback** implementations (FaqTool, TicketTool)
- Tenant-aware tool behavior via ToolContext

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                    HELPDESK BOT APP                        │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Security         │  [jaiclaw-security] → JWT / API-key auth  │
├──────────────────┼────────────────────────────────────────┤
│ Multi-tenancy    │  TenantContext → per-tenant sessions    │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Custom Tools     │  [FaqTool] [TicketTool]                 │
│                  │  (tenant-aware via ToolContext)          │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  Client ──(JWT / API key)──► Security filter
                                    │
                             TenantContext set
                                    │
                                    ▼
                              AgentRuntime
                                    │
                           ┌────────┼────────┐
                           ▼        ▼        ▼
                       FaqTool  TicketTool   LLM
                       (tenant   (tenant      │
                        FAQ DB)   tickets)    │
                           │        │         │
                           └────────┼─────────┘
                                    ▼
                             Response (JSON)
                          (isolated per tenant)
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI/Gemini API key OR Ollama running locally

## Build & Run

```bash
cd jaiclaw-examples/helpdesk-bot
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Ask a support question (uses default API key)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "How do I reset my password?"}'

# Create a ticket for a complex issue
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "My account was charged twice for the same subscription. Order #12345."}'

# Health check
curl http://localhost:8080/api/health
```
