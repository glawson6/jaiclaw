# JaiClaw Kanban Task Processing — Analysis & Extension Design

**Status:** Proposal / analysis
**Repo state analyzed:** `jaiclaw-parent 0.8.1-SNAPSHOT` (Spring Boot 3.5.14, Spring AI 1.1.7, Embabel 0.3.5, Camel 4.18.2)
**Date:** 2026-06-12

---

## 1. Executive Summary

JaiClaw's agent runtime today is **reactive**: a message arrives (chat, channel webhook, WebSocket, cron fire, pipeline trigger) and `AgentRuntime.run()` reacts. There is no notion of a *standing set of work items* that progress through user-defined states toward completion, no transition validation, no transition events, and no board-shaped view of in-flight work.

The good news: **the codebase already contains ~60% of the raw material** for a kanban processing model, and the agent runtime itself needs **zero changes**. The work decomposes into:

| Capability | Exists today? | Where |
|---|---|---|
| Task record + lifecycle status | ✅ Yes | `jaiclaw-tasks` (`TaskRecord`, `TaskStatus`) |
| Async task execution (virtual threads / Camel SEDA) | ✅ Yes | `jaiclaw-tasks` (`TaskExecutor`, `CamelTaskRoute`) |
| Task persistence SPI + JSON store | ✅ Yes | `jaiclaw-tasks` (`TaskStore`, `JsonFileTaskStore`) |
| Agent CRUD tools for tasks | ✅ Yes | `jaiclaw-tasks` (`task_create` … `task_delete`) |
| Execution tracking pattern (bounded, queryable) | ✅ Yes (precedent) | `jaiclaw-pipeline` (`PipelineExecutionTracker`) |
| REST + Actuator exposure pattern | ✅ Yes (precedent) | `jaiclaw-pipeline` (`PipelineTriggerController`, `PipelineActuatorEndpoint`) |
| ASCII rendering engine | ✅ Yes | `jaiclaw-ascii-render` (`Canvas`, `Table`, `AsciiBox`, `AsciiSceneFactory`) |
| Typed event/hook system | ✅ Yes | `jaiclaw-core` (`HookEvent` sealed hierarchy, `HookRunner`) |
| Task records survive restart | ⚠️ Partial | `JsonFileTaskStore` persists on every save — but writes are non-atomic, in-flight work is unrecoverable, no startup recovery sweep (§6) |
| MCP tool exposure pattern | ✅ Yes (precedent) | `jaiclaw-calendar` (`CalendarMcpToolProvider`) |
| **User-defined states / transition graph** | ❌ No | `TaskStatus` is a fixed enum; `TaskService.updateStatus()` accepts any status with no validation |
| **Transition events emitted on state change** | ❌ No | `TaskExecutor` and `TaskService` write to the store silently |
| **Board model (columns, WIP limits, ordering)** | ❌ No | — |
| **Dashboard-facing API (snapshot + live stream)** | ❌ No | — |
| **Board ASCII view** | ❌ No (engine exists, no renderer) | — |
| **Spring State Machine** | ❌ Not a dependency anywhere | — |

**Recommendation in one line:** build a new extension module **`jaiclaw-kanban`** that *depends on and extends* `jaiclaw-tasks`, introduces a `BoardDefinition` + pluggable `TaskStateEngine` SPI (default lightweight transition-graph implementation, optional Spring State Machine implementation behind `@ConditionalOnClass`), emits typed transition events on three planes (Spring `ApplicationEventPublisher`, JaiClaw hooks, SSE), exposes a REST API for a dashboard UI, and renders on-the-fly ASCII boards via `jaiclaw-ascii-render`. Changes to existing code are small, additive, and confined to ~6 files outside the new module.

> ⚠️ **Side finding:** the repo's `CLAUDE.md` is stale relative to the tree — it documents 9 root modules / 18 extensions / Boot 3.5.6 / Embabel 0.3.4, while the tree has 34 extensions (including `jaiclaw-tasks`, `jaiclaw-pipeline`, `jaiclaw-rules`, `jaiclaw-camel`, `jaiclaw-observability`, `jaiclaw-messaging`), `jaiclaw-ascii-render` in core, `jaiclaw-maven-plugin` at root, and Boot 3.5.14 / Embabel 0.3.5. Per the repo's own Documentation Maintenance rule, the kanban work should also patch `CLAUDE.md` and `docs/ARCHITECTURE.md`.

---

## 2. Current-State Analysis

### 2.1 The reactive execution model

`AgentRuntime.run(String userInput, AgentRuntimeContext ctx)` is the single entry point: it appends the user message to the session, resolves tools/prompt (singleton or tenant path), runs the tool loop, fires hooks (`AgentStartedEvent` → `LlmInputEvent` → `LlmOutputEvent` → `AgentEndedEvent`), and returns a `CompletableFuture<AssistantMessage>`. Every existing "proactive" feature drives this same entry point from the outside:

- **Cron** — `CronJobExecutor` wraps the agent behind a `Function<CronJob, String> agentRunner`, deliberately avoiding a compile-time dependency on `AgentRuntime`.
- **Pipeline** — `AgentStageProcessor` invokes the agent as one stage type among `BEAN`/`CAMEL`/`AGENT`.
- **Tasks** — `TaskExecutor.submit(task, Function<TaskRecord, String> handler)` runs an arbitrary handler on a virtual thread (or Camel SEDA consumer) and flips `QUEUED → RUNNING → SUCCEEDED/FAILED` in the store.

This is exactly the right seam for kanban: **the kanban engine is another driver in front of the reactive runtime**, not a modification of it. `AgentRuntimeContext.stateless(true)` already exists for ephemeral executions, which is what per-card agent work wants (each card execution is an isolated session, keyed `kanban:{boardId}:{taskId}`).

### 2.2 What `jaiclaw-tasks` gives us — and where it stops

```java
public enum TaskStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED }

public record TaskRecord(
        String id, String name, String description,
        TaskStatus status, TaskDeliveryState deliveryState,
        String result, String error, String flowId,
        Map<String, String> metadata,
        Instant createdAt, Instant startedAt, Instant completedAt,
        String tenantId) { ... }
```

Strengths to reuse:

