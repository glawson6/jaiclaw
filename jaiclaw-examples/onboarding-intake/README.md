# Onboarding Intake

AI-guided employee onboarding with Drools validation rules.

## Problem

HR collects new-hire information through forms that are often incomplete or contain typos. Back-and-forth corrections delay onboarding by days. Rigid forms frustrate users who enter data in natural formats ("john at company dot com").

## Solution

An AI agent conducts a natural conversation to collect employee details one field at a time, while Drools validation rules check each field deterministically (email format, phone digits, age range). The LLM handles messy inputs and translates rule errors into friendly guidance.

## Architecture

```
User Message
    │
    ▼
┌─────────────────┐
│  Onboarding     │
│  Agent (LLM)    │──── Conversational data collection
│                 │
└───────┬─────────┘
        │
        ▼
┌─────────────────┐     ┌─────────────────────┐
│  rules_execute  │────▶│  Drools Validation   │
│  (validation)   │◀────│  Rules Engine        │
└───────┬─────────┘     └─────────────────────┘
        │
        ▼ (all fields valid)
┌─────────────────┐
│ save_onboarding │──── Store employee record
└─────────────────┘
```

**Key classes:**
- `OnboardingApplication` — Spring Boot entry point
- `SaveOnboardingTool` — stores validated employee records in memory
- `OnboardingStatusTool` — retrieves records by ID, name, or email
- `OnboardingRecord` — immutable record for employee data

## Design

- **Single rule type**: Only `validation` rules are loaded — keeps the Drools session minimal
- **Conversational validation**: The agent validates incrementally as fields are collected, not all at once
- **Friendly error translation**: The LLM converts terse rule errors ("invalid email format") into natural guidance
- **In-memory store**: `ConcurrentHashMap` for simplicity — production would use a database

## Build & Run

### Prerequisites
- Java 21+
- `ANTHROPIC_API_KEY` environment variable

### Build
```bash
./mvnw package -pl :jaiclaw-example-onboarding-intake -am -DskipTests
```

### Run
```bash
ANTHROPIC_API_KEY=your-key java -jar jaiclaw-examples/onboarding-intake/target/jaiclaw-example-onboarding-intake-0.4.0.jar
```

### Verify
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hi, I'\''m starting next Monday. My name is John Doe."}'
```

Expected: The agent greets you, acknowledges your name, and asks for the next field (email).
