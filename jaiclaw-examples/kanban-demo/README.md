# JaiClaw Kanban Demo

Minimal Spring Boot app that exercises every Phase 1–3 surface of the
`jaiclaw-kanban` module against a single fixture board. The `.claude/skills/kanban-e2e`
skill drives this app out-of-process to validate the kanban story end-to-end.

## Problem

The `jaiclaw-kanban` module ships ~145 unit + integration specs across
five phases of work (Core → REST/SSE → MCP/Actuator → Processing/SSM → Hardening).
Those specs catch internal regressions but they don't catch the *consumer*
experience: does a clean Spring Boot app booted with `jaiclaw-starter-kanban`
actually expose a usable REST + SSE + Actuator surface, fire its column
processor on card entry, and survive a restart-recovery? Regressions in the
autoconfig wiring or in the property naming would land green on `./mvnw test`
and still break every user.

## Solution

This example is the minimum surface area needed to exercise every
plan-§9 Definition-of-Done item against a real Spring Boot context:

- **REST + SSE** — `KanbanBoardController` + `KanbanEventController`
  exposed under `/api/kanban/...`
- **Actuator** — `KanbanActuatorEndpoint` under `/actuator/kanban`
- **Column processor** — a fixture board with a `drafting` column whose
  processor fires a stub `kanbanAgentRunner` bean on entry. No LLM key
  required — the runner returns `"DRAFT: {card name}"`
- **Idempotency** — the processor's recorded result is written to the
  `EffectLedger` so a crash-replay returns the cached output without a
  second runner call
- **Recovery** — `KanbanRecoveryManager` is enabled, so a card stuck in
  `drafting` at startup follows the column's `restartPolicy` (`requeue`
  with `maxAttempts=3`)
- **ASCII rendering** — `GET /api/kanban/boards/demo/ascii` produces
  text-plain output the e2e skill byte-compares

The fixture board ships in the classpath at
`src/main/resources/jaiclaw/kanban/boards/demo.yaml` and is loaded via
`jaiclaw.kanban.locations.patterns`.

## Architecture

```
HTTP / SSE client (e2e skill, curl, browser)
   │
   ├─ POST /api/kanban/boards/demo/tasks              create a card
   ├─ POST /api/kanban/tasks/{id}/transition          fire START / SUBMIT / APPROVE
   ├─ GET  /api/kanban/boards/demo/snapshot           JSON snapshot
   ├─ GET  /api/kanban/boards/demo/ascii?style=full   text/plain rendering
   ├─ GET  /api/kanban/boards/demo/events             text/event-stream (SSE)
   └─ GET  /actuator/kanban                           Spring Boot Actuator
   ▼
┌─────────────────────────────┐
│ KanbanBoardController       │  Spring Web
│ KanbanEventController       │  text/event-stream (SSE)
│ KanbanActuatorEndpoint      │  @Endpoint(id="kanban")
└─────────────────────────────┘
            │
            ▼
┌─────────────────────────────┐
│ TaskTransitionService       │  Phase 1 — engine + history + events
│ KanbanBoardService          │  YAML-backed (Phase 2 §9 Q1 resolution)
│ TransitionGraphStateEngine  │  default; SSM available behind a property
└─────────────────────────────┘
            │
            ├── publish TaskStateChanged ──► SSE fan-out
            └──────► ColumnProcessorManager
                         │
                         ▼
                    ┌─────────────────────────┐
                    │ AgentColumnProcessor    │
                    │  • renders {{name}}, etc│
                    │  • consults EffectLedger│
                    │  • invokes runner       │
                    └─────────────────────────┘
                                 │
                                 ▼
                    kanbanAgentRunner (this demo's stub bean)
                    returns "DRAFT: {card name}"
```

Key classes:

| Class | Role |
|---|---|
| `KanbanDemoApplication` | `@SpringBootApplication` boot entry |
| `KanbanDemoBeans` | provides `kanbanAgentRunner` (stub) + `TaskStore` |
| `demo.yaml` | classpath fixture board: backlog → drafting → review → done, with `blocked` side state and a processor on `drafting` |
| `application.yml` | enables kanban + recovery + processors; binds the fixture board via `locations.patterns` |

## Design

- **No LLM key required.** The `kanbanAgentRunner` bean is a deterministic
  stub so the demo runs in CI without any provider configuration. The
  Phase 3 `AgentColumnProcessor` still goes through its full path
  (prompt template render → ledger lookup → runner call → ledger record);
  only the runner itself is a stub.
- **`jaiclaw.tasks.enabled: false`.** The kanban autoconfig only requires
  a `TaskStore` bean — we provide one directly via `KanbanDemoBeans` to
  avoid pulling in the full task auto-config stack (which expects a
  `ToolRegistry`). Same shape every kanban integration spec uses.
- **`jaiclaw.skills.allow-bundled: []`.** Repo rule for examples per
  `CLAUDE.md` — keeps prompt token budget sane even though this demo
  doesn't drive an LLM. Configured for hygiene, not because we need it.
- **Storage in `$TMPDIR`.** Boards and tasks live under
  `${java.io.tmpdir}/kanban-demo/` so successive demo runs start clean.
- **Default port 8200.** Picked to avoid collision with `pipeline-e2e`
  (`8100`) and the gateway (`8888`).

## Build & Run

### Prerequisites

- Java 21 (`export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle`)
- Maven (use `./mvnw` from the repo root)

### Build

```bash
./mvnw package -pl :jaiclaw-example-kanban-demo -am -DskipTests -o
```

### Run

```bash
java -jar jaiclaw-examples/kanban-demo/target/jaiclaw-example-kanban-demo-0.8.1-SNAPSHOT.jar
```

Or run the executable JAR directly:

```bash
./mvnw spring-boot:run -pl :jaiclaw-example-kanban-demo
```

The app boots on `http://localhost:8200` with the fixture board
`demo` pre-registered.

### Verify

```bash
# 1. List boards (should include "demo")
curl -s http://localhost:8200/api/kanban/boards | jq .

# 2. Create a card
CARD=$(curl -s -X POST -H 'Content-Type: application/json' \
    http://localhost:8200/api/kanban/boards/demo/tasks \
    -d '{"name":"Q3 Blog Post","description":"Write a long-form post"}')
ID=$(echo "$CARD" | jq -r .id)

# 3. Fire START — the column processor runs the stub agent runner
curl -s -X POST -H 'Content-Type: application/json' \
    http://localhost:8200/api/kanban/tasks/$ID/transition \
    -d '{"event":"START","actor":"e2e"}'

# 4. Snapshot — the card is now in "drafting" with result="DRAFT: Q3 Blog Post"
curl -s http://localhost:8200/api/kanban/boards/demo/snapshot | jq .

# 5. ASCII rendering
curl -s 'http://localhost:8200/api/kanban/boards/demo/ascii?style=compact&width=80'

# 6. Live event stream (Ctrl-C to stop)
curl -N -H 'Accept: text/event-stream' \
    http://localhost:8200/api/kanban/boards/demo/events

# 7. Actuator endpoint
curl -s http://localhost:8200/actuator/kanban | jq .
```

The `.claude/skills/kanban-e2e` skill runs the same sequence
non-interactively and byte-compares the ASCII output against a checked-in
golden file.
