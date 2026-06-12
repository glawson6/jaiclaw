# JaiClaw Kanban — Implementation Plan

**Status:** In Progress — **Phase 1 (Core engine) complete; Phase 2 (Surfaces) next**
**Companion analysis:** [`KANBAN-TASK-PROCESSING-ANALYSIS.md`](./KANBAN-TASK-PROCESSING-ANALYSIS.md) — design rationale; this plan is the execution map.
**Started:** 2026-06-12
**Last updated:** 2026-06-12 — Phase 1 fully landed (all groups checked, 23/23 kanban specs + 24/24 tasks specs pass, install clean).

---

## 1. Context

The companion analysis lays out the case for a new `extensions/jaiclaw-kanban`
module that extends `jaiclaw-tasks` with user-defined states, transition
events, a board model, a dashboard-facing API, and ASCII board rendering. ~60%
of the raw material already exists; the agent runtime itself needs no changes.

This plan is the multi-session execution map. It is consulted at the start of
every session, drives one phase at a time, and carries its own maintained task
list so the next session can pick up without re-reading the analysis.

### Decisions locked in before Phase 1

- **All four phases** from analysis §8 are separately shippable milestones.
- **Default boards directory:** `~/.jaiclaw/kanban/boards`.
- **State engine:** ship `TaskStateEngine` SPI + `TransitionGraphStateEngine`
  in Phase 1; `SpringStateMachineEngine` arrives in Phase 3 behind
  `@ConditionalOnClass`.
- **E2E testing:** a dedicated `@SpringBootTest` harness inside
  `jaiclaw-kanban` grows phase-by-phase on one shared small fixture board; a
  separate `jaiclaw-examples/kanban-demo` runnable app **plus** a
  `kanban-e2e` skill land in Phase 4 alongside the dashboard UI.

### Explicitly out of scope

- Modifications to the agent runtime, gateway, session manager, or tool bridge
  (the analysis is explicit that none are required).
- A built-in dashboard UI inside the JaiClaw repo — the Phase 2 REST + SSE
  contract is the integration point; the UI itself is a separate
  app/repository tracked as a single Phase 4 checkbox.
- Cross-backend migration tooling for the per-tenant routing store (export /
  import only).

---

## 2. How to use this plan (and conventions)

1. Read this section, then jump to the **current phase** named in §1's status
   line.
2. At the top of every phase block is a single **Resume here** pointer. It
   names the next unchecked task and the file last touched. Update it when you
   pick up work and when you finish.
3. Inside a phase, pick the next `[ ]` task. Flip it to `[x]` when done.
   Annotate `Blocked: <reason>` inline if you get stuck.
4. A phase is not complete until **every checkbox is ticked** *and* the
   **Definition of Done** is satisfied.
5. Commit per task where reasonable. Spock specs ship alongside production
   code in the same commit.
6. Favor `@Configuration` + `@Bean` over `@Component` (repo rule). Do **not**
   add `Co-Authored-By` or any AI attribution to commit messages (repo rule).
7. After significant decisions, append to §10 Decision log — not for commits
   (git log covers those), only for choices future sessions need to know.

Checkbox states used:

- `[ ]` not started
- `[x]` done

---

## 3. Module layout