- `TaskStore` SPI with `JsonFileTaskStore` (tenant-aware via `TenantGuard`), `findByStatus`, `@JsonIgnoreProperties(ignoreUnknown = true)` on the record → **forward-compatible JSON files**; old stores will deserialize after we add fields.
- `TaskExecutor` dual-mode execution: virtual threads fallback, Camel SEDA (`seda:jaiclaw-tasks?size=100&concurrentConsumers=5`) when Camel is on the classpath — the optional-dependency pattern we should mirror for Spring State Machine.
- `TaskFlow` (ordered `taskIds`) is a primitive pipeline; a kanban board generalizes it.
- Agent tools already registered: `task_create`, `task_list`, `task_get`, `task_update`, `task_delete` — agents can *already* manipulate tasks conversationally; kanban tools extend this vocabulary (`task_move`, `board_show`, `board_ascii`).

Hard gaps (each one is a design item in §3):

1. **Fixed states.** `TaskStatus` is a closed enum. Users cannot define `TRIAGE → IN_PROGRESS → REVIEW → DONE`.
2. **No transition validation.** `TaskService.updateStatus(id, status)` will happily move `SUCCEEDED → QUEUED`. Anything is reachable from anything.
3. **Silent transitions.** Neither `TaskService` nor `TaskExecutor` emits any event — no hook, no Spring event, nothing a dashboard could subscribe to. (Contrast with `jaiclaw-pipeline`, whose `PipelineHookFirer` reports stage starts/completions through `HookRunner`.)
4. **No concurrency control on transitions.** `TaskRecord` has no `version` field; two concurrent `updateStatus` calls last-write-win. Tolerable for JSON-file scale, a real bug once a multi-consumer column processor exists.
5. **No crash recovery.** The execution handler is an in-memory lambda (and Camel-mode submissions live in `CamelTaskRoute.pendingHandlers`, also in-memory); a restart orphans RUNNING/QUEUED records permanently, and `JsonFileTaskStore`'s non-atomic flush + corruption-swallowing load can silently lose the file (§6.2–6.3).
6. **No board/column/WIP concept**, no ordering within a state, no assignee.

### 2.3 Patterns worth copying verbatim

- **`PipelineExecutionTracker`** — bounded per-key `ArrayDeque` + by-id index, synchronized eviction. The kanban transition history store should be a near copy (`TransitionHistory`, last N transitions per board).
- **`PipelineActuatorEndpoint` + `PipelineTriggerController`** — the read/trigger split: Actuator for operator visibility, `@RestController` under a configurable base path for programmatic API, security delegated to the Spring Security filter chain.
- **`CalendarMcpToolProvider`** — the MCP exposure shape (server name, `McpToolDefinition` list with inline JSON schemas, `execute(toolName, args, TenantContext)` switch).
- **`PipelineHookFirer`** — precedent for emitting lifecycle events through `HookRunner` *without touching `jaiclaw-core`*, by mapping onto existing event types (it reports stages as `ToolCallStarted/EndedEvent`). See §5.2 for whether kanban should do the same or add first-class events.
- **`TasksAutoConfiguration`** — `@ConditionalOnProperty("jaiclaw.tasks.enabled")`, `@ConditionalOnBean(ToolRegistry.class)`, `@AutoConfigureAfter(JaiClawAgentAutoConfiguration)`, nested `@Configuration @ConditionalOnClass` for the optional Camel path. The kanban auto-config should be structurally identical (and per the starter rules in `CLAUDE.md`, remember `@ConditionalOnBean` limits within a single auto-config pass).

### 2.4 `jaiclaw-ascii-render` — the ASCII story is mostly solved

The module ships a layered `Canvas`/`Layer`/`Region` engine with `Table`, `Label`, `Rectangle`, `Line`, `Text` elements, an `AsciiBox` factory, and — crucially — `AsciiSceneFactory` + `SceneSpec`, a **data-driven scene description** (the same spec format the `ascii-rendering` skill-pack exposes over MCP). A kanban board is a row of fixed-width boxed columns containing stacked card boxes; this maps directly onto `Rectangle` + `Label` elements positioned in a `Region` per column, or onto a `Table` for the compact variant. **No changes to ascii-render are needed** — the kanban module adds a `BoardAsciiRenderer` that *consumes* it.

### 2.5 The hook system constraint

`HookEvent` is a **sealed interface** with an explicit `permits` list of 16 event types. Adding first-class `TaskStateChangedEvent`/`BoardUpdatedEvent` means editing `jaiclaw-core` (the zero-Spring module) — additive, but it's a core touch, and any exhaustive `switch` over `HookEvent` subtypes anywhere in the codebase becomes a compile error until updated (a quick `grep -r "switch.*HookEvent\|case .*Event ->"` audit is a pre-task). The alternative is the `PipelineHookFirer` mapping trick. §5.2 weighs both.

---

## 3. Proposed Design — `jaiclaw-kanban` Extension

### 3.1 Module placement and shape

```
extensions/jaiclaw-kanban/
  src/main/java/io/jaiclaw/kanban/
    model/        BoardDefinition, ColumnDefinition, TransitionDefinition,
                  CardView, BoardSnapshot, TransitionRecord
    state/        TaskStateEngine (SPI), TransitionGraphStateEngine (default),
                  TransitionResult, StateEngineException
    statemachine/ SpringStateMachineEngine, BoardStateMachineFactory   (optional dep)
    service/      KanbanBoardService, TaskTransitionService, TransitionHistory
    engine/       ColumnProcessorManager, AgentColumnProcessor, ColumnPolicy
    events/       TaskStateChanged, BoardChanged (Spring ApplicationEvents),
                  KanbanHookFirer, KanbanEventStream (SSE broadcaster)
    persistence/  BoardStore (SPI), JsonFileBoardStore
    render/       BoardAsciiRenderer, AsciiBoardOptions
    web/          KanbanBoardController, KanbanEventController (SSE),
                  KanbanWebConfiguration
    actuator/     KanbanActuatorEndpoint
    mcp/          KanbanMcpToolProvider
    tool/         MoveTaskTool, ShowBoardTool, BoardAsciiTool, ClaimTaskTool
    KanbanAutoConfiguration.java
    KanbanProperties.java
jaiclaw-starters/jaiclaw-starter-kanban/pom.xml
```

