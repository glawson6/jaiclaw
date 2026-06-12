# JaiClaw Kanban — Implementation Plan

**Status:** **Complete** — all four phases landed. 236/236 specs green, install gate clean. Maintenance follow-ups (if any surface) tracked inline in §11 Open Questions.
**Companion analysis:** [`KANBAN-TASK-PROCESSING-ANALYSIS.md`](./KANBAN-TASK-PROCESSING-ANALYSIS.md) — design rationale; this plan is the execution map.
**Started:** 2026-06-12
**Last updated:** 2026-06-12 — Phase 3 closed (125/125 kanban specs + 24/24 tasks specs, install clean).

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

**Resume here →** all Phase 2 groups complete; jump to Phase 3 §8. | last touched: `KanbanSurfacesE2ESpec.groovy` (E2E landed; 90/90 kanban specs + 24/24 tasks specs green; install clean)

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
- [x] **Analysis §9 Q1 resolved 2026-06-12** — YAML-as-BoardStore. `POST /api/kanban/boards` registers in-memory AND writes `{boards-dir}/{boardId}.yaml` atomically (tmp + ATOMIC_MOVE). `DELETE /api/kanban/boards/{id}` unregisters + removes the file. Writes gated by `jaiclaw.kanban.boards.writable` (default true). Cards stay dynamic regardless of this flag.

**Board persistence (Phase 2 prerequisite)**
- [x] Create `BoardStore` SPI in `io.jaiclaw.kanban.persistence` — `save/delete/findById/findAll/count`; YAML files are the durable record
- [x] `YamlFileBoardStore` impl: atomic per-file writes (tmp + ATOMIC_MOVE), reads on bootstrap, writes through on `save/delete`; malformed files skipped
- [x] `KanbanBoardService` accepts an optional `BoardStore` + `writable` flag — `register/remove` dual-write or throw `BoardWriteException`; `cache/cacheAll` is the bootstrap-only path that bypasses the store
- [x] Add `jaiclaw.kanban.boards.writable` flag to `KanbanProperties.Boards` (default true), with a legacy 9-arg constructor so Phase 1 callers keep compiling
- [x] `KanbanAutoConfiguration` wires `YamlFileBoardStore`, threads `boards.writable` into the service, bootstrap merges classpath/file boards (from `locations.patterns`) with store-loaded boards (store wins on id conflict)
- [x] Spock specs: `YamlFileBoardStoreSpec` (6 — write/roundtrip/delete/order/atomic/malformed-skip); `KanbanBoardServiceSpec` (6 — memory-only, persistent roundtrip, write-rejection in both directions, cache vs register, isWritable)

**REST + SSE**
- [x] Create `KanbanBoardController` with all read endpoints (boards list, get, snapshot, history, task detail, ASCII rendering)
- [x] Add write endpoints: `POST /boards/{id}/tasks`, `POST /tasks/{id}/transition`, `POST /tasks/{id}/claim`, `POST /boards`, `DELETE /boards/{id}` (latter two return 405 when `writable=false`)
- [x] Implement `409 + reason` on rejected transitions (guard, WIP, unknown event, version conflict)
- [x] Configurable base path `jaiclaw.kanban.http.base-path` (default `/api/kanban`); tenant resolution from `TenantContext` via existing `TenantGuard` injection (no per-controller header parsing — Spring Security delegated)
- [x] `BoardSnapshotService` builds `BoardSnapshot` for the controller, SSE on-connect, ASCII renderer, and the upcoming actuator endpoint
- [x] `KanbanWebConfiguration` separate auto-config: `@ConditionalOnClass(RestController)` + `@ConditionalOnProperty(jaiclaw.kanban.http.enabled, default true)`
- [x] Create `KanbanEventController` (`GET /boards/{id}/events`, text/event-stream) + `KanbanEventBroadcaster` SmartLifecycle bean — emitter pool per `(tenantId, boardId)`; first event on connect is full `BoardSnapshot` named `snapshot`; subsequent `state-changed` events fan out per `TaskStateChanged`; heartbeat per `jaiclaw.kanban.sse.heartbeat-seconds`; max-connections per `jaiclaw.kanban.sse.max-connections` returns 429
- [x] `TenantContextPropagator` wraps the heartbeat scheduler task; per-event fan-out runs on the publisher thread, which already carries the right tenant context (analysis §3.5)
- [x] Gated on `jaiclaw.kanban.sse.enabled` (default true) via the existing `KanbanWebConfiguration` (which already requires `RestController` on the classpath); spring-webmvc added as an optional dep for `SseEmitter`
- [x] Spock specs: `KanbanBoardControllerSpec` (13, full RANDOM_PORT integration through Tomcat — every endpoint with happy + edge paths); `KanbanBoardControllerReadOnlySpec` (3, asserts 405 on board writes when `writable=false` and that card endpoints still work); `KanbanEventBroadcasterSpec` (6, unit-level fan-out/dedup/max-cap/deregister/stop); `KanbanEventControllerSpec` (3, full Tomcat RANDOM_PORT — opens raw SSE via java.net.http, asserts snapshot-on-connect, state-changed delivered post-transition, heartbeat keeps stream alive)

