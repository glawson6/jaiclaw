# Procurement Approval

AI procurement intake with Drools text-analysis, validation, and decision rules for approval workflows.

## Problem

Procurement requests vary by amount, urgency, and justification quality but all go through the same slow queue. Small purchases under $100 should auto-approve; urgent requests need escalation. Manually classifying and routing each request wastes time.

## Solution

An AI agent extracts intent from natural language ("I need 5 DataDog licenses ASAP"), identifies urgency and category via text-analysis rules, validates vendor contacts, and applies decision rules for deterministic approval thresholds. All three non-tax rule types work together in a single workflow.

## Architecture

```
Procurement Request (natural language)
    │
    ▼
┌──────────────────┐
│  Procurement     │
│  Agent (LLM)     │──── Extract details, ask for missing info
└──────┬───────────┘
       │
       ├──▶ rules_execute(text-analysis) ──▶ urgency, category
       │
       ├──▶ rules_execute(validation) ────▶ vendor email/phone check
       │
       ├──▶ rules_execute(decision) ──────▶ APPROVED / PENDING / ESCALATE
       │
       ▼
┌──────────────────┐
│ submit_procurement│──── Store with decision + routing
└──────────────────┘
```

**Key classes:**
- `ProcurementApplication` — Spring Boot entry point
- `SubmitProcurementTool` — stores processed requests with decisions
- `ProcurementStatusTool` — check status by request ID
- `ProcurementListTool` — list requests filtered by status/priority
- `ProcurementRequest` — immutable record for request data

## Design

- **Three rule types in one workflow**: text-analysis (understand justification) → validation (check vendor contacts) → decision (approve/escalate). The agent orchestrates the chain.
- **Natural language extraction**: The LLM parses "5 DataDog licenses, $500, contact vendor@datadog.com" into structured fields
- **Deterministic thresholds**: Under $100 auto-approves, $100-$1000 needs manager, over $1000 needs VP — enforced by rules, not the LLM
- **In-memory store**: `ConcurrentHashMap` for simplicity

## Build & Run

### Prerequisites
- Java 21+
- `ANTHROPIC_API_KEY` environment variable

### Build
```bash
./mvnw package -pl :jaiclaw-example-procurement-approval -am -DskipTests
```

### Run
```bash
ANTHROPIC_API_KEY=your-key java -jar jaiclaw-examples/procurement-approval/target/jaiclaw-example-procurement-approval-0.4.0.jar
```

### Verify
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"I need 5 DataDog licenses for the platform team, about $500. Contact vendor@datadog.com"}'
```

Expected: The agent analyzes the request, validates the email, applies decision rules (amount $500 → needs manager approval), stores the request, and returns a request ID with PENDING_APPROVAL status.
