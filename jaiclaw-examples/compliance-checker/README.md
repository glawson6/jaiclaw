# Compliance Checker Example

GOAP document compliance — an Embabel agent ingests policy documents, extracts rules, and checks target documents against them with full audit trails.

## What This Demonstrates

- **Embabel GOAP** agent (extractPolicy → checkCompliance)
- **Documents** module for PDF/HTML document parsing
- **Audit** module for compliance audit trail
- Domain records as blackboard state (PolicyDocument → ComplianceReport)
- LLM structured extraction with `createObject()`

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                  COMPLIANCE CHECKER APP                     │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Audit Trail      │  [jaiclaw-audit] → compliance log         │
├──────────────────┼────────────────────────────────────────┤
│ Orchestration    │  [Embabel GOAP] → multi-step checking   │
│                  │  extractPolicy → checkCompliance         │
├──────────────────┼────────────────────────────────────────┤
│ Document Parsing │  [jaiclaw-documents] → PDF/HTML ingestion │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  User ──("check GDPR")──► Embabel AgentPlatform
                                  │
                         ┌────────┴────────┐
                         ▼                 ▼
                  extractPolicy     Documents module
                    (LLM)           (PDF/HTML parse)
                         │                 │
                         ▼                 ▼
                   PolicyRules ────► checkCompliance
                                         │
                                    ┌────┴────┐
                                    ▼         ▼
                             ComplianceReport  AuditLog
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI API key (Embabel works best with capable models)

## Build & Run

```bash
cd jaiclaw-examples/compliance-checker
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Check a document for GDPR compliance
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Check our privacy policy against GDPR compliance requirements. The policy should cover data collection, user consent, right to deletion, and data breach notification."}'

# Health check
curl http://localhost:8080/api/health
```