Dependencies: `jaiclaw-core`, `jaiclaw-tools`, **`jaiclaw-tasks`** (reuses `TaskRecord`/`TaskStore`/`TaskExecutor`), `jaiclaw-ascii-render`, `jackson-databind`, optional `spring-boot-autoconfigure`, optional `spring-web` (REST/SSE only when in a web app), optional `spring-statemachine-core` (≈ 4.0.x for Boot 3 — add `<spring-statemachine.version>` to the root pom; verify against Boot 3.5.14 before committing), optional `jaiclaw-plugin-sdk` (for `HookRunner`, mirroring `jaiclaw-pipeline`).

### 3.2 Board model — user-defined states

States are **strings owned by a board definition**, not enum members. The existing `TaskStatus` enum is retained as a coarse *phase classifier* so all current code keeps working (see §4, Option B).

```yaml
# ~/.jaiclaw/kanban/boards/content-review.yaml
id: content-review
name: Content Review Board
tenantIds: []                 # empty = all tenants (same convention as PipelineDefinition)
initialState: backlog
columns:
  - state: backlog
    name: Backlog
    phase: QUEUED             # maps to TaskStatus for legacy interop
  - state: drafting
    name: Drafting
    phase: RUNNING
    wipLimit: 3
    processor:                # optional: agent picks up cards entering this column
      type: agent
      promptTemplate: "Draft content for task: {{name}}\n\n{{description}}"
      onSuccess: review       # event fired into the state engine on completion
      onFailure: blocked
  - state: review
    name: In Review
    phase: RUNNING
    wipLimit: 2
  - state: blocked
    name: Blocked
    phase: BLOCKED
  - state: done
    name: Done
    phase: SUCCEEDED
    terminal: true
    terminalKind: SUCCESS     # SUCCESS | FAILURE | CANCELLED
transitions:
  - { from: backlog,  to: drafting, event: START }
  - { from: drafting, to: review,   event: SUBMIT }
  - { from: drafting, to: blocked,  event: BLOCK }
  - { from: review,   to: done,     event: APPROVE }
  - { from: review,   to: drafting, event: REJECT }
  - { from: blocked,  to: drafting, event: UNBLOCK }
```

Java model (records, matching house style — compact constructors with defaulting, `List.copyOf` defensiveness, `@JsonIgnoreProperties(ignoreUnknown = true)`):

```java
public record BoardDefinition(String id, String name, List<String> tenantIds,
        String initialState, List<ColumnDefinition> columns,
        List<TransitionDefinition> transitions) { ... }

public record ColumnDefinition(String state, String name, TaskStatus phase,
        Integer wipLimit, boolean terminal, TerminalKind terminalKind,
        ProcessorDefinition processor) { ... }

public record TransitionDefinition(String from, String to, String event,
        Map<String, String> guards) { ... }
```