**ASCII renderer**
- [x] `BoardAsciiRenderer.render(BoardSnapshot, AsciiBoardOptions)` — FULL style draws outer frame + per-column boxed cards directly on `jaiclaw-ascii-render`'s `Canvas`; COMPACT style emits a state/id/name table. Long names + descriptions wrap+truncate with `…`. Empty columns show the configured `emptyMarker`.
- [x] `text/plain` endpoint `GET /api/kanban/boards/{id}/ascii?width=N&style=full|compact` (landed with the REST controller group)
- [x] Commit golden snapshots under `src/test/resources/boards/golden/` (`demo-board-full.txt`, `demo-board-compact.txt`)
- [x] Spock spec `BoardAsciiRendererSpec` — golden compare for FULL + COMPACT; empty-marker; title-bar clamping; empty-board safety (5 specs)

**MCP + agent tools**
- [x] `KanbanMcpToolProvider` (server name `kanban`) with `getTools()` + `execute(toolName, args, TenantContext)` for `board_list`, `board_show`, `board_ascii`, `task_move`, `task_claim` — same five operations the agent tools expose, returns JSON-encoded results
- [x] Implement agent tools `task_move`, `task_claim`, `board_show`, `board_ascii`, `board_list` via `AbstractBuiltinTool`. **Decision:** these five tools do not need the `Function<TaskRecord,String>` agent-runner indirection — they are pure read/write operations on kanban state, not agent-invoking tools. The Phase 1 forward-decision was anticipating a future agent-backed tool; that tool doesn't appear in this list and will be added with the right signature in Phase 3 if needed. Recorded in §12.
- [x] Register tools through `ToolRegistry` from `KanbanAutoConfiguration` via `KanbanTools.registerAll`; gated on `@ConditionalOnBean(ToolRegistry.class)` so non-tool embeddings stay clean
- [x] Spock specs: `KanbanToolsSpec` (10, one per tool covering success/error/edge cases + factory shape); `KanbanMcpToolProviderSpec` (10, server identity, tools/list shape, every tool execution path including rejection mapping and unknown-tool)

**Actuator**
- [x] `KanbanActuatorEndpoint` at `@Endpoint(id="kanban")` mirroring `PipelineActuatorEndpoint` — list() returns engine info + board summaries, byId() returns definition + recent transitions. Separate `KanbanActuatorConfiguration` auto-config gated on `@ConditionalOnClass(Endpoint)` + `jaiclaw.kanban.actuator.enabled` (default true)
- [x] `KanbanActuatorEndpointSpec` (3): list shape, byId with recorded transitions, byId unknown returns error map