See analysis [§3.1](./KANBAN-TASK-PROCESSING-ANALYSIS.md#31-module-placement-and-shape).
Do not duplicate here — copies drift.

---

## 4. Pattern precedents

Concrete file references that every phase reuses (verified at plan-write time):

| Purpose | File |
|---|---|
| Bounded per-key history tracker | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/tracking/PipelineExecutionTracker.java` |
| REST trigger controller | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/web/PipelineTriggerController.java` |
| Actuator endpoint | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/actuator/PipelineActuatorEndpoint.java` |
| Startup validator with "did you mean?" | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/validation/PipelineValidator.java` |
| YAML loader (classpath* + file: globs) | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/loader/PipelineFileLoader.java` |
| Hook firer mapping onto existing events | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/PipelineHookFirer.java` |
| MCP tool provider shape | `extensions/jaiclaw-calendar/src/main/java/io/jaiclaw/calendar/mcp/CalendarMcpToolProvider.java` |
| Agent-runner `Function` indirection | `extensions/jaiclaw-cron/src/main/java/io/jaiclaw/cron/CronJobExecutor.java` |
| H2 store with optional dependency | `extensions/jaiclaw-cron-manager/src/main/java/io/jaiclaw/cronmanager/persistence/h2/H2CronJobStore.java` |
| Sealed HookEvent (16 permits) | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/HookEvent.java` (lines 34–40) |
| ASCII rendering primitives | `core/jaiclaw-ascii-render/src/main/java/io/jaiclaw/asciirender/core/{Canvas,element/Table,factory/AsciiSceneFactory}.java` |
| Pipeline-style example app | `jaiclaw-examples/pipeline-e2e/` |
| Existing E2E skill to mirror | `.claude/skills/e2e-test/` |

Known defects in `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/JsonFileTaskStore.java`
fixed in Phase 1:

- **Non-atomic flush** — `flushToDisk()` called immediately after every
  `save()` and `deleteById()` at lines 78 and 104; no `tmp` file, no
  `ATOMIC_MOVE`.
- **Silent corruption swallow** — `loadFromDisk()` lines 127–129 catches
  `IOException`, logs a warning, continues with an empty map; the next
  `flushToDisk()` then overwrites the corrupt file.

---

## 5. End-to-end testing (referenced from every phase)

One story told once and grown per phase, so a failure points at the layer,
not the fixture.

### 5.1 Shared fixture board

`extensions/jaiclaw-kanban/src/test/resources/boards/e2e-content-review.yaml`
— small content-review board: states `backlog → drafting → review → done`
with `blocked` side state, 3–5 cards, WIP limit 2 on `drafting`, a stub
processor on `drafting` that calls a test-injected `Function<TaskRecord,
String>`. Used by every phase's E2E spec.

### 5.2 In-module Spring harness

Lives at `extensions/jaiclaw-kanban/src/test/groovy/io/jaiclaw/kanban/e2e/`.

| Phase | Spec | Coverage |
|---|---|---|
| 1 | `KanbanCoreE2ESpec` | `@SpringBootTest(webEnvironment=NONE)`. Loads fixture, runs happy-path transitions through `TaskTransitionService`, asserts (a) `TaskStore` writes are atomic via a fault-injecting decorator that kills the JVM mid-flush and confirms no truncated file, (b) `compareAndSave` rejects stale-version writes, (c) `TaskStateChanged` Spring events fire in order with full payload, (d) `BoardValidator` rejects a broken-fixture twin. |
| 2 | `KanbanSurfacesE2ESpec` | `@SpringBootTest(webEnvironment=RANDOM_PORT)`. Drives `POST /api/kanban/tasks/{id}/transition`, asserts SSE stream delivers `state-changed` via `WebTestClient`, asserts `/ascii` byte-matches a golden snapshot in `src/test/resources/boards/golden/`, asserts MCP `tools/list` returns the 5 kanban tools and `tools/call task_move` advances a card, asserts **adding a new card via `POST /tasks` to a running board** appears in the snapshot and SSE stream. |
| 3 | `KanbanRecoveryE2ESpec` + `KanbanIdempotencyE2ESpec` | Injects a fault into `TaskExecutor` mid-handler (after RUNNING flip), bounces the Spring context, asserts per-`restartPolicy` outcomes (`fail` → `blocked` with RECOVERY `TransitionRecord`; `requeue` → re-execution with same idempotency key + `EffectLedger` consulted; `manual` → in-place with `kanban.interrupted=true`). A separate spec verifies the same scenarios under both `graph` and `spring-statemachine` engines via property flip. |
| 4 | `KanbanTenantRoutingE2ESpec` | Multi-tenant config with two backends (JSON + H2 in-memory). Asserts each tenant's cards land in the correct store, recovery sweeps both, transition journal jsonl records every event, `RedisTaskStoreProvider` swap-in passes the shared `TaskStoreContractSpec`. |

### 5.3 External E2E (Phase 4)

- **`jaiclaw-examples/kanban-demo`** — runnable Spring Boot app preloaded with
  the fixture board, a stub agent runner, and a README demoing curl-driven
  transitions + SSE + ASCII output. Mirrors `jaiclaw-examples/pipeline-e2e`.
  Must include the `jaiclaw-maven-plugin` `jaiclaw:analyze` step per the
  examples rule in `CLAUDE.md`.
- **`kanban-e2e` skill** under `.claude/skills/kanban-e2e/` — mirrors the
  existing `e2e-test` skill. Boots the demo app out-of-process, runs the curl
  scenarios, captures the SSE stream, byte-compares ASCII output. This is the
  out-of-process gate that the demo app and dashboard contract actually work.

---

## 6. Phase 1 — Core engine

**Resume here →** all Phase 1 groups complete; jump to Phase 2 §7. | last touched: `CLAUDE.md` (drift catch-up landed)

### 6.1 Scope

Stand up the `jaiclaw-kanban` module skeleton, evolve `TaskRecord` and
`TaskStore` to support boards (Option A from analysis §4 plus the
`idempotencyKey` field added now to avoid a Phase 3 second migration), harden
`JsonFileTaskStore`'s two known defects, ship the lightweight transition-graph
state engine, fire rich `TaskStateChanged` Spring application events, map onto
existing hooks via `KanbanHookFirer`, and prove all of it together with
`KanbanCoreE2ESpec`.

### 6.2 Definition of Done

- `./mvnw install -pl :jaiclaw-kanban -am -DskipTests` succeeds clean.
- `./mvnw test -pl :jaiclaw-tasks,:jaiclaw-kanban` passes.
- `KanbanCoreE2ESpec` passes.
- `JsonFileTaskStore` writes via `tmp + ATOMIC_MOVE`; a manually corrupted
  `tasks.json` is renamed to `tasks.json.corrupt-<ts>` and startup fails fast
  (unless `jaiclaw.tasks.storage.ignore-corrupt=true`).
- Existing `jaiclaw-tasks` Spock specs still pass — no behavior change for
  legacy task consumers (cron, calendar, pipeline).
- `jaiclaw.kanban.enabled=false` (default) leaves the module entirely dormant.

### 6.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `extensions/pom.xml` | modify | add `<module>jaiclaw-kanban</module>` |
| `pom.xml` (root) | modify | dependencyManagement entries for `jaiclaw-kanban` and `jaiclaw-starter-kanban` |
| `jaiclaw-starters/jaiclaw-starter-kanban/pom.xml` | create | starter wrapping `jaiclaw-tasks` + `jaiclaw-kanban` |
| `extensions/jaiclaw-kanban/pom.xml` | create | module pom (mirror `jaiclaw-pipeline`) |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/TaskRecord.java` | modify | +`boardId`, +`state`, +`assignee`, +`version`, +`orderIndex`, +`idempotencyKey`; new `withX` methods; update all `withX` call sites |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/TaskStore.java` | modify | add `compareAndSave(TaskRecord)`, `findByBoardAndState(String, String)` |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/JsonFileTaskStore.java` | modify | atomic flush; corrupt-file rename + fail-fast; implement `TaskStoreProvider` SPI |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/persistence/TaskStoreProvider.java` | create | SPI shape (single-provider for now; Phase 4 adds Redis/JDBC) |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/{TaskService,TasksAutoConfiguration}.java` | modify | constructor call updates; new factory overload for board-aware tasks |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/tool/*.java` | modify | constructor call updates only |
| `extensions/jaiclaw-tasks/src/test/groovy/.../*Spec.groovy` | modify | constructor call updates; new specs for atomic flush, corrupt-file, compareAndSave |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/model/*.java` | create | `BoardDefinition`, `ColumnDefinition`, `TransitionDefinition`, `ProcessorDefinition`, `TerminalKind`, `TransitionRecord`, `BoardSnapshot`, `CardView` |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/loader/BoardFileLoader.java` | create | Spring resource resolution (`classpath*:` + `file:`) over `~/.jaiclaw/kanban/boards` |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/validation/BoardValidator.java` | create | unknown from/to, unreachable terminals, "did you mean?" via Levenshtein ≤ 2 |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/state/{TaskStateEngine,TransitionGraphStateEngine,TransitionResult,StateEngineException}.java` | create | SPI + default impl |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/service/{KanbanBoardService,TaskTransitionService,TransitionHistory}.java` | create | service surface + bounded per-board deque (PipelineExecutionTracker pattern) |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/events/{TaskStateChanged,KanbanHookFirer}.java` | create | Spring app event + mapped HookRunner firer (ToolCallStarted/Ended pattern per PipelineHookFirer) |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/{KanbanAutoConfiguration,KanbanProperties}.java` | create | gated on `jaiclaw.kanban.enabled`, `@AutoConfigureAfter(TasksAutoConfiguration.class)` |
| `extensions/jaiclaw-kanban/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | create | register `KanbanAutoConfiguration` |
| `extensions/jaiclaw-kanban/src/test/resources/boards/e2e-content-review.yaml` | create | shared E2E fixture board (§5.1) |
| `extensions/jaiclaw-kanban/src/test/groovy/io/jaiclaw/kanban/e2e/KanbanCoreE2ESpec.groovy` | create | Phase 1 E2E |
| `CLAUDE.md` | modify | analysis §1 drift catch-up: module counts, Boot 3.5.14, Embabel 0.3.5, mention `jaiclaw-ascii-render` and `jaiclaw-maven-plugin` |

### 6.4 Task list

**Module skeleton**
- [x] Add `extensions/pom.xml` module entry for `jaiclaw-kanban`
- [x] Add root pom dependencyManagement entries (`jaiclaw-kanban`, `jaiclaw-starter-kanban`)
- [x] Create `extensions/jaiclaw-kanban/pom.xml` (mirror `jaiclaw-pipeline`)
- [x] Create `jaiclaw-starters/jaiclaw-starter-kanban/pom.xml`
- [x] Create `KanbanProperties` (boards-dir default `~/.jaiclaw/kanban/boards`, engine, history, recovery, processors per analysis §7) — recovery + processor properties placeholder until Phase 3
- [x] Create `KanbanAutoConfiguration` + register in `AutoConfiguration.imports`

**TaskRecord & TaskStore evolution**
- [x] Add fields to `TaskRecord`: `boardId`, `state`, `assignee`, `version`, `orderIndex`, `idempotencyKey` (all nullable / 0-default for legacy)
- [x] Add new `withBoardId/withState/withAssignee/withVersion/withOrderIndex/withIdempotencyKey` methods
- [x] Update all existing `withX` methods (`withStatus`, `withResult`, `withError`, `withStarted`, `withDeliveryState`) to thread the new fields
- [x] Update all `TaskRecord` constructor call sites in `jaiclaw-tasks` (TaskService, 5 tool classes, specs) — legacy 13-arg constructor delegates to canonical so call sites untouched
- [x] Add `TaskStore.compareAndSave(TaskRecord)` returning success/conflict
- [x] Add `TaskStore.findByBoardAndState(String, String)`
- [x] Create `TaskStoreProvider` SPI; create `JsonTaskStoreProvider` wrapping `JsonFileTaskStore` (single provider for Phase 1)

**JsonFileTaskStore hardening**
- [x] Replace `flushToDisk()` with `tmp + Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` (fixes lines 78, 104)
- [x] On parse failure in `loadFromDisk()` rename file to `tasks.json.corrupt-<ts>` and throw, unless `jaiclaw.tasks.storage.ignore-corrupt=true` (fixes lines 127–129)
- [x] Add Spock spec: atomic flush leaves no `.tmp` after success (`JsonFileTaskStoreHardeningSpec`)
- [x] Add Spock spec: pre-corrupted `tasks.json` triggers rename + fail-fast; flag enables empty-start path
- [x] Add Spock spec: `compareAndSave` — first writer wins version bump, stale writer gets conflict

**Board model**
- [x] Create record `BoardDefinition` (id, name, tenantIds, initialState, columns, transitions) with compact-constructor defaulting per house style
- [x] Create `ColumnDefinition`, `TransitionDefinition`, `ProcessorDefinition`, `TerminalKind`
- [x] Create `TransitionRecord(taskId, boardId, fromState, toState, event, actor, tenantId, timestamp)`
- [x] Create `BoardSnapshot` + `CardView` value records — primary payload (analysis §3.5)

**Board loader & validator**
- [x] `BoardFileLoader` resolves `classpath*:` + `file:` patterns under `jaiclaw.kanban.boards-dir`; filename-stem fallback for `id:`
- [x] `BoardValidator` checks unknown from/to, unreachable terminals, WIP guards present where required; "did you mean?" via Levenshtein ≤ 2 (PipelineValidator pattern)
- [x] Spock specs for loader (file:, classpath, filename-stem fallback, corrupt-file skip, de-dup) and validator (7 error classes incl. `REQUEUE_REQUIRES_IDEMPOTENT`)

**State engine**
- [x] `TaskStateEngine` SPI (`fire(board, task, event, context) → TransitionResult`, `allowedEvents(board, state)`)
- [x] `TransitionGraphStateEngine` default: precomputed `Map<state, Map<event, TransitionDefinition>>`; WIP-limit + custom guards; `TransitionResult(accepted, fromState, toState, reason)`
- [x] Spock specs for legal/illegal events, no-outgoing-edge rejection, WIP-limit rejection + pass-through, `allowedEvents`, cache invalidation

**Service layer**
- [x] `KanbanBoardService` — register/list/get boards (in-memory for Phase 1; analysis §9 Q1 decides REST persistence in Phase 2)
- [x] `TaskTransitionService` — load task, run engine, on accept: `withState`, `compareAndSave`, append `TransitionHistory`, publish `TaskStateChanged`, fire `KanbanHookFirer`; on conflict: re-read + re-validate (second event may now be illegal — correct behavior)
- [x] `TransitionHistory` — bounded per-board deque + by-task index, synchronized eviction (copy `PipelineExecutionTracker` shape)
- [x] Per-task striped `ReentrantLock` map in `TaskTransitionService` as Phase-1 race guard (analysis §6.6)

**Events**
- [x] `TaskStateChanged` Spring `ApplicationEvent` with full payload (boardId, taskId, tenantId, fromState, toState, event, actor, timestamp) — **shape frozen**, Phase 2 SSE consumes it directly
- [x] `KanbanHookFirer` reports each transition as `ToolCallStartedEvent` + `ToolCallEndedEvent` (`agentId = boardId`, `sessionKey = taskId`) — PipelineHookFirer pattern, no core change

**E2E**
- [x] Commit shared fixture board `src/test/resources/boards/e2e-content-review.yaml`
- [x] Write `KanbanCoreE2ESpec` covering the four assertions in §5.2 Phase 1 row (CREATE → START → SUBMIT → APPROVE chain; atomic flush leaves no `.tmp`; stale-version `compareAndSave` rejection; validator rejects broken twin). **Note:** Phase 1 E2E uses constructor wiring not `@SpringBootTest`. The Phase 1 wiring is a straight mirror of `KanbanAutoConfiguration` and there are no Phase 1 surfaces (REST/SSE/MCP/Actuator are all Phase 2). Spring-context coverage lands in Phase 2's `KanbanSurfacesE2ESpec`, where it earns its boot cost.

**Docs drift catch-up**
- [x] Update `CLAUDE.md` analysis-§1 drift: core count 10→11 + add `jaiclaw-ascii-render`; extensions 34→35 + add `jaiclaw-kanban`; Version Alignment block updated to Embabel 0.3.5 / Spring Boot 3.5.14 / Spring AI 1.1.7 / Spring Shell 3.4.2 / Camel 4.18.2; kanban module description added to the dependency-graph block (full kanban section deferred to Phase 4 docs sync)

### 6.5 Verification

Run `./mvnw test -pl :jaiclaw-tasks,:jaiclaw-kanban -am -o`. The focused
Spock specs land per component; the §5.2 Phase 1 row covers the full happy
path. Manually corrupt a `tasks.json` and bounce a smoke app to confirm
fail-fast.

### 6.6 Risk & rollback

`TaskRecord` changes are forward-compatible by Jackson's existing
`@JsonIgnoreProperties(ignoreUnknown=true)` — old JSON deserializes; new
fields default null/0. `TaskStatus` enum stays as coarse phase, so existing
consumers (cron, calendar, pipeline) keep working unchanged. Rollback =
revert the `jaiclaw-tasks` commit; `jaiclaw-kanban` is opt-in via
`jaiclaw.kanban.enabled=false` default and harmless when off.

---

## 7. Phase 2 — Surfaces

**Resume here →** `[ ]` *Decide analysis §9 Q1 before opening REST controller* (board-definition CRUD persistence) | last touched: *(Phase 2 not started)*

### 7.1 Scope

Add REST + SSE for dashboards, ASCII board rendering on top of
`jaiclaw-ascii-render`, MCP tool provider, agent tools, and a Boot Actuator
endpoint. Zero changes outside the kanban module.

### 7.2 Definition of Done

- All REST endpoints from analysis §3.5 respond with the expected shapes.
- `KanbanSurfacesE2ESpec` passes, including the dynamic-card scenario.
- ASCII output byte-matches the golden snapshot.
- `tools/list` over MCP returns the 5 declared kanban tools.
- `/actuator/kanban` returns recent transitions per board.
- Analysis §9 Q1 (board definition CRUD over REST) resolved and recorded in
  §10 + linked from §11.

### 7.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/web/{KanbanBoardController,KanbanEventController,KanbanWebConfiguration}.java` | create | REST + SSE; configurable base path |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/render/{BoardAsciiRenderer,AsciiBoardOptions}.java` | create | over `jaiclaw-ascii-render` `Canvas`/`Rectangle`/`Label` |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/mcp/KanbanMcpToolProvider.java` | create | server name `kanban`; CalendarMcpToolProvider shape |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/tool/{MoveTaskTool,ShowBoardTool,BoardAsciiTool,ClaimTaskTool,BoardListTool}.java` | create | constructors **already accept `Function<TaskRecord,String>` runner indirection** (so Phase 3 needs no refactor) |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/actuator/KanbanActuatorEndpoint.java` | create | `@Endpoint(id="kanban")` mirroring PipelineActuatorEndpoint |
| `extensions/jaiclaw-kanban/src/test/resources/boards/golden/*.txt` | create | golden ASCII snapshots for renderer |
| `extensions/jaiclaw-kanban/src/test/groovy/io/jaiclaw/kanban/e2e/KanbanSurfacesE2ESpec.groovy` | create | Phase 2 E2E |

### 7.4 Task list

**Decision before code**
- [ ] **Decide analysis §9 Q1** — does `POST /api/kanban/boards` persist definitions to a writable `BoardStore`, register in-memory only, or return 405 (YAML-only)? Cards stay dynamic regardless — this question is about *board definitions*. Record resolution in §10 and update §11 status.

**REST + SSE**
- [ ] Create `KanbanBoardController` with all read endpoints (boards list, definition, snapshot, history, task detail)
- [ ] Add write endpoints per §9 Q1 resolution (always: `POST /tasks`, `POST /tasks/{id}/transition`, `POST /tasks/{id}/claim`; conditionally `POST /boards`)
- [ ] Implement `409 + reason` on rejected transitions (guard, WIP, unknown event, version conflict)
- [ ] Resolve tenant from `X-Tenant-Id` / `TenantContext`; configurable base path `jaiclaw.kanban.http.base-path` (default `/api/kanban`)
- [ ] Create `KanbanEventController` with SSE emitter pool per `(tenantId, boardId)`; first event on connect is full `BoardSnapshot`; heartbeat per `jaiclaw.kanban.sse.heartbeat-seconds`; max-connections per `jaiclaw.kanban.sse.max-connections`
- [ ] Wrap SSE fan-out in `TenantContextPropagator` (analysis §3.5 multi-tenancy rule)
- [ ] Guard SSE bits with `@ConditionalOnClass(SseEmitter.class)` + `@ConditionalOnWebApplication`
- [ ] Spock specs: controller MockMvc per endpoint; SSE emitter lifecycle unit spec; 409 paths

**ASCII renderer**
- [ ] `BoardAsciiRenderer.render(BoardSnapshot, AsciiBoardOptions)` over `Canvas` + `Rectangle` + `Label`; full style + compact (`Table`) variants
- [ ] `text/plain` endpoint `GET …/ascii?width=N&style=full|compact`
- [ ] Commit golden snapshots under `src/test/resources/boards/golden/`
- [ ] Spock spec: snapshot golden compare; differing widths and styles

**MCP + agent tools**
- [ ] `KanbanMcpToolProvider` with `getTools()` + `execute(toolName, args, TenantContext)` — forwards tenant per analysis §3.5
- [ ] Implement agent tools `task_move`, `task_claim`, `board_show`, `board_ascii`, `board_list` — constructors take `Function<TaskRecord,String>` agent-runner so Phase 3 plugs in without changing signatures
- [ ] Register tools through `ToolRegistry` from `KanbanAutoConfiguration`
- [ ] Spock specs: `tools/list` shape; `tools/call` for each, including dynamic card creation via `task_move` flow

**Actuator**
- [ ] `KanbanActuatorEndpoint` mirroring `PipelineActuatorEndpoint`'s read operations (boards, recent history per board, configured engine)

**E2E**
- [ ] Write `KanbanSurfacesE2ESpec` per §5.2 Phase 2 row — includes the dynamic-card scenario

### 7.5 Verification

`./mvnw test -pl :jaiclaw-kanban -am -o`. Manual `curl` against a running
gateway-app smoke profile to sanity-check headers and SSE behavior. The §9 Q1
decision must be recorded in §10 before the controller PR merges.

### 7.6 Risk & rollback

SSE and REST are guarded by `@ConditionalOnClass` / `@ConditionalOnWebApplication`,
so non-web embeddings remain unaffected. No gateway changes. Rollback =
disable controllers via property or revert the surfaces commit; engine
(Phase 1) keeps working.

---

## 8. Phase 3 — Processing & SSM

**Resume here →** `[ ]` *Phase 3 not started* | last touched: *(none yet)*

### 8.1 Scope

Wire column processors so agents pick up cards entering a column, add startup
recovery + stale-execution detection with per-column restart policies, ship
the idempotency contract (key + EffectLedger + `idempotent` gating), and
introduce `SpringStateMachineEngine` as an optional second `TaskStateEngine`
behind `@ConditionalOnClass`.

### 8.2 Definition of Done

- `KanbanRecoveryE2ESpec` + `KanbanIdempotencyE2ESpec` pass.
- A property flip between `graph` and `spring-statemachine` engines runs the
  same transition chain successfully on the shared fixture board.
- WIP-limit violations cause transition rejection at the engine, not panic at
  a queue.
- A card with `restartPolicy: requeue` on an `idempotent: false` column fails
  `BoardValidator`.
- `jaiclaw.kanban.engine: graph` (default) leaves no `spring-statemachine`
  classes on the runtime classpath.

### 8.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/engine/{ColumnProcessorManager,AgentColumnProcessor,ColumnPolicy}.java` | create | event-driven processor; submits via existing `TaskExecutor` + `Function` indirection |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/recovery/{KanbanRecoveryManager,RestartPolicy,StaleRunningDetector}.java` | create | `SmartLifecycle` startup sweep + periodic stale detection |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/idempotency/{IdempotencyKeyBuilder,EffectLedger}.java` | create | stable key + `{key → persisted result}` ledger alongside journal |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/statemachine/{SpringStateMachineEngine,BoardStateMachineFactory}.java` | create | runtime build from `BoardDefinition`; stateless rehydrate-fire-release |
| `pom.xml` (root) | modify | `<spring-statemachine.version>` property pin (compat-check against Boot 3.5.14 / Spring 6.2) |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/validation/BoardValidator.java` | modify | reject `requeue` on `idempotent: false` columns |
| `extensions/jaiclaw-kanban/src/test/groovy/io/jaiclaw/kanban/e2e/{KanbanRecoveryE2ESpec,KanbanIdempotencyE2ESpec}.groovy` | create | Phase 3 E2E |

### 8.4 Task list

**Column processors**
- [ ] `ColumnProcessorManager` listens for `TaskStateChanged`; if `column.processor` set, submits via existing `TaskExecutor.submit(task, handler)`
- [ ] Handler injects an app-provided `Function<TaskRecord,String> agentRunner` (cron pattern — kanban module never compiles against `AgentRuntime`)
- [ ] `AgentColumnProcessor` renders prompt template with `{{name}}`, `{{description}}`, `{{attempt}}`, `{{idempotencyKey}}`
- [ ] On handler success fire `column.processor.onSuccess`; on exception fire `column.processor.onFailure`
- [ ] WIP-limit enforcement at engine guard (not at queue) — full column rejects `START`

**Recovery**
- [ ] `KanbanRecoveryManager` `SmartLifecycle` bean — runs after stores load; iterates RUNNING + QUEUED processor-column cards
- [ ] Apply per-column `restartPolicy` (`fail` default | `requeue` | `manual`) per analysis §6.2 table
- [ ] Record `TransitionRecord(event="RECOVERY", actor="system")` for each
- [ ] Periodic stale-running detection: compare `startedAt` (or processor heartbeat in metadata) vs `jaiclaw.kanban.recovery.stale-running-timeout`; apply same policy

**Idempotency**
- [ ] `IdempotencyKeyBuilder` produces `{boardId}:{taskId}:{state}:{entrySeq}` (entrySeq derived from `TransitionHistory` count of prior entries into this state)
- [ ] `EffectLedger` `{key → persisted result}` stored alongside journal (Phase 4 journal — Phase 3 ledger lives in `JsonFileTaskStore` storage dir under `{boardId}/effects.jsonl`)
- [ ] `AgentColumnProcessor` consults ledger before re-executing on retry: if recorded, fire `onSuccess` directly with stored result
- [ ] `BoardValidator` rejects `restartPolicy: requeue` on `idempotent: false` columns
- [ ] `maxAttempts` cap with fall-through to `fail` policy

**Spring State Machine engine**
- [ ] Pin `<spring-statemachine.version>` in root pom; verify compat against Boot 3.5.14 / Spring Framework 6.2
- [ ] Implement `SpringStateMachineEngine` via runtime `StateMachineBuilder` from `BoardDefinition`
- [ ] Stateless usage: rehydrate per transition with `DefaultStateMachineContext<>(task.state(), …)` via `resetStateMachineReactively`; pool machines per board id
- [ ] Activate via `@ConditionalOnClass(name="org.springframework.statemachine.StateMachine")` + `jaiclaw.kanban.engine: spring-statemachine`

**E2E**
- [ ] Write `KanbanRecoveryE2ESpec` per §5.2 Phase 3 row
- [ ] Write `KanbanIdempotencyE2ESpec` per §5.2 Phase 3 row
- [ ] Add engine-swap scenario asserting graph and SSM produce equivalent transition outcomes

### 8.5 Verification

`./mvnw test -pl :jaiclaw-kanban -am -o`. Then re-run with
`-Djaiclaw.kanban.engine=spring-statemachine` to confirm the swap.

### 8.6 Risk & rollback

SSM version pin is analysis §9 Q5 — if no clean fit emerges,
`SpringStateMachineEngine` ships as a stub disabled by default, and Phase 3
proceeds with `TransitionGraphStateEngine` only. SSM is opt-in via property
**and** classpath, so its absence does not block Phase 3 ship. Recovery and
idempotency are guarded behind `jaiclaw.kanban.recovery.enabled` (default
true) and `column.idempotent` (default false).

---

## 9. Phase 4 — Hardening & docs

**Resume here →** `[ ]` *Phase 4 not started* | last touched: *(none yet)*

### 9.1 Scope

Promote the kanban event to a first-class `HookEvent`, add per-tenant store
routing with Redis + JDBC providers, ship transactional H2 stores with
lease-based multi-instance recovery, durably journal transitions, ship the
demo example app + `kanban-e2e` skill, and sync all documentation.

### 9.2 Definition of Done

- `TaskStateChangedEvent` is a first-class `HookEvent`; `grep -r "switch.*HookEvent\|case .*Event ->"` audit shows zero compile errors after the change.
- Shared `TaskStoreContractSpec` passes against JSON, Redis (Testcontainers), and JDBC (H2 + Postgres-via-Testcontainers) providers.
- `KanbanTenantRoutingE2ESpec` passes.
- `jaiclaw-examples/kanban-demo` builds and runs; the `kanban-e2e` skill passes when invoked against it.
- `CLAUDE.md`, `docs/dev/ARCHITECTURE.md`, `docs/user/OPERATIONS.md`, dev-guide satellites all reflect the kanban module (and the analysis-§1 drift fully retired).

### 9.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/TaskStateChangedEvent.java` | create | first-class hook event |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/HookEvent.java` | modify | add to `permits` list |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/persistence/{TenantRoutingTaskStore,RedisTaskStoreProvider,JdbcTaskStoreProvider}.java` | create | tenant router + two new providers |
| `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/persistence/h2/{H2TaskStore,H2BoardStore}.java` | create | transactional WIP + `claimedBy`/`leaseUntil` columns |
| `extensions/jaiclaw-tasks/src/test/groovy/.../TaskStoreContractSpec.groovy` | create | abstract spec every provider runs |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/journal/TransitionJournal.java` | create | append-only jsonl |
| `jaiclaw-examples/kanban-demo/` | create | runnable example app + README |
| `.claude/skills/kanban-e2e/` | create | new E2E skill mirroring `e2e-test` |
| `CLAUDE.md`, `docs/dev/ARCHITECTURE.md`, `docs/user/OPERATIONS.md`, `/Users/tap/dev/docs/jaiclaw/JAICLAW-DEVELOPER-GUIDE.md` + satellites | modify | docs sync |

### 9.4 Task list

**First-class HookEvent**
- [ ] `grep -r "switch.*HookEvent\|case .*Event ->"` audit; list every exhaustive switch
- [ ] Add `TaskStateChangedEvent` record to `jaiclaw-core` hook events
- [ ] Add to `HookEvent` permits
- [ ] Update every exhaustive switch (kept in same PR)
- [ ] Update `KanbanHookFirer` to publish the first-class event; keep mapped-event firer as deprecated fallback for one release cycle

**TaskStoreProvider & TenantRoutingTaskStore**
- [ ] Implement `TenantRoutingTaskStore` (route via `TenantGuard`; fallback to default)
- [ ] Add `RedisTaskStoreProvider` (optional dep on `spring-boot-starter-data-redis`; key-prefix per tenant; CAS via `WATCH`/`MULTI` or Lua)
- [ ] Add `JdbcTaskStoreProvider` (optional dep on `spring-jdbc`; tenant column + CAS via `UPDATE ... WHERE id=? AND version=?` row-count)
- [ ] Write `TaskStoreContractSpec` (abstract Spock spec); run against JSON, Redis (Testcontainers), JDBC (Testcontainers Postgres)
- [ ] Update `KanbanRecoveryManager` to iterate `(tenantId, store)` pairs with tenant context per pass

**H2 stores**
- [ ] `H2TaskStore` + `H2BoardStore` (cron-manager precedent) with transactional WIP checks
- [ ] Add `claimedBy`/`leaseUntil` columns for lease-based multi-instance recovery; renewal as heartbeat from §6.2
- [ ] Activate behind `@ConditionalOnClass(H2.class)` + `jaiclaw.tasks.storage.type: h2`

**Transition journal**
- [ ] `jaiclaw.kanban.history.journal=true` enables append-only `{boards-dir}/../journal/{boardId}.jsonl`
- [ ] On startup, `TransitionHistory` deque becomes a cache over the journal tail

**Dashboard UI**
- [ ] Track as single checkbox here; link to the separate repo/app once created — out of this repo's scope

**External E2E + skill**
- [ ] Create `jaiclaw-examples/kanban-demo` Spring Boot app with fixture board + stub agent runner
- [ ] Write demo README (Problem / Solution / Architecture / Design / Build & Run per repo rule)
- [ ] Configure `jaiclaw.skills.allow-bundled: []` and add `jaiclaw-maven-plugin` `jaiclaw:analyze` per examples rule in `CLAUDE.md`
- [ ] Create `.claude/skills/kanban-e2e/SKILL.md` mirroring `e2e-test`; boots demo app, runs curl scenarios, captures SSE, byte-compares ASCII
- [ ] Run skill end-to-end against the demo app; commit golden artifacts under the skill

**Docs sync (single checkbox covers all)**
- [ ] Update `CLAUDE.md` (kanban section in architecture; module counts ticked up to include `jaiclaw-kanban`), `docs/dev/ARCHITECTURE.md` (dependency graph + section), `docs/user/OPERATIONS.md` (config surface from analysis §7), `/Users/tap/dev/docs/jaiclaw/JAICLAW-DEVELOPER-GUIDE.md` + `dev-guide/` satellites — retire any remaining analysis-§1 drift

### 9.5 Verification

`./mvnw verify -pl :jaiclaw-tasks,:jaiclaw-kanban,:jaiclaw-example-kanban-demo -am` plus
the out-of-process `kanban-e2e` skill run.

### 9.6 Risk & rollback

First-class `HookEvent` is the only core touch in the whole plan — gated on
the exhaustive-switch audit and shipped in one PR. Backward compat: keep the
mapped-event firer (Phase 1's path) as a deprecated fallback for one release
cycle. Per-tenant routing and H2 stores are opt-in via property + classpath;
default deployment remains JSON-file single-store, identical to today.

---

## 10. Cross-cutting checklist

Applied per task where relevant — do not duplicate this in each phase.

- **Multi-tenancy** (analysis §3.5, §6.7): inject `TenantGuard`, never call
  `TenantContextHolder` directly; wrap async fan-out (SSE, column processor
  submissions, recovery sweeps) in `TenantContextPropagator`; per-tenant
  path/key prefixing in stores; SINGLE-mode null-safe; MCP tools forward
  `TenantContext`.
- **Spock spec parity**: every production class lands with at least one
  Spock spec in the same commit. Specs end in `Spec`.
- **SPI freeze marker**: end of Phase 2 freezes `TaskStateEngine`,
  `TaskStoreProvider`, `BoardStore`, and the `TaskStateChanged` event
  payload shape. Changes after that need a deprecation note appended in
  §10 Decision log.
- **House style**: explicit types over `var`; favor `@Configuration` + `@Bean`
  over `@Component`; immutable records for value types; sealed interfaces
  where exhaustiveness matters.

---

## 11. Open questions

Linked to analysis §9 unless added during execution.

| # | Question | Status |
|---|---|---|
| Q1 | Board definition CRUD over REST — persist, in-memory only, or 405? | **Open** — must resolve before Phase 2 controller merges; see Phase 2 task list |
| Q2 | Card ↔ flow relationship — reuse `TaskRecord.flowId` for `boardId` or use a dedicated field? | **Resolved 2026-06-12: dedicated `boardId` field added in Phase 1** (Option A from analysis §4) |
| Q3 | Retention — archive terminal cards to `done/` shard after N days, or rely on history bound only? | **Open** — revisit in Phase 4 alongside the journal |
| Q4 | Embabel processor target — should a column's processor target an Embabel `@Agent` action? | **Open** — the `Function` indirection permits it without design change; consider a Phase 3 example |
| Q5 | `spring-statemachine-core` version pin compatible with Boot 3.5.14 / Spring 6.2 | **Open** — must resolve in Phase 3 SSM engine task |

---

## 12. Decision log

Append-only; records decisions, not commits.

- **2026-06-12** — initial plan written from approved design analysis;
  user-locked: all four phases shippable; boards dir
  `~/.jaiclaw/kanban/boards`; SPI in Phase 1, SSM impl in Phase 3; in-module
  Spring E2E per phase + demo app + `kanban-e2e` skill in Phase 4.
- **2026-06-12** — Q2 resolved: dedicated `boardId` field on `TaskRecord`;
  `flowId` retained for `TaskFlow`. `idempotencyKey` added in Phase 1 (not
  Phase 3) to avoid a second migration.
- **2026-06-12** — Phase 1 closed. Notable in-flight decisions while
  executing: (a) `TaskRecord` keeps a legacy 13-arg constructor that
  delegates to the canonical 19-arg form so existing call sites stayed
  untouched (no spec churn outside the tasks module); (b) Phase 1
  `KanbanCoreE2ESpec` uses direct constructor wiring, not `@SpringBootTest`
  — bringing up a Spring context just to exercise wiring without any
  surfaces gives no extra coverage. Phase 2 will pick up the real
  Spring-context E2E when the REST/SSE surfaces land.
