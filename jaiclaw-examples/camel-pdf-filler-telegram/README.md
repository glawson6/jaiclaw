# Camel PDF Filler (Telegram)

## Problem

Automated PDF form filling works well when JSON fields map cleanly to PDF form fields. But when the JSON contains fields the PDF doesn't have (or vice versa), you need a human to decide how to handle the mismatches. You want the pipeline to pause, ask for guidance, and resume — without losing progress.

## Solution

This example extends the automated PDF filler with a human-in-the-loop via Telegram. When the LLM can't confidently map all JSON fields to PDF form fields, it sends a question to the user on Telegram listing the unmapped fields and available PDF fields. The user replies with mapping instructions, the agent processes the corrections, and the PDF is filled with the complete mapping.

## Architecture

```
JSON file (data/inbox/)
        |
  JsonIngestRoute (file watcher)
        |
  seda:jaiclaw-pdf-filler-telegram-in
        |
  CamelChannelAdapter -> AgentRuntime
        |
  LLM maps fields + identifies unmapped fields
        |
  seda:jaiclaw-pdf-filler-telegram-out
        |
  TelegramPdfOutputRoute
        |
  +--- All fields mapped? ---+
  | YES                       | NO
  |                           |
  Fill PDF ->              Send question to Telegram
  ArtifactStore.save()        |
  + outbox/ + notify       User replies with corrections
                              |
                         Agent processes corrections
                              |
                         Fill PDF -> ArtifactStore.save()
                         + outbox/ + notify on Telegram
```

### Key Classes

| Class | Role |
|-------|------|
| `CamelPdfFillerTelegramApp` | Spring Boot entry point |
| `JsonIngestRoute` | Camel RouteBuilder - watches inbox for `.json` files |
| `TelegramPdfOutputRoute` | Camel RouteBuilder - fills PDF or sends clarification to Telegram |
| `TemplateManager` | Loads and caches PDF template + field metadata |
| `SampleFormGenerator` | Creates sample PDF form template at startup if absent |

## Design

**Stateful channel:** Unlike the automated example (stateless), this uses `stateless: false` so the conversation context is preserved. When the LLM asks a clarification question, the user's Telegram reply flows back through the same agent session, and the LLM has the full context of the original mapping attempt.

**Two-phase LLM interaction:**
1. Phase 1: LLM maps fields and identifies ambiguities. If `clarificationNeeded: true`, the question is forwarded to Telegram.
2. Phase 2: User replies on Telegram, agent processes corrections and returns final complete mapping.

**Telegram notifications:** Completion notifications are sent to Telegram whether or not clarification was needed, so the user always knows when their PDF is ready.

**Same test data, extra fields:** The included `test-data.json` has `company` and `department` fields that don't exist in the sample PDF form, triggering the clarification flow.

## Build & Run

### Prerequisites

- Java 21+
- An Anthropic API key
- A Telegram bot token (from @BotFather) and chat ID

### Environment Variables

```bash
# Required
export ANTHROPIC_API_KEY=sk-your-key-here
export TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
export TELEGRAM_CHAT_ID=your-chat-id

# Optional — PDF template (the unfilled form to use as a template)
# If the file doesn't exist, a sample 8-field form is generated automatically
export APP_TEMPLATE=file:/path/to/your-form.pdf   # default: file:target/data/templates/sample-form.pdf

# Optional — other overrides
export AI_PROVIDER=anthropic
export ANTHROPIC_MODEL=claude-haiku-4-5
```

### Build

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# From the jaiclaw root:
./mvnw package -pl :jaiclaw-example-camel-pdf-filler-telegram -am -DskipTests
```

### Run

```bash
mkdir -p target/data/inbox

TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... \
java -jar jaiclaw-examples/camel-pdf-filler-telegram/target/jaiclaw-example-camel-pdf-filler-telegram-0.1.0-SNAPSHOT.jar
```

### Verify

1. Drop the test JSON into the inbox:

```bash
cp jaiclaw-examples/camel-pdf-filler-telegram/src/test/resources/test-data.json target/data/inbox/
```

2. The agent maps most fields but finds `company` and `department` unmapped. It sends a clarification question to your Telegram chat.

3. Reply on Telegram with instructions (e.g., "Ignore company and department, they're not needed for this form").

4. The agent processes your reply, fills the PDF with the resolved mapping, writes it to `target/data/outbox/`, and sends a completion notification on Telegram.
