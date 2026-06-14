# AgentMind Demo

End-to-end demo of the three AgentMind pillars — **Soul**, **Memory**,
**Tendencies** — and **Persona overlays**. Boots a chat surface that shows
how the pillars layer into the system prompt and how the agent learns
per-user style across turns.

## Problem

You want a JaiClaw agent that:

- Has a consistent voice (Soul).
- Remembers facts about each user across sessions (Memory).
- Notices each user's communication style and adapts to it (Tendencies).
- Can swap on-the-fly into a different persona for a specific session
  (Persona overlays).

These are four separate concerns. Wiring them by hand for every project
is repetitive and easy to get wrong. AgentMind ships them as opt-in
pillars with stable SPIs.

## Solution

The demo wires all three pillars + persona overlays via
`application.yml` flags. No code changes — just configuration. The
agent runtime composes the system prompt from the layered Soul,
splices Memory + Tendencies under `<memory>…</memory>` and
`<tendencies-context>…</tendencies-context>` fences, and lets the user
agent call the `personality` tool to switch the active overlay.

## Architecture

```
                          ┌──────────────────────────────────────┐
                          │  AgentMindDemoApplication (Spring)   │
                          └──────────────┬───────────────────────┘
                                         │
                  ┌──────────────────────┼───────────────────────────┐
                  ▼                      ▼                           ▼
       ┌──────────────────┐   ┌───────────────────┐    ┌──────────────────────┐
       │  jaiclaw-        │   │  jaiclaw-         │    │  jaiclaw-            │
       │  agentmind-soul  │   │  agentmind-memory │    │  agentmind-          │
       │                  │   │                   │    │  tendencies          │
       │ - SoulProvider   │   │ - MemoryProvider  │    │ - TendenciesProvider │
       │ - PromptInjector │   │ - PromptInjector  │    │ - PromptInjector     │
       │ - personality    │   │ - memory tool     │    │ - LearningProvider   │
       │   agent tool     │   │                   │    │   (deterministic)    │
       │ - Persona files  │   │                   │    │                      │
       └────────┬─────────┘   └─────────┬─────────┘    └──────────┬───────────┘
                │                       │                         │
                └───────────────────────┼─────────────────────────┘
                                        ▼
                          ┌──────────────────────────────┐
                          │  SystemPromptBuilder          │
                          │  ────────────────────────     │
                          │  [identity]                   │
                          │  [tenant Soul, if enabled]    │
                          │  [agent Soul]                 │
                          │  [active persona overlay]     │
                          │  [behaviour preamble]         │
                          │  [tools / skills]             │
                          │                               │
                          │  user msg:                    │
                          │  <memory>…</memory>           │
                          │  <tendencies-context>…</…>    │
                          │  [user content]               │
                          └──────────────────────────────┘
```

Key classes:

- `AgentMindDemoApplication` — Spring Boot entry point. Just `@SpringBootApplication`;
  all wiring is from auto-configs.
- `PersonaSeeder` — copies bundled persona markdown files from
  `classpath:/personas/*.md` into the configured persona directory at startup
  so the `PersonaOverlayManager` finds them. Idempotent — never overwrites
  user-authored personas.

## Design

- **Three opt-in flags** — `jaiclaw.agentmind.{soul,memory,tendencies}.enabled=true`.
  Any single pillar can be disabled to show the additive layering shrink.
- **Empty sections are omitted entirely** from the system prompt — a Soul or
  Memory with no content does not emit a placeholder header. Preserves
  prefix-cache stability when an operator introduces an overlay mid-deployment.
- **In-process state** for the demo: Memory and Tendencies persist to
  `~/.jaiclaw/agentmind/{memory,tendencies}` as JSON files. No database
  required.
- **Deterministic tendencies learner** by default — no external service.
  Replace with `provider=local-llm` for an LLM-backed learner, or add the
  `jaiclaw-tendencies-honcho` sub-module + `provider=honcho` for the
  Honcho-compatible remote provider.
- **5 curated personas** ship under `src/main/resources/personas/`. Drop a
  new `.md` file into the runtime persona dir and the next reload picks
  it up.

## Build & Run

### Prerequisites

- Java 21 + Maven wrapper (`./mvnw`).
- `ANTHROPIC_API_KEY` set to a valid Anthropic key.

### Build

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw install -pl :jaiclaw-example-agentmind-demo -am -DskipTests
```

### Run

```bash
ANTHROPIC_API_KEY=sk-ant-... \
  ./mvnw spring-boot:run -pl :jaiclaw-example-agentmind-demo
```

### Verify it's working

```bash
# 1. First turn — Memory + Tendencies start empty.
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"sess-1","message":"Hi, I prefer concise bullet replies. My team uses ESM not CJS."}'

# 2. Second turn — Memory + Tendencies should now influence the answer.
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"sess-1","message":"How do I structure a new Node project?"}'

# 3. Switch persona via the agent tool.
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"sess-1","message":"Use the personality tool to switch to the pirate persona, then summarize how I should structure my Node project."}'

# 4. List available personas.
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"sess-1","message":"Use the personality tool with action=list."}'
```

You should see:

- The Spring Boot startup log show `agentmind-soul-enabled` and equivalent
  marker beans for memory + tendencies.
- The `PersonaSeeder` log line `Seeded persona <name>.md → …` for each of
  the five bundled personas on first boot.
- The third response take on the pirate voice while still answering the
  technical question correctly.

Memory + tendencies persist under `~/.jaiclaw/agentmind/`. Delete those
directories to reset.
