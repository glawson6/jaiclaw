# Code Review Bot Example

GOAP-orchestrated code review — an Embabel agent analyses diffs, identifies issues, and generates structured review feedback.

## What This Demonstrates

- **Embabel GOAP** agent with multi-step planning (analyzeDiff → generateReview)
- **@Agent**, **@Action**, **@AchievesGoal** annotations
- Domain objects as blackboard state (DiffAnalysis, ReviewComplete)
- **Canvas** module for HTML dashboard rendering
- **JClawPlugin** SPI for tool registration (GetDiffTool)
- LLM structured extraction with `createObject()`

## Architecture

Where this example fits in JClaw:

```
┌───────────────────────────────────────────────────────────┐
│                   CODE REVIEW BOT APP                      │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Rendering        │  [jclaw-canvas] → HTML review dashboard │
├──────────────────┼────────────────────────────────────────┤
│ Orchestration    │  [Embabel GOAP] → multi-step planning   │
│                  │  analyzeDiff → generateReview            │
├──────────────────┼────────────────────────────────────────┤
│ Plugin SPI       │  [jclaw-plugin-sdk] → GetDiffTool       │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  User ──("review PR #42")──► Embabel AgentPlatform
                                    │
                            ┌───────┼───────┐
                            ▼       ▼       ▼
                      GetDiffTool  LLM   Canvas
                            │       │       │
                            ▼       ▼       ▼
                      DiffAnalysis ──► ReviewComplete
                                        │
                                        ▼
                              HTML review dashboard
```

## Prerequisites

- Java 21+
- JClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI API key (Embabel works best with capable models)

## Build & Run

```bash
cd jclaw-examples/code-review-bot
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Ask the bot to review a PR
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "Review the code diff from repo acme/backend PR #42"}'

# Health check
curl http://localhost:8080/api/health
```
