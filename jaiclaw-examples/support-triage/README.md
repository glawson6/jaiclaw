# Support Triage

AI support triage with Drools text-analysis and decision rules for deterministic routing.

## Problem

Support teams receive hundreds of messages daily. Manually classifying sentiment, routing to teams, and assigning priority is slow and inconsistent. Different agents make different routing decisions for similar messages.

## Solution

An AI agent uses Drools text-analysis rules to detect sentiment and categories, then chains into decision rules for deterministic routing. The LLM understands nuanced free-text (sarcasm, implied urgency) while the rules engine enforces consistent policies ("critical + technical = Tier-2 Engineering"). Routing policies are editable DRL files, not LLM prompts.

## Architecture

```
Support Message
    │
    ▼
┌──────────────────┐
│  Triage Agent    │
│  (LLM)           │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐     ┌──────────────────────┐
│  rules_execute   │────▶│  Drools Text-Analysis │
│  (text-analysis) │◀────│  Rules Engine         │
└──────┬───────────┘     └──────────────────────┘
       │ sentiment, keywords, categories
       ▼
┌──────────────────┐     ┌──────────────────────┐
│  rules_execute   │────▶│  Drools Decision      │
│  (decision)      │◀────│  Rules Engine         │
└──────┬───────────┘     └──────────────────────┘
       │ priority, team, decision
       ▼
┌──────────────────┐
│  store_ticket    │──── Persist triage result
└──────────────────┘
```

**Key classes:**
- `SupportTriageApplication` — Spring Boot entry point
- `TicketStoreTool` — stores triaged tickets with routing decisions
- `TriageSummaryTool` — lists recent tickets with priority/team filters
- `TriageRecord` — immutable record for triage results

## Design

- **Two-phase rule chaining**: Text-analysis first (understand the message), then decision rules (route it). The agent bridges the two.
- **LLM as interpreter**: The agent maps free-form sentiment results to structured decision parameters — something rules alone can't do well
- **Deterministic routing**: Same priority + type always routes to the same team, regardless of LLM variability
- **In-memory store**: `ConcurrentHashMap` for simplicity

## Build & Run

### Prerequisites
- Java 21+
- `ANTHROPIC_API_KEY` environment variable

### Build
```bash
./mvnw package -pl :jaiclaw-example-support-triage -am -DskipTests
```

### Run
```bash
ANTHROPIC_API_KEY=your-key java -jar jaiclaw-examples/support-triage/target/jaiclaw-example-support-triage-0.4.0.jar
```

### Verify
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"I'\''ve been waiting 3 days and your API keeps returning 500 errors. This is terrible."}'
```

Expected: The agent analyzes sentiment (negative), categorizes (technical), assigns high/critical priority, routes to Engineering, and returns a ticket ID.