**E2E**
- [x] `KanbanSurfacesE2ESpec` per §5.2 Phase 2 row (2 specs): boot with classpath-loaded fixture board (`e2e-content-review.yaml`); then the big integration: SSE-open → snapshot event → `POST /tasks` dynamic card → CREATE event over SSE → REST transition → START event over SSE → ASCII rendering → MCP `tools/list` returns five tools → MCP `tools/call task_move` advances the card → SUBMIT event over SSE → `/actuator/kanban` + `/actuator/kanban/{id}` show the board and recent transitions. MCP invoked via the provider SPI bean (the gateway's HTTP MCP hosting is out of scope for a kanban-extension spec).

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

**Resume here →** all Phase 3 groups complete; jump to Phase 4 §9. | last touched: `KanbanEngineSwapE2ESpec.groovy` (Phase 3 closed — 125/125 kanban + 24/24 tasks specs green; install clean)

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
- [x] `ColumnProcessorManager` listens for `TaskStateChanged`; if `column.processor` set, runs the work on its own virtual-thread executor (**not** `TaskExecutor.submit` — that overwrites the state field; see §12 decision log)
- [x] App-provided `Function<TaskRecord,String> kanbanAgentRunner` bean injected into `AgentColumnProcessor` (cron pattern — kanban module never compiles against `AgentRuntime`)
- [x] `AgentColumnProcessor` renders prompt template with `{{name}}`, `{{description}}`, `{{attempt}}`, `{{idempotencyKey}}` per analysis §6.8
- [x] On handler success fire `column.processor.onSuccess` event into the state engine; on exception fire `column.processor.onFailure`. `onSuccess`/`onFailure` are **transition event names**, not column ids — recorded in §12 decision log so future readers don't mis-configure boards
- [x] WIP-limit enforcement at engine guard (already landed in Phase 1's `TransitionGraphStateEngine`; `SpringStateMachineEngine` mirrors it byte-for-byte)

**Recovery**
- [x] `KanbanRecoveryManager` `SmartLifecycle` bean — runs after stores load; iterates RUNNING + QUEUED processor-column cards
- [x] Apply per-column `restartPolicy` (`fail` default | `requeue` | `manual`) per analysis §6.2 table; `maxAttempts` cap with fall-through to `fail`
- [x] FAIL fires `onFailure` with reason `"interrupted by restart"` (or `"stale-running timeout"`); REQUEUE bumps `kanban.attempts` and republishes a synthetic `TaskStateChanged(event="RECOVERY")` so the processor manager re-runs under the same idempotency key; MANUAL marks `kanban.interrupted=true`
- [x] `StaleRunningDetector` `SmartLifecycle` runs a periodic sweep against `staleRunningTimeout` (parsed: `30m`/`2h`/`45s`/raw seconds)

**Idempotency**
- [x] `IdempotencyKeyBuilder` produces `{boardId}:{taskId}:{state}:{entrySeq}` via `TransitionHistory.entrySeq`
- [x] `EffectLedger` is an append-only jsonl at `{boards-dir}/../effects/effects.jsonl` — survives restart; idempotent re-records skip writing
- [x] `AgentColumnProcessor.process` consults `EffectLedger.lookup(key)` before invoking the runner; recorded result is replayed directly (no second agent call)
- [x] `BoardValidator` rejects `restartPolicy: requeue` on `idempotent: false` columns (Phase 1 carried this forward — `BoardValidatorSpec.requeue requires idempotent=true`)

**Spring State Machine engine**
- [x] Pinned `<spring-statemachine.version>4.0.1</spring-statemachine.version>` in root pom (targets Spring 6 / Boot 3.x; downloadable + buildable verified)
- [x] `BoardStateMachineFactory` builds machines from `BoardDefinition` via `StateMachineBuilder`; caches per board id; exposes `invalidate(boardId)`
- [x] `SpringStateMachineEngine` is stateless: SSM machine is built only to validate the graph structure (surfaces config errors early); per-event fire walks the transition list directly. No live per-task machine, no `StateMachinePersister`. Identical accept/reject semantics to the graph engine. See §12 decision log for the divergence from the plan's recipe.
- [x] Activated by `@ConditionalOnClass(StateMachine.class)` + `jaiclaw.kanban.engine.name=spring-statemachine` via `SpringStateMachineConfiguration`. `@AutoConfigureBefore(KanbanAutoConfiguration.class)` so the default graph engine's `@ConditionalOnMissingBean` steps aside.

**E2E**
- [x] `KanbanPhase3E2ESpec` per §5.2 Phase 3 row — `@SpringBootTest(NONE)` against fixture `e2e-processor-fail.yaml`. Subsumes `KanbanRecoveryE2ESpec` + `KanbanIdempotencyE2ESpec`: proves processor runs, EffectLedger replays the recorded result, FAIL restartPolicy routes a stuck card to `blocked`, default engine is `TransitionGraphStateEngine`. (4 tests)
- [x] `KanbanEngineSwapE2ESpec` per §5.2 third bullet — `engine.name=spring-statemachine` swaps the bean, walks the same `backlog→drafting→review→done` chain. (1 test)

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

**Resume here →** **Phase 4 complete.** All groups landed (1: first-class HookEvent, 2: transition journal, 3: H2 stores, 4a: TaskStoreProvider routing + JDBC, 4b: Redis + Postgres, 5: dashboard UI out-of-scope marker, 6: kanban-demo + skill, 7: docs sync). 144/144 kanban specs + 89/89 tasks specs + 3/3 kanban-demo smoke spec = **236/236 green**; install gate clean. Last touched: `docs/user/OPERATIONS.md` (kanban configuration section).

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

**TaskStoreProvider & TenantRoutingTaskStore (group 4a — landed)**
- [x] `TenantRoutingTaskStore` routes each call to a per-tenant backing store via `TenantGuard`; SINGLE-mode hits the default store; explicitly-registered backends override; missing tenants fall through to default. `withTenant(tenantId, Runnable)` helper for context-scoped sweep iteration.
- [x] `JdbcTaskStoreProvider` supports `"jdbc"` and `"h2"` type names, builds an `H2TaskStore` over a `SimpleDriverDataSource` from `{url, user, password}` config.
- [x] `TaskStoreContractSpec` (abstract Spock base) pins the SPI invariants every backend must honour: round-trip, CAS (new-id / matching / stale), status/board filters, delete, count, findAll. Concrete subclasses `JsonTaskStoreContractSpec` (10) and `JdbcH2TaskStoreContractSpec` (10) prove JSON + H2 conform.
- [x] `KanbanRecoveryManager.sweep` now detects a `TenantRoutingTaskStore` and iterates `(tenantId, store)` pairs with `withTenant` per pass; the default store is swept last under no context. Non-routed stores stay on the original single-pass path. `KanbanRecoveryRoutingSpec` proves the routed sweep finds stuck cards across multiple tenants.

**Group 4b — Redis + Postgres-via-Testcontainers (landed)**
- [x] `RedisTaskStore` + `RedisTaskStoreProvider` — JSON payload at `{prefix}:{tenantId}:{taskId}` with parallel SET-based indexes for status / board-state / all-ids. `compareAndSave` uses `WATCH`/`MULTI`/`EXEC` for atomic CAS, with a small retry loop for benign contention. `save`/`deleteById` use `SessionCallback` transactions to keep index maintenance atomic with the value write.
- [x] Postgres contract spec via the same `H2TaskStore` class — the SQL the store emits is portable, only the schema needed a `CLOB → TEXT` swap. Schema lives at `src/main/resources/schema-postgres.sql`.
- [x] Testcontainers as new test infrastructure: `testcontainers:1.20.3` + `testcontainers:postgresql` + `redis:testcontainers-redis:2.2.2`. Each contract spec starts its container once (`@Shared`) and truncates/flushes per test.
- [x] Production deps added (all optional): `spring-boot-starter-data-redis`, `redis.clients:jedis`, `org.postgresql:postgresql`.
- [x] Specs: `JdbcPostgresTaskStoreContractSpec` (10 — same 10 tests as JSON/H2 contract specs, against real Postgres), `RedisTaskStoreContractSpec` (10 — same suite against real Redis), `RedisTaskStoreProviderSpec` (2 — supports check + roundtrip-through-create).

**H2 stores**
- [x] `H2TaskStore` in `jaiclaw-tasks` — full `TaskStore` SPI implementation: tenant-scoped reads (composite PK `(id, tenant_id)` so two tenants can share an id, same guarantee as `JsonFileTaskStore`), `compareAndSave` via row-count CAS (`UPDATE ... WHERE id=? AND version=?`), `findByBoardAndState` indexed by `(board_id, state)`. Metadata stored as JSON CLOB so future field growth is schema-free.
- [x] `H2BoardStore` in `jaiclaw-kanban` — `BoardStore` SPI implementation. Whole `BoardDefinition` lives as a JSON CLOB in `definition_json`, so columns/transitions/processor definitions evolve without DDL. Tenant scoping stays on `BoardDefinition.tenantIds()` (Phase 1 convention).
- [x] Lease columns (`claimed_by`/`lease_until`) on the task table for multi-instance recovery (analysis §6.5). New `H2TaskStore` methods: `claim`, `renewLease`, `releaseLease`, `findExpiredLeases`. Claim succeeds when nobody holds it, when the existing lease has expired, or when the caller already holds it. Phase 3's `KanbanRecoveryManager` will wire this into the sweep loop in a follow-up.
- [x] Activated behind `@ConditionalOnClass({JdbcTemplate.class, H2.Driver.class})` + `jaiclaw.tasks.storage.type=h2` / `jaiclaw.kanban.boards.type=h2`. Two separate auto-configs (`H2TaskPersistenceAutoConfiguration` in tasks, `H2BoardPersistenceAutoConfiguration` in kanban) so each can be enabled independently. `@AutoConfigureBefore` the default JSON / YAML config so the H2 bean wins the `@ConditionalOnMissingBean` race.
- [x] Schemas ship as `schema.sql` (tasks: `jaiclaw_tasks`) and `schema-kanban.sql` (kanban: `jaiclaw_kanban_boards`) — separate filenames so Spring Boot's SQL init loads both without collision.
- [x] Production deps: `spring-boot-starter-jdbc` + `h2` added as optional on both modules (BOM-managed versions).
- [x] Specs: `H2TaskStoreSpec` (13 — full round-trip, CAS success/conflict, status/board filters, delete, MULTI-tenant isolation, full lease lifecycle, expired-lease enumeration); `H2BoardStoreSpec` (5 — round-trip, MERGE replaces row, findAll ordering, delete, missing-id empty).

**Transition journal**
- [x] `jaiclaw.kanban.history.journal=true` enables `TransitionJournal` — one append-only JSONL per board at `{boards-dir}/../journal/{boardId}.jsonl`. Subscribes to `TaskStateChanged` so journal-append is the same code path as SSE fan-out and the processor manager
- [x] `SmartLifecycle.start()` reads each board's tail (capped at `history.max-per-board`), sorts by timestamp, and replays into `TransitionHistory` — the bounded deque survives a restart. Malformed JSONL lines are skipped; missing journal dir is a no-op. Idempotent: second `start()` is a no-op
- [x] `TransitionJournalSpec` (9 tests): append, event listener happy + null-board ignored, replay ordering + cap, malformed-line skip, idempotent start, missing dirs

**Dashboard UI**
- [x] **Out of this repo's scope** per the plan. The Phase 2 REST + SSE contract (analysis §3.5, plan §7) is the integration point — any client written against `GET /api/kanban/boards/{id}/snapshot` for the initial paint, the SSE stream at `GET /boards/{id}/events` for live deltas, and `POST /tasks/{id}/transition` for user actions is a valid dashboard. No specific UI is shipped from this repository. Tracked as resolved here so future sessions don't re-open the checkbox.

**External E2E + skill (group 6 — landed)**
- [x] `jaiclaw-examples/kanban-demo` runnable Spring Boot app. Fixture board at `src/main/resources/jaiclaw/kanban/boards/demo.yaml` (backlog → drafting [with processor] → review → done + blocked side state). Stub `kanbanAgentRunner` returns `"DRAFT: {card name}"` deterministically — no LLM key needed. `KanbanDemoApplicationSpec` (3 tests) is the in-module smoke check; the real end-to-end run is the skill.
- [x] README covers Problem / Solution / Architecture / Design / Build & Run per the examples rule. `jaiclaw.skills.allow-bundled: []` configured per the CLAUDE.md examples rule.
- [x] `.claude/skills/kanban-e2e/SKILL.md` mirroring the existing `e2e-test` skill — 6 phases (build / boot / surface checks / card lifecycle / SSE / teardown) with its own task-list convention so a user invoking it sees per-phase progress.
- [x] Golden ASCII at `.claude/skills/kanban-e2e/golden/demo-board-empty-compact.txt`, captured against the running demo and byte-compared in skill Phase 3.
- [x] **Carve-out:** the demo example pom omits `jaiclaw-maven-plugin`'s `analyze` goal (required by the examples rule when an example drives an LLM). The plugin transitively requires `jaiclaw-project-scaffolder` and `jaiclaw-prompt-analyzer`, which aren't always installed locally; `allow-bundled: []` makes the token-budget check a no-op anyway. Recorded inline in the pom comment so future contributors adding LLM-driving behaviour know to restore the plugin.

**Docs sync (group 7 — landed)**
- [x] `CLAUDE.md` — module count bumped to include the kanban entry from Phase 1 drift catch-up; example count bumped to 40 to include `kanban-demo`; the kanban one-liner refreshed from "Phase 2 adds REST/SSE...Phase 4 adds first-class HookEvent" (stale future-tense) to a single shipped-state summary listing every Phase 1–4 deliverable plus the demo + skill.
- [x] `docs/dev/ARCHITECTURE.md` — added a `jaiclaw-tasks` line (it was absent — analysis §1 drift) and a `jaiclaw-kanban` line under the module dependency graph. Added two rows to the "What Exists vs What's Needed" table (async task store SPI; kanban boards).
- [x] `docs/user/OPERATIONS.md` — new "Kanban Configuration" section right after "Pipeline Configuration": starter dependency, opt-in property, board sources (YAML files + classpath patterns), full REST + SSE endpoint table, state-engine swap to SSM, column processor `kanbanAgentRunner` recipe, persistence backends, actuator endpoint, pointer to demo app.
- [x] Dev-guide satellites at `/Users/tap/dev/docs/jaiclaw/` are user-private docs outside this repo; the kanban module's plan + analysis + this plan file are the canonical references and are committed in `docs/dev/`. Sync of the private satellites is out of repo scope.

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
| Q1 | Board definition CRUD over REST — persist, in-memory only, or 405? | **Resolved 2026-06-12: YAML-as-BoardStore.** `POST /api/kanban/boards` registers in memory AND writes `{boards-dir}/{boardId}.yaml` atomically (tmp + ATOMIC_MOVE, same pattern as `JsonFileTaskStore`). `DELETE /api/kanban/boards/{id}` unregisters + removes the file. Writes are gated by `jaiclaw.kanban.boards.writable` (default true) — false returns 405 from write endpoints for ops-locked deployments. YAML wins as the durable record; REST is the source of truth at write time. Hand-edits to YAML survive across restart (boot reads YAML); subsequent REST writes overwrite. Phase 4 H2 `BoardStore` swaps in behind the same SPI without API break. |
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
- **2026-06-12** — Q1 resolved: YAML-as-BoardStore. `POST /api/kanban/boards`
  registers in memory and writes `{boards-dir}/{boardId}.yaml` atomically;
  `DELETE` unregisters and removes the file. Gated by
  `jaiclaw.kanban.boards.writable` (default true). Chosen over a separate
  JSON store because YAML files give ops `cat`/`git`/`diff` workflows
  identical to today, with a single source of truth (the directory).
  Hand-edits survive boot; subsequent REST writes overwrite. Phase 4 H2
  store swaps in behind the same `BoardStore` SPI without API break.
- **2026-06-12** — Repo-wide gitignore bug surfaced during REST controller
  work: root `.gitignore` matches `META-INF/`, which silently excludes every
  module's `src/main/resources/META-INF/spring/AutoConfiguration.imports`.
  Existing module tests work because Maven copies the file into
  `target/classes` from the working copy, but a fresh `git clone` build of
  any auto-configured module would be missing autoconfig discovery. The
  kanban commit force-adds its imports file via `git add -f`. Broader fix
  (audit + force-add every module's imports file, or scope the
  `META-INF/` rule to `target/`) is **out of scope** for the kanban work
  but should be raised as a separate cleanup.
- **2026-06-12** — Phase 2 REST controller in-flight calls. (a) Test
  isolation: each `KanbanBoardControllerSpec` test wipes
  `BoardStore`/`TaskStore`/`TransitionHistory` in `setup()`, so specs run
  independently regardless of order. Cheaper than `@DirtiesContext` per
  method (which would rebuild the Tomcat context every test). (b)
  Multiple `@SpringBootApplication` test apps in the same package cause
  bean-definition collisions because `@SpringBootApplication` component-scans
  the package — move each test app's class into its own sub-package
  (`web/writable/`, `web/readonly/`). (c) `KanbanProperties` has two
  constructors (canonical 10-arg + legacy 9-arg delegating) so Spring's
  binder needs `@ConstructorBinding` on the canonical one to pick. (d)
  `spring-boot-starter-web` is now a test-scope dep so the controller spec
  can boot a real RANDOM_PORT Tomcat. Production stays on optional
  `spring-web` only.
- **2026-06-12** — Phase 2 SSE notes. Dropped `produces = text/event-stream`
  from the SSE route so 404 error responses can be JSON (Spring
  content-negotiation otherwise returns 406 on 404). `spring-webmvc`
  added as an optional production dep — `SseEmitter` lives there, not in
  `spring-web`. Per-event fan-out runs on the publisher thread, which
  already carries the right `TenantContext` from the transition service;
  only the heartbeat scheduler needs `TenantContextPropagator` wrapping.
- **2026-06-12** — Phase 2 agent-tool decision. The Phase 1 forward-call
  said agent tools should accept the `Function<TaskRecord,String>`
  agent-runner indirection so Phase 3 could plug processors in without
  refactoring. Looking at the five named tools (`task_move`,
  `task_claim`, `board_show`, `board_ascii`, `board_list`), none of them
  *invoke* the agent — they are CRUD/read operations on kanban state.
  The runner indirection is for the Phase 3 `ColumnProcessorManager`,
  which is a separate bean, not one of these tools. Resolved by
  **not** adding the indirection here; if Phase 3 introduces a
  literally-agent-backed tool (e.g. `ask_agent_about_card`) it'll be
  added then with the right signature from the start.
- **2026-06-12** — Phase 2 done. Final counts: 90/90 jaiclaw-kanban specs +
  24/24 jaiclaw-tasks specs, install gate clean. `KanbanSurfacesE2ESpec`
  boots the full auto-config stack against the §5.1 fixture and drives
  REST + SSE + ASCII + MCP + Actuator in a single spec. Test scope grew
  with `spring-boot-starter-web` + `spring-boot-starter-actuator`;
  production scope grew with optional `spring-webmvc`.
- **2026-06-12** — Phase 3 in-flight calls.
  (a) **Processor lifecycle vs `TaskExecutor`**: the plan called for using
  `TaskExecutor.submit(card, handler)` from the column processor manager.
  In practice, `TaskExecutor.executeTask` writes `task.withResult(...)`
  after the handler returns, which overwrites the `state` field a
  concurrent transition just advanced. Solution: the manager owns its
  own virtual-thread `Executor` and writes the result in-place
  (`persistResult`) without touching state, status, or version. Tests
  pass a synchronous executor for deterministic ordering.
  (b) **`onSuccess`/`onFailure` are event names, not column ids**.
  Phase-3-aware board YAML uses the transition event name
  (`SUBMIT`/`BLOCK`/`APPROVE`), not the destination column id
  (`review`/`blocked`/`done`). Test fixtures that mixed these up silently
  no-op'd. The plan + fixture YAML are now explicit.
  (c) **REQUEUE re-publish path**: rather than introduce a self-loop
  transition event, the recovery manager directly publishes a synthetic
  `TaskStateChanged(event="RECOVERY", fromState=toState=current)`. The
  `ColumnProcessorManager` re-enters under the same idempotency key, so
  retry semantics are clean.
  (d) **SSM engine stateless-ness**: the plan's recipe (per-transition
  rehydrate via `DefaultStateMachineContext`, machine pool) is heavier
  than needed for a flat kanban graph. The implementation builds the
  SSM machine via `StateMachineBuilder` (which surfaces graph config
  errors early) but walks the transition list directly when firing.
  Same accept/reject semantics as the graph engine, no per-transition
  reactive plumbing. The factory caches the built machine per board id.
  (e) **SSM property name** — `jaiclaw.kanban.engine.name=spring-statemachine`,
  not `jaiclaw.kanban.engine=spring-statemachine` as the plan abbreviated;
  the underlying `KanbanProperties.Engine` is a sub-record so the dotted
  path includes the field name.
  (f) Phase 3 E2E consolidated to one `KanbanPhase3E2ESpec` plus
  `KanbanEngineSwapE2ESpec`, rather than separate `KanbanRecoveryE2ESpec`
  and `KanbanIdempotencyE2ESpec` as the plan listed. Per-policy outcomes
  are already exhaustively covered by `KanbanRecoveryManagerSpec`
  (6 specs) and idempotency replay by `AgentColumnProcessorSpec`; the
  E2E spec proves the autoconfig wiring instead of re-running matrix
  coverage.
- **2026-06-12** — **Phase 4 group 4 split (4a / 4b).** The plan listed
  TaskStoreProvider routing + Redis + JDBC + a contract spec against all
  three as one group. In practice the Redis half requires introducing
  Testcontainers as new test infrastructure (none of jaiclaw currently
  uses it) and a `WATCH`/`MULTI` or Lua CAS implementation. Split this
  group so the SPI shape lands cleanly without the Testcontainers
  install:
    **4a (landed):** `TenantRoutingTaskStore`, `JdbcTaskStoreProvider`
    (H2-flavoured), shared `TaskStoreContractSpec` running against JSON
    + H2 to pin SPI semantics, recovery sweep iterates routed backends.
    **4b (landed in the next commit):** `RedisTaskStore` +
    `RedisTaskStoreProvider` with `WATCH`/`MULTI`/`EXEC` CAS, Postgres
    contract spec via the same `H2TaskStore` class (SQL is portable —
    only the `CLOB → TEXT` schema swap), Testcontainers as new test
    infrastructure (first use in jaiclaw). The shared
    `TaskStoreContractSpec` is the gate every backend extends.
- **2026-06-12** — **Testcontainers introduced** as first-time test
  infrastructure for jaiclaw via Phase 4b. Coordinates pinned:
  `testcontainers:1.20.3` (BOM) + `testcontainers:postgresql:1.20.3` +
  `redis:testcontainers-redis:2.2.2`. Container start cost: ~2s for
  Redis, ~9s for Postgres on first run (image pull cached after).
  Subsequent contributors who add Testcontainers-backed specs should
  reuse the `@Shared` container + per-method truncate/flush pattern
  established in `JdbcPostgresTaskStoreContractSpec` /
  `RedisTaskStoreContractSpec` to keep per-method cost in milliseconds.
  The Redis class refactor used anonymous-inner `SessionCallback`
  rather than lambdas — Java compiles the lambda form but the JVM
  verifier rejects it because `SessionCallback.execute` has a generic
  type parameter the lambda can't bind.
- **2026-06-12** — Phase 3 done. Final counts: 125/125 jaiclaw-kanban
  specs + 24/24 jaiclaw-tasks specs, install gate clean. Production
  scope grew with optional `spring-statemachine-core:4.0.1`. Resume
  pointer moves to Phase 4 §9 (Hardening & docs).
- **2026-06-12** — **Phase 4 closed.** Final counts: 89/89 jaiclaw-tasks
  + 144/144 jaiclaw-kanban + 3/3 kanban-demo = 236/236 specs green.
  Install gate clean. Production deps gained (all optional):
  `spring-boot-starter-jdbc`, `h2`, `spring-boot-starter-data-redis`,
  `jedis`, `postgresql`. Test deps gained: Testcontainers `1.20.3` +
  `testcontainers:postgresql` + `redis:testcontainers-redis:2.2.2`.
  Notable in-flight decisions during Phase 4:
  (a) **Group 4 split into 4a/4b** (already documented above).
  (b) **Testcontainers introduced** for the first time — pattern
  `@Shared container + per-method truncate/flush` keeps per-test cost
  in milliseconds despite the per-spec-class container startup of
  ~2s (Redis) or ~9s (Postgres on amd64-emulated arm64).
  (c) **`RedisTaskStore` SessionCallback uses anonymous-inner classes**,
  not lambdas. The JVM verifier rejects lambdas binding
  `SessionCallback<K, V>.execute` because the generic type parameters
  can't be erased through a SAM conversion target.
  (d) **Postgres reuses `H2TaskStore`** unchanged. The store's SQL is
  portable; only the schema differs in `CLOB → TEXT`. New
  `schema-postgres.sql` lives alongside `schema.sql` and apps select
  via `spring.sql.init.schema-locations`.
  (e) **Dashboard UI marker resolved** (group 5) as out-of-repo-scope
  per the original plan — the Phase 2 REST + SSE contract is the
  integration point; no UI is shipped from this repository. Tracked
  as `[x]` to keep the resume pointer clean.
  (f) **`kanban-demo` example pom omits `jaiclaw-maven-plugin`'s
  `analyze` goal**. Plugin transitively requires
  `jaiclaw-project-scaffolder` + `jaiclaw-prompt-analyzer` which
  aren't always installed locally; `jaiclaw.skills.allow-bundled: []`
  makes the analyzer's token-budget check a no-op anyway since the
  demo doesn't drive an LLM. Recorded inline in the pom.
  (g) **Demo uses `jaiclaw-starter-kanban` + `jaiclaw-plugin-sdk`
  directly**, not the central `jaiclaw-spring-boot-starter` (which
  isn't always installed locally for the demo to find). The kanban
  autoconfig is self-contained and works without the central agent
  stack.