A `PipelineFileLoader`-style `BoardFileLoader` reads YAML from `jaiclaw.kanban.boards-dir`; programmatic registration via `KanbanBoardService.register(BoardDefinition)` serves the DSL/embedded path (mirror `jaiclaw-pipeline`'s loader + registry + validator triple — including a `BoardValidator` that checks reachability of terminal states and unknown `from`/`to` references, the analogue of `PipelineValidator`).

### 3.3 State engine SPI — Spring State Machine as an *optional* implementation

This is the central architectural decision. Recommendation: **don't hard-wire Spring State Machine; put it behind an SPI**, exactly as `jaiclaw-tasks` treats Camel.

```java
public interface TaskStateEngine {
    /** Validate and compute the transition; does not persist. */
    TransitionResult fire(BoardDefinition board, TaskRecord task, String event,
                          Map<String, Object> context);
    List<String> allowedEvents(BoardDefinition board, String currentState);
}
```

**Default: `TransitionGraphStateEngine`** (~120 LOC, zero dependencies). Precomputes `Map<state, Map<event, TransitionDefinition>>` per board, enforces WIP-limit and custom guards, returns `TransitionResult(accepted, fromState, toState, reason)`. This covers 90% of kanban semantics and keeps the module light for the embeddable-library use case.

**Optional: `SpringStateMachineEngine`**, activated by `@ConditionalOnClass(name = "org.springframework.statemachine.StateMachine")` + `jaiclaw.kanban.engine: spring-statemachine`. Why offer it at all:

- `StateMachineBuilder` lets us build machines **at runtime from `BoardDefinition`** — no annotations, no compile-time states — which is precisely the "users define the states" requirement:

  ```java
  Builder<String, String> b = StateMachineBuilder.builder();
  b.configureStates().withStates()
      .initial(board.initialState())
      .states(board.columns().stream().map(ColumnDefinition::state).collect(toSet()))
      .end(terminalStates(board));
  for (TransitionDefinition t : board.transitions()) {
      b.configureTransitions().withExternal()
          .source(t.from()).target(t.to()).event(t.event())
          .guard(guardFor(board, t));        // WIP limits, custom guards
  }
  ```

- Guards, transition actions, listeners, and (later) hierarchical/sub-states and timers come for free.

How to use it **statelessly** (important): do *not* keep a live `StateMachine` per task. On each transition, acquire a machine for the board (pooled per board id), rehydrate with `DefaultStateMachineContext<>(task.state(), …)` via `resetStateMachineReactively`, `sendEvent`, read the resulting state, release. The task's current state lives in `TaskRecord`/`TaskStore` — the store remains the single source of truth, and we avoid `StateMachinePersister` machinery entirely. This sidesteps SSM's main operational cost (machine lifecycle) while keeping its main benefit (declarative guards/actions).

Honest assessment: for flat kanban graphs, SSM is heavier than needed — it pulls in its own reactive plumbing and the per-transition rehydrate dance. The SPI keeps the door open without taxing every deployment; ship the graph engine first, SSM engine in a later phase (§8).

### 3.4 Event emission — three planes

Every accepted transition produces one immutable `TransitionRecord(taskId, boardId, fromState, toState, event, actor, tenantId, timestamp)` and is published on:

1. **Spring application events** — `publisher.publishEvent(new TaskStateChanged(record, task))`. Cheapest in-JVM integration point; the column processors themselves subscribe here (a card entering `drafting` triggers `AgentColumnProcessor`).
2. **JaiClaw hooks** — `KanbanHookFirer` through `HookRunner`, so plugins/observability see kanban activity. Two sub-options analyzed in §5.2.
3. **Transport (dashboard)** — `KanbanEventStream`: an SSE broadcaster holding `SseEmitter`s per `(tenantId, boardId)`, fed by the application-event listener. SSE rather than WebSocket because the dashboard flow is server→client only, and **it requires zero changes to the gateway** (the existing `WebSocketSessionHandler` is chat-specific and registering a second WS handler would mean editing gateway auto-config). Wrap async fan-out with `TenantContextPropagator` per the multi-tenancy checklist.

Transition history: `TransitionHistory` — bounded per-board deque + by-task index, copied from `PipelineExecutionTracker`.

### 3.5 REST API for a dashboard UI

Configurable base path `jaiclaw.kanban.http.base-path` (default `/api/kanban`), security delegated to the Spring Security chain (same stance as `PipelineTriggerController`), tenant resolved from `X-Tenant-Id` / `TenantContext`:

```
GET    /api/kanban/boards                          → board summaries
POST   /api/kanban/boards                          → register board definition
GET    /api/kanban/boards/{boardId}                → full definition
GET    /api/kanban/boards/{boardId}/snapshot       → BoardSnapshot (columns → ordered cards, WIP counts)
GET    /api/kanban/boards/{boardId}/ascii?width=120&style=full|compact   → text/plain board
GET    /api/kanban/boards/{boardId}/history?limit=50                     → recent TransitionRecords
GET    /api/kanban/boards/{boardId}/events         → SSE stream (snapshot event on connect, then deltas)
POST   /api/kanban/boards/{boardId}/tasks          → create card {name, description, metadata}
GET    /api/kanban/tasks/{taskId}                  → card detail incl. allowedEvents[]
POST   /api/kanban/tasks/{taskId}/transition       → {event: "SUBMIT", actor?, comment?} → 200 TransitionRecord
                                                      409 + reason on rejected transition (guard/WIP/unknown event)
POST   /api/kanban/tasks/{taskId}/claim            → {assignee} (assignment without state change)
```

`BoardSnapshot` is the dashboard's primary payload — also exactly the input `BoardAsciiRenderer` consumes, so JSON view, ASCII view, and SSE snapshot all derive from one structure. An `@Endpoint(id = "kanban")` Actuator endpoint mirrors the read operations for operators (copy `PipelineActuatorEndpoint`).

### 3.6 Column processors — where the agent runtime plugs in

`ColumnProcessorManager` listens for `TaskStateChanged`; when a card enters a column whose `ColumnDefinition.processor` is set, it submits work through the **existing** `TaskExecutor` (inheriting virtual-thread/SEDA duality and `QUEUED→RUNNING` store updates) with a handler built from a `Function<TaskRecord, String> agentRunner` injected at auto-config time — the same decoupling `CronJobExecutor` uses, so `jaiclaw-kanban` never compiles against `AgentRuntime`. The wiring app supplies:

```java
task -> agentRuntime.run(renderPrompt(task),
        AgentRuntimeContext.builder()
            .agentId("kanban")
            .sessionKey("kanban:" + task.flowId() + ":" + task.id())
            .stateless(true)
            .build())
    .join().content();
```

On handler success the manager fires the column's `onSuccess` event into the state engine; on exception, `onFailure`. WIP limits are enforced as guards at transition time, so a full column simply rejects `START` until capacity frees — backpressure expressed in the state graph rather than in queue plumbing.

### 3.7 ASCII rendering

`BoardAsciiRenderer.render(BoardSnapshot, AsciiBoardOptions)` built on `jaiclaw-ascii-render` (`Canvas` + `Rectangle`/`Label` per column region; compact mode via `Table`). Target output:

```
┌─ Content Review Board ──────────────────────────── 7 cards ─ 2026-06-12 14:02 ─┐
│                                                                                │
│  BACKLOG (2)      DRAFTING (2/3)    REVIEW (1/2)    BLOCKED (1)    DONE (1)    │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐  ┌────────────┐ ┌──────────┐ │
│  │ #a1f2 Blog │   │ #c9d1 News │   │ #e571 FAQ  │  │ #b2c3 SEO  │ │ #f0a9 TOS│ │
│  │ post: Q3   │   │ letter v2  │   │ rewrite    │  │ audit      │ │ update ✓ │ │
│  ├────────────┤   │ ▶ agent    │   └────────────┘  └────────────┘ └──────────┘ │
│  │ #77ab Docs │   ├────────────┤                                               │
│  │ refresh    │   │ #d4e8 API  │                                               │
│  └────────────┘   │ guide      │                                               │
│                   └────────────┘                                               │
└────────────────────────────────────────────────────────────────────────────────┘
```

Exposed three ways from the same renderer: REST (`GET …/ascii`, `text/plain` — `curl`/terminal friendly, per the iterative-doc ASCII precedent), agent tool `board_ascii` (an agent can print its own board into any chat channel), and an MCP tool in `KanbanMcpToolProvider`.

### 3.8 Agent + MCP tools

Extend the existing task tool family (registered through `ToolRegistry` like `TaskTools.registerAll`): `task_move (taskId, event)`, `task_claim`, `board_show (boardId)` → JSON snapshot, `board_ascii (boardId)` → rendered text, `board_list`. `KanbanMcpToolProvider` (server name `kanban`) mirrors the calendar provider so external MCP clients — including Claude — can drive and visualize boards.

---

## 4. How Much Existing Code Must Change

This is the heart of the analysis. Two viable strategies for the task model, then the shared change inventory.

### Option A — Evolve `TaskRecord` in `jaiclaw-tasks` (recommended)

Add fields: `String boardId`, `String state` (kanban state string, nullable for non-board tasks), `String assignee`, `long version` (optimistic locking), `int orderIndex`. Because the record uses canonical-constructor `withX` copy methods, **every `withX` method and every constructor call site must be updated** — but they are all *inside the module*:

| File | Change |
|---|---|
| `TaskRecord.java` | +5 components, +`withState/withBoard/withAssignee/withVersion`, update 5 existing `withX` |
| `TaskService.java` | new `createTask` overload (boardId/state), constructor call update |
| `TaskExecutor.java` | none functionally (uses `withStarted/withResult/withError`) — recompile only |
| `JsonFileTaskStore.java` | none — Jackson + `@JsonIgnoreProperties` handles old files; add `findByBoardAndState` to `TaskStore` SPI + impl |
| `tool/CreateTaskTool.java` (+4 siblings) | optional params; constructor call updates |
| Spock specs in `src/test/groovy` | constructor call updates |

Estimated blast radius: **~8–10 files, all in `jaiclaw-tasks`**, mechanical edits, no behavior change for existing users (new fields default null/0; `state == null` ⇒ legacy enum-only task). `TaskStatus` stays as the coarse phase, so `findByStatus`, delivery logic, and the 5 existing tools keep their semantics. Old JSON task files load cleanly. **Add a `version`-checked `save` (compare-and-save) to `TaskStore`** so concurrent transitions on the same card fail fast instead of last-write-winning — the one genuine behavioral hardening this work should land in `jaiclaw-tasks` regardless of kanban.

### Option B — Zero-touch: kanban state in `TaskRecord.metadata`

Store `kanban.board`, `kanban.state`, `kanban.assignee` in the existing `Map<String,String> metadata`. No change to `jaiclaw-tasks` at all. Rejected as the primary path: stringly-typed, no optimistic locking, `findByBoardAndState` becomes a full scan with map lookups, and it violates the repo's "grounded, real-types" sensibility. Acceptable as a *Phase-1 spike* to validate the engine before committing to the record change.

### Shared change inventory (both options)

| Location | Change | Size | Risk |
|---|---|---|---|
| `extensions/pom.xml` | `<module>jaiclaw-kanban</module>` | 1 line | none |
| root `pom.xml` | dependencyManagement entries for `jaiclaw-kanban`, `jaiclaw-starter-kanban`; `<spring-statemachine.version>` property (Phase 3) | ~12 lines | none |
| `jaiclaw-starters/` | new `jaiclaw-starter-kanban` pom (tasks + kanban + ascii-render) | 1 file | none |
| `jaiclaw-core` `HookEvent` | **only if** first-class events chosen (§5.2): +`TaskStateChangedEvent` record, +1 permits entry | 1 new file + 1 line | low — audit exhaustive switches first |
| `jaiclaw-gateway` | **zero** (SSE lives in the kanban module) | 0 | — |
| `jaiclaw-agent` / `AgentRuntime` | **zero** (Function-indirection, `stateless` context already exists) | 0 | — |
| `jaiclaw-spring-boot-starter` | zero — kanban ships its own `@AutoConfiguration` + `AutoConfiguration.imports`, like tasks/pipeline | 0 | — |
| `CLAUDE.md`, `docs/ARCHITECTURE.md`, dev guide | module counts, dependency graph, kanban section (+ catch up on the pre-existing drift noted in §1) | docs | none |

**Bottom line: ~85–90% of the work is additive code in one new module.** The agent runtime, gateway, session, tenancy, and tool-bridge layers are untouched. The reactive execution model is not modified — it is *driven* by the kanban engine the same way cron and pipeline already drive it. The only contested touches are (a) the contained `TaskRecord` evolution and (b) an optional one-line sealed-interface addition in core.

---

## 5. Key Decisions & Trade-offs

### 5.1 New module vs. growing `jaiclaw-tasks` in place

Putting boards/REST/SSE/SSM inside `jaiclaw-tasks` would bloat a deliberately small module with optional web and state-machine dependencies. The repo convention is thin base + richer companion (`jaiclaw-cron` / `jaiclaw-cron-manager`, `jaiclaw-docstore` / `jaiclaw-docstore-telegram`). **Follow it: `jaiclaw-tasks` gains only the record/SPI evolution; everything kanban-specific lives in `jaiclaw-kanban`.**

### 5.2 Hook events: first-class vs. mapped

- **First-class** (`TaskStateChangedEvent implements HookEvent`): clean for plugin authors (`hooks.register(TaskStateChangedEvent.class, …)`), but edits the sealed permits in zero-Spring core and risks breaking exhaustive switches. Precedent for adding events exists (the hierarchy already grew to 16).
- **Mapped** (`KanbanHookFirer` reports transitions as `ToolCallStartedEvent`/`ToolCallEndedEvent` with `agentId = boardId`, `sessionKey = taskId`): zero core change, existing observability plugins capture kanban activity for free — this is literally what `PipelineHookFirer` does and documents.

**Recommendation:** start mapped (Phase 1), promote to first-class in the next core minor bump when the event shape has stabilized. Spring application events + SSE carry the rich payload either way, so nothing is lost in the interim.

### 5.3 SSE vs. WebSocket for the dashboard stream

SSE: one-directional fits the use case, plain HTTP (proxy/k8s-ingress friendly — relevant to the JKube deployment story), auto-reconnect with `Last-Event-ID`, and zero gateway modification. Dashboard *commands* go over the REST endpoints. If bidirectional board collaboration ever matters, a WS handler can be added then; it would require registering in gateway auto-config — flagged as the one known future gateway touch.

### 5.4 Spring State Machine: measured adoption

Adopt via the `TaskStateEngine` SPI (§3.3), graph engine default. Concrete risks if hard-required instead: a new transitive dependency for every kanban user including CLI/embedded; per-transition machine rehydration overhead; version coupling to verify against Boot 3.5.14 (SSM 4.x targets Boot 3, but it is not in either BOM — pin explicitly and add to the CVE-watch list alongside the existing tomcat/netty/log4j overrides). The SPI converts all of this from a foundation risk into an opt-in.

### 5.5 Multi-tenancy conformance (repo checklist applied)

1. *Persistence isolation*: `JsonFileBoardStore` under tenant subdirectory when `TenantGuard.isMultiTenant()`; task queries filtered by `tenantId` (pattern: `CronService.listJobs`). `TransitionHistory` filtered like `CronService.getFullHistory`.
2. *Async propagation*: column-processor submissions and SSE fan-out wrapped in `TenantContextPropagator` (pattern: `AgentRuntime.run`, `WebSocketSessionHandler`).
3. *`TenantGuard` injection*, never `TenantContextHolder` directly, in `KanbanBoardService`/`TaskTransitionService`.
4. *SINGLE-mode compatibility*: all guards null-safe; boards with empty `tenantIds` visible to all (the `PipelineDefinition` convention).
5. *MCP tools*: `KanbanMcpToolProvider.execute` receives and forwards `TenantContext` (calendar pattern).

---

## 6. Persistence, Crash Recovery & Concurrency

This section answers the operational question directly: **what is on disk, what is only in memory, and what happens to the board when the process dies.**

### 6.1 Durability inventory

| State | Where it lives | Survives restart? |
|---|---|---|
| Task/card records (id, name, status, kanban `state`, metadata, timestamps, tenantId) | `TaskStore` → `JsonFileTaskStore` (`{storage-dir}/tasks.json`, full-file flush on every `save()`, reload in constructor) | ✅ Yes — **the store is the single source of truth for card position** |
| Flows | `JsonFileFlowStore` (`flows.json`) | ✅ Yes |
| Board definitions (states, transitions, processors, WIP limits) | YAML files in `jaiclaw.kanban.boards-dir` (+ `JsonFileBoardStore` for REST-registered boards) | ✅ Yes |
| **In-flight executions** (virtual thread / SEDA consumer running a card's handler) | JVM memory only — the handler is a `Function<TaskRecord, String>` lambda | ❌ **No** — see 6.2 |
| Queued-but-unconsumed submissions | Camel `seda:` queue (in-memory) **and** `CamelTaskRoute.pendingHandlers` (in-memory `ConcurrentHashMap<taskId, handler>`) | ❌ No — the QUEUED *record* survives, the *submission* doesn't |
| Transition history | `TransitionHistory` in-memory bounded deque (Pipeline-tracker pattern) + optional append-only `transitions.jsonl` (6.4) | ⚠️ Deque lost; JSONL survives if enabled |
| SSE dashboard connections | Sockets | ❌ Clients auto-reconnect; first SSE event on connect is a full `BoardSnapshot`, so dashboards self-heal |
| Spring State Machine instances | None persisted — machines are rehydrated per transition from `TaskRecord.state` (§3.3) | ✅ N/A by design — no `StateMachinePersister` needed |
| WIP-limit counters | Derived — computed from the store (`findByBoardAndState(...).size()`), never stored | ✅ Recomputed on demand |

### 6.2 The crash scenario, concretely

Process dies while card `#c9d1` is in `drafting` with an agent processor mid-execution:

1. The store says `state=drafting, status=RUNNING, startedAt=T`. That record **is** on disk.
2. The virtual thread (or SEDA consumer) and its handler lambda are gone. In today's raw `jaiclaw-tasks`, nothing reconstructs them — the card is **orphaned in RUNNING forever**. There is no startup sweep anywhere in the module.
3. Any cards QUEUED into the SEDA queue but not yet consumed are similarly orphaned: record present, handler map empty.

**The kanban design structurally fixes the root cause.** Today's orphaning is unrecoverable because the *work* is described only by an in-memory lambda. On a board, the work is declared in the persisted `ColumnDefinition.processor` (prompt template, onSuccess/onFailure events). Card state + column definition = everything needed to rebuild the execution. Recovery therefore becomes a deterministic sweep:

```java
/** SmartLifecycle bean (precedent: GatewayLifecycle / CronManagerLifecycle), runs after stores load. */
public class KanbanRecoveryManager {
    // For each board, for each processor column:
    //   RUNNING cards  → apply column.restartPolicy
    //   QUEUED cards   → resubmit to ColumnProcessorManager (their submission died with the queue)
    //   record TransitionRecord(event="RECOVERY", actor="system") either way
}
```

Per-column `restartPolicy`:

| Policy | Behavior on recovery of a RUNNING card | When to use |
|---|---|---|
| `fail` (**default**) | Fire the column's `onFailure` event with reason `"interrupted by restart"` — card lands in `blocked`, visible on the board, human/agent decides | Side-effectful work (agent already sent messages, called tools) where duplicate execution is worse than manual triage |
| `requeue` | Increment `metadata["kanban.attempts"]`, resubmit; cap via `maxAttempts` then fall back to `fail` | Idempotent / pure-generation work; gives **at-least-once** semantics |
| `manual` | Leave in place but mark `metadata["kanban.interrupted"]=true`; surfaced in snapshot/ASCII as `⚠ interrupted` | Operator-driven environments |

The same sweep handles the hung-without-crash case: a `staleRunningTimeout` (compared against `startedAt`, or a heartbeat timestamp the processor refreshes in metadata) lets a periodic check apply the same policy to executions that died without taking the JVM with them.

### 6.3 Hardening the JSON store itself (required, small)

Two genuine defects in `JsonFileTaskStore` that kanban inherits and should fix in `jaiclaw-tasks` (Option A scope):

1. **Non-atomic flush.** `flushToDisk()` serializes directly onto `tasks.json`. A crash mid-write leaves a truncated file. Fix: write `tasks.json.tmp` then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. ~6 lines.
2. **Silent corruption swallow.** `loadFromDisk()` catches `IOException`, logs a *warning*, and continues with an empty map — the next flush then **overwrites the corrupt file**, converting a recoverable corruption into permanent data loss. Fix: on parse failure, rename the corrupt file to `tasks.json.corrupt-{ts}` and fail fast (or start empty only with an explicit `jaiclaw.tasks.storage.ignore-corrupt=true`).

Also note for capacity planning: the store rewrites the entire file on every save — every kanban transition is an O(all tasks) write. Fine for hundreds of cards; the documented escape hatch is an `H2TaskStore`/`H2BoardStore` behind the existing `TaskStore` SPI (direct precedent: `jaiclaw-cron-manager`'s `H2CronJobStore` + `H2PersistenceAutoConfiguration`), which also makes WIP checks and compare-and-save transactional.

### 6.4 Durable transition history (optional, cheap)

The in-memory `TransitionHistory` deque is a *recent-activity cache*, not a record. For audit/replay, `jaiclaw.kanban.history.journal=true` appends each `TransitionRecord` as one JSON line to `{boards-dir}/../journal/{boardId}.jsonl` (append-only, atomic at line granularity, trivially `tail -f`-able, replayable to rebuild any historical board snapshot). The deque becomes a cache over the journal tail on startup.

### 6.5 Multi-instance honesty

Everything above assumes **one writer process** — the JSON store is per-filesystem with no locking, and two k8s replicas would split-brain on `tasks.json` and double-execute processor columns. State this explicitly in the module docs. The path to N replicas is: H2/Postgres `TaskStore` + a `claimedBy`/`leaseUntil` column on RUNNING cards (lease renewal = the heartbeat from 6.2; expired lease = another instance may recover the card). That is Phase-4+ work and out of scope for the initial extension, but the SPI shape (compare-and-save + lease fields) is chosen now so it slots in without breaking changes.

### 6.6 Concurrency within one instance

- **Transition race:** two actors fire events on the same card concurrently. Mitigation: optimistic `version` on `TaskRecord` + compare-and-save in `TaskStore`; on conflict, re-read and re-validate (the second event may now be illegal from the new state — correct behavior). A per-task striped `ReentrantLock` map inside `TaskTransitionService` is a simpler Phase-1 stand-in for the JSON store.
- **WIP-limit race:** guard check + move is not atomic across tasks. Acceptable at JSON-file scale; the H2 store enforces the count transactionally.
- **SSE emitter lifecycle:** remove emitters on timeout/completion; bound emitters per tenant (`jaiclaw.kanban.sse.max-connections`).

### 6.7 Per-tenant storage backends

**Current state:** one `TaskStore` bean per process. Tenancy is enforced *inside* that single backend — `JsonFileTaskStore` keys its map by `{tenantId}:{taskId}` and filters reads by the current tenant prefix. All tenants therefore share one storage technology; "tenant A on Redis, tenant B on Postgres" is not expressible today.

**Design:** because every consumer (`TaskService`, `TaskTransitionService`, tools, recovery) depends only on the `TaskStore` SPI, per-tenant backends are a routing layer, not a rewrite:

```java
/** Resolves the backing store for the current tenant; falls back to the default. */
public class TenantRoutingTaskStore implements TaskStore {
    private final TaskStore defaultStore;
    private final Map<String, TaskStore> byTenant;   // built at startup from config
    private final TenantGuard tenantGuard;

    private TaskStore route() {
        if (!tenantGuard.isMultiTenant()) return defaultStore;
        return byTenant.getOrDefault(tenantGuard.requireTenantIfMulti(), defaultStore);
    }
    @Override public void save(TaskRecord t)            { route().save(t); }
    @Override public Optional<TaskRecord> findById(String id) { return route().findById(id); }
    // ... delegate remainder, incl. compareAndSave
}
```

Backends are contributed through a small provider SPI so extensions register implementations as beans (`JsonTaskStoreProvider` built-in; `RedisTaskStoreProvider`, `JdbcTaskStoreProvider` as optional modules — direct precedents: `jaiclaw-calendar`'s `InMemoryCalendarProvider`/`RedisCalendarProvider` pair for swappable backends, and `TenantAgentRuntimeFactory` for per-tenant infrastructure resolution from config):

```java
public interface TaskStoreProvider {
    boolean supports(String type);                       // "json" | "redis" | "postgres" | ...
    TaskStore create(String tenantId, Map<String, String> config);
}
```

```yaml
jaiclaw:
  tasks:
    storage:
      default: { type: json, dir: ~/.jaiclaw/tasks }
      tenants:
        acme:   { type: redis,    url: "redis://redis.acme.svc:6379", key-prefix: "jaiclaw:tasks:" }
        globex: { type: postgres, datasource: globexTasksDs }
```

Rules that keep this sound:

1. **Each backend owns its isolation mechanics** per the multi-tenancy checklist: Redis key prefixes, `WHERE tenant_id = ?`, tenant subdirectories. The router guarantees only *routing*, not isolation.
2. **The `compareAndSave` contract must hold per backend** — JSON: in-map CAS on `version`; Redis: `WATCH`/`MULTI` or a Lua script; JDBC: `UPDATE ... WHERE id=? AND version=?` row-count check. A shared `TaskStoreContractSpec` (Spock) runs against every provider so semantics can't drift.
3. **Recovery sweeps every configured store**: `KanbanRecoveryManager` iterates `(tenantId, store)` pairs, setting tenant context via `TenantContextPropagator` for each pass — a sweep under the default tenant would silently miss Redis/Postgres tenants.
4. `BoardStore` routes identically (boards may also be declared global via empty `tenantIds`, loaded from the shared YAML dir regardless of backend).
5. Cross-backend migration tooling is explicitly out of scope; moving a tenant = export/import, not config flip.

### 6.8 Idempotency contract for retryable work

`restartPolicy: requeue` gives **at-least-once** execution, so re-runs must be harmless. Agent executions are the hard case — the LLM may have already called side-effectful tools (sent a Slack message, opened a PR) before the crash. Pure advice ("please make tasks idempotent") is not enough; the design enforces, assists, and then advises:

**Enforce — declare it or you don't get retries.** `requeue` is only legal on columns marked `idempotent: true`; `BoardValidator` rejects the combination otherwise, so the safe `fail` policy is the path of least resistance:

```yaml
- state: drafting
  processor:
    type: agent
    idempotent: true          # author asserts re-execution is safe → unlocks requeue
    restartPolicy: requeue
    maxAttempts: 3
```

**Assist — stable keys and an effect ledger.** Every processor execution gets an idempotency key that is *identical across retries* of the same unit of work: `{boardId}:{taskId}:{state}:{entrySeq}` (where `entrySeq` is the count of prior entries into this state from the transition history — so a card legitimately re-entering `drafting` after a `REJECT` gets a *new* key, while a crash-retry reuses the old one). The key is injected three ways:

1. **Compute dedupe:** before re-executing, `AgentColumnProcessor` checks an `EffectLedger` (`{key → persisted result}`, stored alongside the journal). If the previous attempt completed its compute but died before the status flip, recovery replays the *recorded result* and fires `onSuccess` — no second LLM run at all.
2. **Tool-level dedupe:** the key rides on `ToolContext`/task metadata so side-effectful tools can check-before-act (branch name = key, message dedupe header = key, upsert instead of insert).
3. **Prompt-level honesty:** retry submissions render with `{{attempt}}` and `{{idempotencyKey}}` plus a standing instruction — *"a previous attempt may have partially completed; verify existing side effects (search before posting, check before creating) before redoing them."* Imperfect, but it points the agent at the right behavior and pairs with (2).

**Advise — guidance to document for board authors:**

- Prefer **compute/effect separation**: have the processor *produce* (persisted in `TaskRecord.result` — already durable) and let delivery be a distinct step gated by the existing `TaskDeliveryState` (`PENDING → DELIVERED`), which recovery checks before re-delivering. Retrying pure generation is always safe; retrying delivery is a ledger lookup.
- Make external effects **upserts keyed by the idempotency key** wherever the target system allows (PRs by branch name, tickets by external-id, files by deterministic path).
- For multi-step work, **checkpoint into `metadata`** (`kanban.checkpoint`) so a retry resumes rather than restarts.
- Treat `maxAttempts` exhaustion as a *transition*, not a log line — the card lands in the `onFailure` column where it is visible on the board and in the ASCII view.

---

## 7. Configuration Surface

```yaml
jaiclaw:
  kanban:
    enabled: true                  # master switch (default false, like tasks)
    boards-dir: ~/.jaiclaw/kanban/boards
    engine: graph                  # graph | spring-statemachine
    http:
      enabled: true
      base-path: /api/kanban
    sse:
      enabled: true
      heartbeat-seconds: 25
      max-connections: 100
    history:
      max-per-board: 200           # PipelineExecutionTracker-style bound
      journal: false               # append-only transitions.jsonl per board (§6.4)
    recovery:
      enabled: true                # startup sweep of RUNNING/QUEUED processor-column cards (§6.2)
      default-restart-policy: fail # fail | requeue | manual (overridable per column)
      max-attempts: 3              # cap for requeue policy
      stale-running-timeout: 30m   # hung-execution detection without a restart
    processors:
      enabled: true                # allow agent column processors
      max-concurrent: 5            # feeds TaskExecutor / SEDA sizing
```

---

## 8. Phased Implementation Plan

| Phase | Scope | Touches existing code? |
|---|---|---|
| **1 — Core engine** | `BoardDefinition` + YAML loader + validator; `TransitionGraphStateEngine`; `TaskTransitionService` with Spring events + mapped hook events; `TransitionHistory`; `TaskRecord`/`TaskStore` evolution (Option A) with compare-and-save; **atomic-write + corrupt-file fixes in `JsonFileTaskStore` (§6.3)**; Spock specs | `jaiclaw-tasks` only (§4) + poms |
| **2 — Surfaces** | REST controller + snapshot; SSE stream; `BoardAsciiRenderer` + `/ascii`; agent tools (`task_move`, `board_show`, `board_ascii`); Actuator endpoint; `KanbanMcpToolProvider`; starter pom | none |
| **3 — Processing & SSM** | `ColumnProcessorManager` + `AgentColumnProcessor` via `Function` indirection over `TaskExecutor`; **`KanbanRecoveryManager` + per-column restart policies + stale-running detection (§6.2)**; idempotency keys + `EffectLedger` + `idempotent` column gating (§6.8); WIP guards; `SpringStateMachineEngine` behind `@ConditionalOnClass`; root-pom SSM version pin | none |
| **4 — Hardening & docs** | First-class `TaskStateChangedEvent` in core (with switch audit); `TenantRoutingTaskStore` + `TaskStoreProvider` SPI with Redis/JDBC providers and shared contract spec (§6.7); H2 `TaskStore`/`BoardStore` with transactional WIP + lease columns for multi-instance (§6.5); transition journal (§6.4); dashboard UI (separate repo/app — API contract from §3.5); `CLAUDE.md` / `ARCHITECTURE.md` / dev-guide updates incl. pre-existing drift | `jaiclaw-core` (1 file + 1 line), docs |

Each phase is independently shippable and gated behind `jaiclaw.kanban.enabled`, so trunk stays releasable.

---

## 9. Open Questions

1. **Board definition CRUD over REST** — should `POST /boards` persist definitions (writable store) or are boards deployment artifacts (YAML files / classpath) only? Pipeline currently treats definitions as artifacts; kanban dashboards may want live board editing.
2. **Card ↔ flow relationship** — reuse `TaskRecord.flowId` to point at the board (cheap, slightly overloaded) vs. the new dedicated `boardId` field (Option A includes it; `flowId` stays for `TaskFlow`). Proposed: dedicated field.
3. **Retention** — terminal cards: archive to a `done/` JSON shard after N days, or rely on history bound only?
4. **Embabel** — should a board's processor be able to target an Embabel `@Agent` action (GOAP-planned card handling) instead of a raw prompt template? The `Function` indirection permits it without design change; worth a Phase-3 example under `jaiclaw-examples`.
5. **SSM version pin** — confirm the exact `spring-statemachine-core` release compatible with Boot 3.5.14 / Spring Framework 6.2 before Phase 3.

---

## Appendix A — Transition sequence (Phase 1–3 assembled)

```
 dashboard/agent/MCP                jaiclaw-kanban                          jaiclaw-tasks
        │                                │                                       │
        │ POST /tasks/{id}/transition    │                                       │
        ├───────────────────────────────►│ TaskTransitionService                 │
        │                                │  ├─ load TaskRecord ──────────────────►│ TaskStore.findById
        │                                │  ├─ TaskStateEngine.fire(board,task,ev)│
        │                                │  │    (graph | spring-statemachine)   │
        │                                │  ├─ rejected ──► 409 + reason         │
        │                                │  ├─ accepted: task.withState(to)      │
        │                                │  │             .withVersion(v+1) ─────►│ TaskStore.compareAndSave
        │                                │  ├─ TransitionHistory.record()        │
        │                                │  ├─ publish TaskStateChanged ─┐       │
        │                                │  └─ KanbanHookFirer ──► HookRunner    │
        │                                │                              │        │
        │      SSE: state-changed        │   KanbanEventStream ◄────────┤        │
        │◄───────────────────────────────┤                              │        │
        │                                │   ColumnProcessorManager ◄───┘        │
        │                                │    └─ if column.processor:            │
        │                                │        TaskExecutor.submit(task, ─────►│ QUEUED→RUNNING→…
        │                                │          agentRunner)  ── on done:    │
        │                                │          fire(onSuccess|onFailure) ↺  │
```

## Appendix B — New-class inventory (estimate)

~28 new Java classes in `jaiclaw-kanban` (≈ 2,400 LOC main + ≈ 1,200 LOC Spock), 1 starter pom, ~10 mechanical edits in `jaiclaw-tasks`, 2 pom edits, 0 changes in `jaiclaw-core`/`jaiclaw-agent`/`jaiclaw-gateway` until Phase 4's optional event promotion. Comparable in size to `jaiclaw-pipeline` (≈ 50 classes), smaller in risk because it modifies no execution path that exists today.
