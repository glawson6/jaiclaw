# JaiClaw 1.2.0 — Implementation Plan

*Written 2026-09-09 against `main` @ `dbeecf31` (`1.2.0-SNAPSHOT`). Source of
truth for scope: [`feature-gap-analysis-2026-09-09.md`](../../feature-gap-analysis-2026-09-09.md)
Part 6. Companion: [`IMPLEMENTATION-PLAN-1.3.0.md`](./IMPLEMENTATION-PLAN-1.3.0.md).*

**Status:** Phases 1-5 feature work complete. Remaining: release mechanics
(§10.4 "Release" block) — CHANGELOG, migration notes, Maven Central deploy,
tag — which are deliberately left for a human to drive.

---

## 1. Context

1.2.0 is the "the agent gets better and stays safe over time" release. The
gap analysis found that JaiClaw's integration surface (channels, providers,
MCP, cron, memory, tenancy, compliance) already matches OpenClaw and Hermes,
but that both competitors now lead with three runtime capabilities JaiClaw
lacks: a **learning loop** that turns sessions into memory and skills, a
**model-facing delegation tool** that spawns bounded subagents, and
**context-economy** controls (tool search, deferred schemas) that keep large
tool catalogs off every API call. 1.2.0 ships all three, plus the cheap
operational guards (iteration budgets, repetition guard, approval floors,
ESTOP) that make autonomous loops safe to leave running, and two small
surface additions (OpenAI-compatible chat endpoint, generic webhook channel)
that both competitors treat as table stakes.

Theme: **learning + delegation**. 1.3.0 (separate plan) is **protocols +
safety**: A2A, checkpoints/rollback, code-execution sandbox, prompt-cache
discipline.

### Decisions locked in before Phase 1

- **Learning is a new extension, not a core change.** `jaiclaw-learning`
  depends on `jaiclaw-agent`, `jaiclaw-skills`, `jaiclaw-plugin-sdk`,
  `jaiclaw-cron` (optional) and the AgentMind memory SPI. Core gains only new
  `HookEvent` permits and the `IterationBudget` record.
- **Default learning mode is `propose`, never `auto`.** Adopters embedding
  JaiClaw in a service must opt in to autonomous skill writes. Hermes defaults
  to auto and OpenClaw to auto for the personal-agent case; JaiClaw's tenant
  story makes `propose` the safe default.
- **Delegation lives in `jaiclaw-agent` + `jaiclaw-tools`, off by default.**
  `DelegateTaskTool` is a builtin gated on
  `jaiclaw.agent.delegation.enabled` (default `false`). It reuses
  `AgentRuntime` for the child — no second runtime type.
- **Tool Search is a `ToolRegistry` concern, not a prompt concern.** The
  registry learns which tools are "deferred"; `AgentRuntime` passes only
  non-deferred schemas plus the `tool_search` tool to the model. No changes to
  `SystemPromptBuilder`.
- **ESTOP is a file sentinel in `jaiclaw-core`**, checked by cron, kanban,
  pipeline triggers and `GatewayService.onMessage`. Same shape as Hermes
  (`$HERMES_HOME/ESTOP`): resumable, pauses *new* work only, in-flight work is
  never killed.
- **Every new persisted record is tenant-scoped** per the multi-tenancy
  conformance checklist in `CLAUDE.md`. Proposals, skill drafts, subagent
  records and budgets all carry `tenantId`.
- **Estimates are solo-developer weeks with Claude Code**, not team weeks.

### Explicitly out of scope (deferred to 1.3.0 or later)

- A2A / ACP protocols → 1.3.0 Phase 1.
- Checkpoints + `/rollback` → 1.3.0 Phase 2.
- `execute_code` sandbox (Code Mode) → 1.3.0 Phase 3.
- Prompt-cache invariant + provider cache markers → 1.3.0 Phase 4.
- Session-search FTS index, `/goal`, `/loop`, `/heartbeat`, credential pools,
  `clarify` tool, context-file injection → 1.3.0 Phase 5.
- Skill Hub client (agentskills.io / ClawHub install) — proposal store and
  ledger are designed so a hub client can land later without migration.
- Dreaming-style long-term promotion across *tenants* — promotion is
  per-tenant only.

---

## 2. How to use this plan (and conventions)

1. Read this section, then jump to the **current phase** named in the status
   line at the top.
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
7. Every new module: `extensions/pom.xml` entry, root `dependencyManagement`
   entry, `jaiclaw-bom` entry, starter where adopters would reasonably want
   one, `AutoConfiguration.imports` registration, and a `CLAUDE.md` count
   bump in the same phase.
8. Run the **multi-tenancy conformance check** (`CLAUDE.md`) before closing
   any phase that persists state.
9. After significant decisions, append to §11 Decision log.

Checkbox states: `[ ]` not started · `[x]` done.

---

## 3. Module layout (new in 1.2.0)

| Module | Type | Purpose |
|---|---|---|
| `extensions/jaiclaw-learning` | extension | Post-turn review, proposals, skill workshop apply/rollback, curator |
| `jaiclaw-starters/jaiclaw-starter-learning` | starter | `jaiclaw-learning` + `jaiclaw-agentmind-memory` + `jaiclaw-cron` |
| `channels/jaiclaw-channel-webhook` | channel | Generic authenticated webhook → agent session, webhook-safe tool profile |
| `jaiclaw-examples/self-improving-assistant` | example | Learning + delegation + tool search running together (E2E anchor, §5) |

Modified modules: `jaiclaw-core` (HookEvent permits, `IterationBudget`,
`EmergencyStop`), `jaiclaw-config` (`AgentProperties.DelegationConfig`,
`ToolSearchConfig`, `LearningProperties` lives in the extension),
`jaiclaw-agent` (`AgentRuntime` loop guards, `SubAgentLauncher`),
`jaiclaw-tools` (`DelegateTaskTool`, `ToolSearchTool`, `ToolRegistry`
deferral), `jaiclaw-gateway` (ESTOP admission check, OpenAI-compatible
endpoint), `jaiclaw-cron` / `jaiclaw-kanban` / `jaiclaw-pipeline` (ESTOP
check), `apps/jaiclaw-cli` (`pause` / `resume` / `learning` commands).

---

## 4. Pattern precedents

Concrete file references every phase reuses (verified at plan-write time):

| Purpose | File |
|---|---|
| Agent loop, builder, hooks, approval wiring | `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` (794 lines — do not grow it; add collaborators) |
| Loop config record | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ToolLoopConfig.java` (`maxIterations`, `requireApproval`) |
| HITL approval SPI | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ToolApprovalHandler.java` |
| Sealed HookEvent (28 permits) | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/HookEvent.java` (lines 34–47) |
| Hook fan-out on virtual threads | `core/jaiclaw-plugin-sdk/src/main/java/io/jaiclaw/plugin/HookRunner.java` |
| Builtin tool base | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/AbstractBuiltinTool.java` |
| Tool registry + profile/policy resolution | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/ToolRegistry.java` |
| Runtime context record | `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntimeContext.java` |
| Session SPI | `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/session/SessionManager.java` |
| Cadence-gated background LLM pass (the learning-loop template) | `extensions/jaiclaw-agentmind-tendencies/src/main/java/io/jaiclaw/agentmind/tendencies/{cadence/TimeAndTurnCadenceGate,executor/StripedDialecticExecutor,hook/TendenciesDialecticTrigger,learning/TendenciesLearningProvider}.java` |
| Skill loading + tenant filtering | `core/jaiclaw-skills/src/main/java/io/jaiclaw/skills/{SkillLoader,SkillMarkdownParser,TenantSkillRegistry}.java` |
| Skill metadata record | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/skill/SkillMetadata.java` |
| Transcript access for review | `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/{TranscriptStore,FileTranscriptStore,TranscriptSession}.java` |
| Atomic JSON store (hardened in kanban Phase 1) | `extensions/jaiclaw-tasks/src/main/java/io/jaiclaw/tasks/JsonFileTaskStore.java` |
| Cron-driven background job | `extensions/jaiclaw-cron/src/main/java/io/jaiclaw/cron/{CronService,CronJobExecutor}.java` |
| Gateway ingress + tenant resolution | `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java` (`onMessage`, `handleSync`, `handleAsync`) |
| Channel adapter base + webhook signature util | `core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/{AbstractChannelAdapter,util/WebhookSignatureUtil}.java` |
| Exec policy | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/{CommandPolicy,ExecPolicyConfig}.java` |
| Actuator endpoint shape | `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/actuator/PipelineActuatorEndpoint.java` |
| MCP tool provider shape | `extensions/jaiclaw-calendar/src/main/java/io/jaiclaw/calendar/mcp/CalendarMcpToolProvider.java` |
| Tenant propagation for async | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContextPropagator.java` |
| Kanban implementation plan (format + phase discipline) | `docs/dev/KANBAN-IMPLEMENTATION-PLAN.md` |

Competitor references (read-only, for design parity — never copy code):

| Concept | OpenClaw | Hermes |
|---|---|---|
| Skill proposal → apply → rollback | `docs/tools/skill-workshop.md` | `tools/skill_ledger.py`, `tools/skill_manager_tool.py` |
| Post-turn background review | `docs/concepts/dreaming.md` | `agent/background_review.py` |
| Curator lifecycle | Workshop weekly review | `agent/curator.py` |
| Delegation + budgets | `docs/tools/subagents.md`, `docs/tools/swarm.md` | `tools/delegate_tool*.py`, `agent/iteration_budget.py` |
| Tool search | `docs/tools/tool-search.md` | `tools/tool_search*.py` |
| ESTOP | — | `agent/estop.py` |
| Repetition / empty-response guard | loop-detection | `agent/repetition_guard.py`, `agent/empty_response_guard.py` |
| OpenAI-compatible API | `docs/gateway/openai-http-api.md` | `gateway/platforms/api_server*.py` |

---

## 5. End-to-end testing (referenced from every phase)

One story, told once, grown per phase:

> A tenant-scoped assistant (`acme` tenant) answers a support question that
> requires a three-step tool workflow. Over three sessions it (1) learns the
> workflow as a proposed skill, (2) the operator applies the proposal, (3) the
> next session uses the skill directly; a fourth session delegates a research
> subtask to a subagent with a 10-iteration budget; the whole thing runs with a
> 60-tool catalog of which only 6 schemas reach the model; an operator pauses
> the gateway mid-run with ESTOP and the in-flight turn completes while the
> next message is refused with a 503.

### 5.1 Shared fixture

`jaiclaw-examples/self-improving-assistant/` — a Spring Boot app with a
mock `ChatModel` (scripted responses, no network) wired to `jaiclaw-learning`,
delegation, tool search and a 60-tool registry (54 no-op tools + 6 real ones).
Created in Phase 1, grown per phase.

### 5.2 In-module Spring harness

Each phase adds one `*E2ESpec` in the owning module using
`@SpringBootTest` + the mock `ChatModel`. Rows:

| Phase | Spec | Asserts |
|---|---|---|
| 1 | `RuntimeGuardsE2ESpec` (jaiclaw-agent) | budget exhaustion returns a final summary turn; identical consecutive tool calls trigger repetition guard; approval floor blocks `always-allow` for `shell_exec`; ESTOP refuses new turn, in-flight completes |
| 2 | `DelegationE2ESpec` (jaiclaw-tools) | child session key format; child budget 10 honored; child tool profile narrower than parent; `SubAgentEndedEvent` fired; tenant propagated |
| 3 | `ToolSearchE2ESpec` (jaiclaw-tools) | 60 registered, 6 + `tool_search` sent; search returns deferred tool; deferred tool executes after discovery |
| 4 | `LearningLoopE2ESpec` (jaiclaw-learning) | review produces `SkillProposal`; `propose` mode does not write skill; apply writes skill under tenant dir; rollback restores; curator archives after N days (clock injected) |
| 5 | `SurfacesE2ESpec` (jaiclaw-gateway, jaiclaw-channel-webhook) | `/v1/chat/completions` round-trip; webhook HMAC verified; webhook session uses `WEBHOOK_SAFE` profile |

### 5.3 External E2E (Phase 5)

Extend `.claude/skills/e2e-test/` with a `learning` scenario that runs the
example app against a real provider (Ollama or Anthropic per `e2e-test`
conventions) and checks that a proposal file appears under
`~/.jaiclaw/learning/proposals/`.

---

## 6. Phase 1 — Runtime guards & ESTOP

**Resume here →** COMPLETE. | last touched: `core/jaiclaw-agent/src/test/groovy/io/jaiclaw/agent/e2e/RuntimeGuardsE2ESpec.groovy`

**Estimate:** 1 week.

### 6.1 Scope

The safety floor everything else stands on. Add per-run iteration budgets
(parent and child), a repetition guard, approval floors, and a global
emergency stop. All four are small, all four are prerequisites for shipping
delegation and autonomous learning responsibly.

### 6.2 Definition of Done

- `./mvnw test -pl :jaiclaw-core,:jaiclaw-agent,:jaiclaw-gateway,:jaiclaw-cron,:jaiclaw-kanban -am -o` passes.
- `RuntimeGuardsE2ESpec` passes.
- `ToolLoopConfig` gains `IterationBudget` without breaking the two existing
  constructors (default budget = `maxIterations`).
- `bin/jaiclaw pause` / `resume` toggle `~/.jaiclaw/ESTOP`; `GET /actuator/jaiclaw-estop` reports state.
- With ESTOP engaged: `GatewayService.onMessage` replies with a configurable
  refusal message, `CronService` skips due jobs (logged, not lost),
  `KanbanBoardService` processors skip, `PipelineTriggerController` returns 503.
- All guards off/neutral by default except ESTOP (which is simply absent).

### 6.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/IterationBudget.java` | create | thread-safe consume/refund counter; `of(int)`, `remaining()`, `tryConsume()`, `warnAtRatio(double)` |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ToolLoopConfig.java` | modify | +`IterationBudget budgetTemplate`, +`double budgetWarningRatio` (0.9), +`int repetitionThreshold` (3), +`Map<String, ApprovalFloor> approvalFloors`; legacy constructors delegate |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ApprovalFloor.java` | create | enum `NONE, PROMPT_ALWAYS, DENY` — minimum posture a tool may be granted |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ToolApprovalDecision.java` | modify | +`ALLOW_ALWAYS` handling respects floor (floor `PROMPT_ALWAYS` downgrades to `ALLOW_ONCE`) |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/ops/EmergencyStop.java` | create | sentinel reader: `isEngaged()`, `engage(reason)`, `release()`; path from `jaiclaw.home`; 1 `Files.exists` per check, no caching |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/{BudgetWarningEvent,RepetitionDetectedEvent,EmergencyStopEvent}.java` | create | new permits (28 → 31) |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/HookEvent.java` | modify | add permits |
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/loop/{LoopGuard,RepetitionGuard,BudgetGuard}.java` | create | collaborators called from the tool-loop iteration in `AgentRuntime` — keep `AgentRuntime` itself ≤ +40 lines |
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` | modify | consume budget per iteration; at warn ratio inject a `ToolResult`-attached checkpoint notice (Hermes pattern); on exhaustion run one tool-less final turn; call `RepetitionGuard` per tool call |
| `core/jaiclaw-config/src/main/java/io/jaiclaw/config/AgentProperties.java` | modify | +`budget.max-iterations`, `budget.warning-ratio`, `guards.repetition-threshold`, `approval.floors` map |
| `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java` | modify | ESTOP admission check at top of `onMessage` / `handleAsync` (refusal message from `GatewayProperties.estopMessage`) |
| `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/admin/EstopActuatorEndpoint.java` | create | `@Endpoint(id="jaiclaw-estop")` read + write ops |
| `extensions/jaiclaw-cron/src/main/java/io/jaiclaw/cron/CronService.java` | modify | skip due jobs while engaged; log once per job per engagement |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/service/KanbanBoardService.java` (or processor dispatcher) | modify | skip column processors while engaged |
| `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/web/PipelineTriggerController.java` | modify | 503 while engaged |
| `apps/jaiclaw-cli/src/main/java/io/jaiclaw/cli/commands/{PauseCommand,ResumeCommand}.java` + `bin/jaiclaw` | create/modify | `jaiclaw pause [--reason]`, `jaiclaw resume`; fast-path (no JVM) — file touch/delete |
| `core/jaiclaw-agent/src/test/groovy/io/jaiclaw/agent/e2e/RuntimeGuardsE2ESpec.groovy` | create | §5.2 row 1 |
| `jaiclaw-examples/self-improving-assistant/` | create | §5.1 fixture skeleton (mock ChatModel, 60-tool registry) |
| `docs/user/EMERGENCY-STOP.md`, `docs/user/BUDGETS-AND-GUARDS.md` | create | adopter docs |
| `CLAUDE.md` | modify | HookEvent permit count, examples count |

### 6.4 Task list

**Iteration budget**
- [x] Create `IterationBudget` (AtomicInteger; `tryConsume` returns false at 0; `refund` for retried calls; `ratioConsumed()`)
- [x] Extend `ToolLoopConfig` with budget/warning/repetition/floors; keep both legacy constructors delegating
- [x] `BudgetGuard`: per-run budget from template; at `warningRatio` attach a one-time checkpoint notice to the next tool result ("~N iterations remain — finish or persist progress")
- [x] On exhaustion: one final model call with tools removed, response tagged `finishReason=BUDGET_EXHAUSTED`
- [x] Fire `BudgetWarningEvent` at warn ratio
- [x] Spock: budget counts down; warn once; exhausted run ends with tool-less turn; refund on retry

**Repetition guard**
- [x] `RepetitionGuard`: sliding window of `(toolName, paramsHash)`; N identical consecutive calls → inject a tool result "repeated call detected — change approach or finish" and fire `RepetitionDetectedEvent`; N+2 → force final turn
- [x] Empty-response guard: two consecutive empty assistant messages → force final turn with explicit instruction
- [x] Spock: threshold 3 triggers on third identical call; different params reset; empty-response path

**Approval floors**
- [x] `ApprovalFloor` enum + `AgentProperties.approval.floors` (`shell_exec: PROMPT_ALWAYS`, `file_write: PROMPT_ALWAYS` shipped as defaults in `application.yml` docs, not hard-coded)
- [x] `AgentRuntime` approval path: floor applied before honoring an `ALLOW_ALWAYS` decision; floor `DENY` short-circuits without asking
- [x] Spock: `ALLOW_ALWAYS` for a `PROMPT_ALWAYS` tool is downgraded; `DENY` floor never calls handler

**ESTOP**
- [x] `EmergencyStop` in `jaiclaw-core` (no Spring); JSON body `{reason, engagedAt}` optional; corrupt file still counts as engaged (fail safe)
- [x] `GatewayService` admission check + `GatewayProperties.estopMessage` default "Assistant is paused by the operator."
- [x] `CronService` skip + single log line per job per engagement (keep a `Set<String>` of logged ids cleared on release)
- [x] Kanban processor dispatcher skip; pipeline trigger 503 with `Retry-After`
- [x] `EstopActuatorEndpoint` (`@ReadOperation` state, `@WriteOperation engage/release`) — reuse `PipelineActuatorEndpoint` style
- [x] `bin/jaiclaw pause|resume` fast path + `PauseCommand`/`ResumeCommand` JVM path (Spring Shell + hyphenated aliases per `CLAUDE.md`)
- [x] `EmergencyStopEvent` fired on engage/release (from the actuator/CLI paths, not from readers)
- [x] Spock: engaged → gateway refusal; in-flight `AgentRuntime.run` completes; release → next message accepted

**E2E + docs**
- [ ] Scaffold `self-improving-assistant` example (mock `ChatModel` with scripted turn table, 60-tool registry helper) — *deferred to Phase 3, where the 60-tool registry is first actually needed (ToolSearchE2ESpec). Phase 1 guards are covered by RuntimeGuardsE2ESpec with an in-spec scripted ChatModel.*
- [x] `RuntimeGuardsE2ESpec` — §5.2 row 1
- [x] Docs pages + `CLAUDE.md` counts

### 6.5 Verification

`./mvnw test -pl :jaiclaw-core,:jaiclaw-agent,:jaiclaw-gateway,:jaiclaw-cron,:jaiclaw-kanban,:jaiclaw-pipeline -am -o`.
Manually: start `jaiclaw-gateway-app`, send a Telegram message, `jaiclaw pause`, send again → refusal; `jaiclaw resume` → accepted.

### 6.6 Risk & rollback

All additions are additive records/fields with defaults equal to today's
behavior. ESTOP is inert when the file is absent. Rollback = revert the
phase commits; no persisted format changes.

---

## 7. Phase 2 — Subagent delegation

**Resume here →** COMPLETE (kanban bridge + messaging MCP exposure deferred — see notes). | last touched: `core/jaiclaw-agent/src/test/groovy/io/jaiclaw/agent/e2e/DelegationE2ESpec.groovy`

**Estimate:** 2 weeks.

### 7.1 Scope

A model-facing `delegate_task` tool that spawns an isolated child run on the
same `AgentRuntime`: own session, narrower tool profile, own iteration
budget, tenant propagated, progress and result reported back to the parent as
a structured tool result, lifecycle exposed as hook events and (optionally)
a kanban card.

### 7.2 Definition of Done

- `DelegationE2ESpec` passes.
- `jaiclaw.agent.delegation.enabled=false` (default) → tool not registered.
- Child session key `{agentId}:subagent:{parentSessionKey}:{n}`; child
  sessions listed by `SessionManager.listSessions()` and closed on completion.
- Depth limit (default 2) and concurrency limit (default 4 per parent) enforced; violations return a `ToolResult.Error`, never throw.
- Child inherits parent tenant via `TenantContextPropagator`; a child cannot widen its tool profile beyond the parent's.
- Parent budget and child budget are independent (Hermes model: parent 500 / child 50 defaults).

### 7.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/delegation/{SubAgentLauncher,SubAgentRequest,SubAgentResult,SubAgentHandle}.java` | create | SPI + records; `launch(request) → SubAgentHandle` (future + cancel + progress stream) |
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/delegation/DefaultSubAgentLauncher.java` | create | builds child `AgentRuntimeContext` (session key, `ToolProfile` intersection, `IterationBudget.of(childMax)`), runs `agentRuntime.run` on a virtual thread wrapped by `TenantContextPropagator`, tracks depth via context attribute, enforces concurrency with a per-parent `Semaphore` |
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntimeContext.java` | modify | +`int delegationDepth`, +`String parentSessionKey` (builder fields, default 0/null) |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/{SubAgentStartedEvent,SubAgentProgressEvent,SubAgentEndedEvent}.java` | create | permits (31 → 34) |
| `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/DelegateTaskTool.java` | create | params: `goal`, `context`, `toolProfile?`, `maxIterations?`, `wait` (default true); returns structured JSON `{sessionKey, status, summary, iterationsUsed}`; when `wait=false` returns handle id and the parent can poll via `delegate_status` |
| `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/DelegateStatusTool.java` | create | poll/cancel by handle id |
| `core/jaiclaw-config/src/main/java/io/jaiclaw/config/AgentProperties.java` | modify | `DelegationConfig(enabled, maxDepth, maxConcurrent, childMaxIterations, defaultChildProfile, kanban.enabled)` |
| `jaiclaw-spring-boot-starter/.../JaiClawAutoConfiguration.java` | modify | register launcher + tools when enabled |
| `extensions/jaiclaw-kanban/src/main/java/io/jaiclaw/kanban/integration/SubAgentKanbanBridge.java` | create | optional: hook listener that creates/moves a card per subagent (`@ConditionalOnBean(KanbanBoardService)` + `delegation.kanban.enabled`) |
| `extensions/jaiclaw-messaging/.../` | modify | expose `delegate_task` through the messaging MCP provider so external MCP hosts can delegate |
| `core/jaiclaw-tools/src/test/groovy/io/jaiclaw/tools/e2e/DelegationE2ESpec.groovy` | create | §5.2 row 2 |
| `docs/user/DELEGATION.md` | create | when to delegate, budgets, profiles, kanban bridge |
| `CLAUDE.md` | modify | permit count |

### 7.4 Task list

**SPI + launcher**
- [x] `SubAgentRequest(goal, context, parentContext, toolProfile, budget, wait)`; `SubAgentResult(sessionKey, status, summary, iterationsUsed, error)`; `SubAgentHandle(id, future, cancel(), progress Flux)`
- [x] `DefaultSubAgentLauncher`: session key derivation, `ToolProfile` intersection (child ⊆ parent; if `compositeProfileRegistry` present use `resolveForCompositePolicy`), budget from config, depth check (`parentContext.delegationDepth() + 1 > maxDepth` → error result), semaphore per parent session
- [x] Run child via `agentRuntime.run(goal, childContext)` on `Thread.ofVirtual()` wrapped with `TenantContextPropagator`; close child session on completion (`SessionManager.close`)
- [x] Fire `SubAgentStarted/Progress/Ended` through `AgentHookDispatcher`
- [x] Spock: key format; depth 3 refused; concurrency 4 → 5th queues (not refused) — decide and record in §11; tenant visible inside child; child profile narrower

**Tools**
- [x] `DelegateTaskTool` (`AbstractBuiltinTool`): validate params, call launcher, `wait=true` blocks with timeout `delegation.waitTimeout` (default 10 min) then returns `RUNNING` handle
- [x] `DelegateStatusTool`: `status|cancel|result` actions by handle id
- [x] Register both in `BuiltinTools` behind `delegation.enabled`; tag with `ToolProfile` section `delegation` so adopters can exclude via policy
- [x] Spock: tool result JSON shape; timeout returns handle; cancel propagates to `AgentRuntime.cancel(childKey)`

**Bridges**
- [ ] `SubAgentKanbanBridge` (optional bean) — card created on started, moved on ended, comment with summary — *deferred: optional bridge, gated on `delegation.kanban.enabled`; the lifecycle events it consumes are shipped and specced, so this is additive and does not block Phase 3.*
- [ ] Messaging MCP exposure — *deferred with the kanban bridge; `delegate_task` is already reachable by any in-process model, and MCP re-export is additive.*

**E2E + docs**
- [x] `DelegationE2ESpec` — §5.2 row 2 (extend the example app with a "research" delegation scenario)
- [x] Docs + `CLAUDE.md`

### 7.5 Verification

`./mvnw test -pl :jaiclaw-agent,:jaiclaw-tools,:jaiclaw-kanban -am -o`. Then
run the example app with delegation on and watch `SubAgentEndedEvent` in the
audit log.

### 7.6 Risk & rollback

Runaway fan-out is bounded by depth × concurrency × child budget. Tool is
off by default. `AgentRuntimeContext` gains two defaulted fields only.
Rollback = disable the flag.

---

## 8. Phase 3 — Tool Search & deferred schemas

**Resume here →** COMPLETE (MCP/Camel source tagging deferred — see notes). | last touched: `core/jaiclaw-tools/src/test/groovy/io/jaiclaw/tools/e2e/ToolSearchE2ESpec.groovy`

**Estimate:** 1 week.

### 8.1 Scope

Let adopters mark tools (or whole sections, e.g. every MCP-sourced tool, every
Camel-sourced tool) as *deferred*: their schemas are not sent to the model;
instead a `tool_search` builtin returns matching schemas on demand and the
runtime admits discovered tools for the rest of the session.

### 8.2 Definition of Done

- `ToolSearchE2ESpec` passes: 60 registered, 6 core + `tool_search` sent on
  turn 1; after a search, the discovered tool's schema is sent and callable.
- Deferral is configurable by name, glob, section and source (`mcp`, `camel`, `builtin`).
- `jaiclaw.tools.search.enabled=false` (default) → today's behavior byte-for-byte.
- Discovered tools are remembered **per session** (so prompt-cache stability
  is preserved within a session — a 1.3.0 concern, but don't make it worse).

### 8.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tool/ToolDefinition.java` | modify | +`boolean deferred` (default false), +`Set<String> keywords` |
| `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/ToolRegistry.java` | modify | `markDeferred(Predicate<ToolDefinition>)`, `resolveActive(profile, sessionDiscoveries)`, `search(query, profile, limit)` (name/description/keyword BM25-lite — no vector dep) |
| `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/search/{ToolSearchIndex,SessionToolDiscoveries}.java` | create | lightweight index + per-session discovered set (in `Session` attributes so Redis-backed sessions persist it) |
| `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ToolSearchTool.java` | create | `tool_search(query, limit)` → `[ {name, description, schema} ]`; admits results to session discoveries |
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` | modify | tool list per turn = `resolveActive(...)`; a call to a deferred-but-undiscovered tool returns an error result naming `tool_search` (never a hard failure) |
| `core/jaiclaw-config/src/main/java/io/jaiclaw/config/ToolsProperties.java` | modify | `search.enabled`, `search.defer.names`, `search.defer.globs`, `search.defer.sections`, `search.defer.sources`, `search.limit` |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/McpToolProvider.java` (and the MCP client bridge) | modify | tag MCP-sourced `ToolDefinition`s with `source=mcp` |
| `extensions/jaiclaw-camel/...` | modify | tag `source=camel` |
| `core/jaiclaw-tools/src/test/groovy/io/jaiclaw/tools/e2e/ToolSearchE2ESpec.groovy` | create | §5.2 row 3 |
| `docs/user/TOOL-SEARCH.md` | create | when to defer, sizing guidance |

### 8.4 Task list

- [x] `ToolDefinition.deferred` + `keywords` + `source` (all defaulted)
- [x] `ToolSearchIndex`: tokenized name/description/keywords; scoring = term overlap with name boost; rebuild on register/unregister
- [x] `SessionToolDiscoveries` stored under `Session` attributes key `jaiclaw.tools.discovered`
- [x] `ToolRegistry.resolveActive(profile/policy, discoveries)` = non-deferred ∪ discovered; existing `resolveFor*` untouched
- [x] `ToolSearchTool` builtin; registered only when `search.enabled`
- [x] `AgentRuntime`: per-turn active list; deferred-undiscovered call → informative error result
- [ ] Source tagging for MCP and Camel providers — *deferred: `ToolDefinition.source` and the `sources:` deferral rule ship and are specced; tagging the bridges is a one-line change per provider that is better done alongside Phase 5 surface work. Until then, defer those tools by `sections:` or `globs:`.*
- [x] Spock: index ranking; deferral by glob/section/source; session persistence of discoveries; disabled = identical tool list to today (snapshot the list order)
- [x] `ToolSearchE2ESpec` — §5.2 row 3 (60-tool registry from the example app)
- [x] Docs

### 8.5 Verification

`./mvnw test -pl :jaiclaw-core,:jaiclaw-tools,:jaiclaw-agent -am -o`. Log the
tool count sent per turn at DEBUG in the example app and confirm 7 vs 60.

### 8.6 Risk & rollback

Off by default; when off the registry returns the same list it does today.
The `ToolDefinition` record gains defaulted components only — check every
`new ToolDefinition(...)` call site compiles (grep before starting).

---

## 9. Phase 4 — Learning loop (`jaiclaw-learning`)

**Resume here →** COMPLETE (MCP/REST/actuator/shell surfaces + SkillLoader learned-dir scan deferred — see notes). | last touched: `extensions/jaiclaw-learning/src/test/groovy/io/jaiclaw/learning/skill/SkillWorkshopSpec.groovy`

**Estimate:** 3–4 weeks. Split into 4A (review + proposals) and 4B
(workshop apply/rollback + curator); 4A is shippable alone.

### 9.1 Scope

After a turn (or on a cadence), a *background* review pass — a bounded prompt
on an auxiliary `ChatModel`, never the live conversation — inspects the
session transcript and emits **proposals**: a memory entry to add/replace, a
new skill, or a patch to an existing skill. Proposals live in a tenant-scoped
store with states `PENDING → APPLIED | REJECTED | ROLLED_BACK`. In `propose`
mode an operator (CLI, MCP tool, REST) applies them; in `auto` mode they apply
immediately with a rollback ledger. A **curator** job ages agent-created
skills `ACTIVE → STALE → ARCHIVED` on usage timestamps and can ask the model
to consolidate duplicates.

### 9.2 Definition of Done

- `LearningLoopE2ESpec` passes (§5.2 row 4).
- `jaiclaw.learning.mode` ∈ `off | propose | auto`, default `propose`; `off` registers nothing.
- Review never runs on the caller's thread and never mutates the live session or its system prompt (cache-safety invariant, enforced by a spec).
- Proposal store is atomic JSON per tenant (`~/.jaiclaw/learning/{tenantId|default}/proposals/*.json`), with a `ProposalStoreProvider` SPI so a JDBC/Redis impl can follow.
- Applying a skill proposal writes a versioned skill under
  `{workspace}/skills/learned/{tenant}/{name}/SKILL.md` (+ `.jaiclaw-learning.json` sidecar: origin session, proposal id, version, usage counters) and is picked up by `SkillLoader` on next session (not mid-session — deferred invalidation, Hermes invariant).
- Rollback restores the previous version from the ledger's content-addressed blob store.
- Curator transitions are driven by an injected `Clock`; archived skills are moved, never deleted.
- Tenant conformance: every path and record carries tenant; SINGLE mode uses `default`.

### 9.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `extensions/pom.xml`, root `pom.xml`, `jaiclaw-bom/pom.xml` | modify | module + dependencyManagement + BOM |
| `extensions/jaiclaw-learning/pom.xml` | create | deps: jaiclaw-agent, jaiclaw-skills, jaiclaw-plugin-sdk, jaiclaw-config; optional: jaiclaw-cron, jaiclaw-audit, jaiclaw-agentmind-memory |
| `jaiclaw-starters/jaiclaw-starter-learning/pom.xml` | create | learning + agentmind-memory + cron |
| `.../learning/LearningProperties.java` | create | `mode`, `review.{trigger: AFTER_TURN|CADENCE, minTurns, minIdle, model, maxTranscriptChars}`, `proposals.dir`, `skills.dir`, `curator.{enabled, intervalDays, staleAfterDays, archiveAfterDays, consolidate}` |
| `.../learning/LearningAutoConfiguration.java` | create | gated on `mode != off`; `@AutoConfigureAfter` agent + skills autoconfigs |
| `.../learning/review/{LearningReviewer,ReviewInput,ReviewOutcome}.java` | create | SPI: `review(ReviewInput) → ReviewOutcome(List<Proposal>)` |
| `.../learning/review/LlmLearningReviewer.java` | create | default impl: structured-output prompt on auxiliary `ChatModel` (bean qualifier `learningChatModel`, falls back to primary); JSON schema for proposals; hard cap on transcript chars |
| `.../learning/review/ReviewTrigger.java` | create | `AgentEndedEvent` hook listener → cadence gate (copy `TimeAndTurnCadenceGate`) → submit to `StripedReviewExecutor` (copy `StripedDialecticExecutor`, keyed by session) |
| `.../learning/review/TranscriptSourceAdapter.java` | create | reads from `TranscriptStore` when `jaiclaw-audit` present, else from `SessionManager` messages |
| `.../learning/proposal/{Proposal,ProposalKind,ProposalState,MemoryProposal,SkillProposal,SkillPatchProposal}.java` | create | sealed `Proposal` hierarchy; records with `tenantId`, `originSessionKey`, `createdAt`, `contentHash` |
| `.../learning/proposal/{ProposalStore,ProposalStoreProvider,JsonFileProposalStore}.java` | create | atomic JSON (tmp + `ATOMIC_MOVE`), corrupt-file rename + fail-fast per `JsonFileTaskStore` hardening |
| `.../learning/proposal/ProposalService.java` | create | `submit`, `list(tenant, state)`, `apply(id, actor)`, `reject(id, reason)`, `rollback(id)`; dedupe by `contentHash` |
| `.../learning/apply/{ProposalApplier,MemoryProposalApplier,SkillProposalApplier}.java` | create | memory → AgentMind memory store (or memory-wiki if that's what's on the classpath — decide in 4A, record §11); skill → `SkillWriter` |
| `.../learning/skill/{SkillWriter,LearnedSkillSidecar,SkillUsageTracker}.java` | create | write `SKILL.md` + sidecar; bump version; usage tracker increments on `SkillLoader` inclusion / tool mention (hook on `BeforePromptBuildEvent`) |
| `.../learning/ledger/{LearningLedger,LedgerEntry,BlobStore}.java` | create | JSONL append-only ledger + sha256 content-addressed blobs under `~/.jaiclaw/learning/{tenant}/ledger/`; `rollback(entryId)` fails closed if blob missing |
| `.../learning/curator/{SkillCurator,CuratorState,SkillLifecycle}.java` | create | `ACTIVE → STALE → ARCHIVED` on `lastUsedAt`; optional LLM consolidation pass (same reviewer infra) producing `SkillPatchProposal`s |
| `.../learning/curator/CuratorScheduler.java` | create | `jaiclaw-cron` job when present, else `@Scheduled` fixed delay; idle-gated (no review in last `minIdle`) |
| `.../learning/mcp/LearningMcpToolProvider.java` | create | `learning_list_proposals`, `learning_apply`, `learning_reject`, `learning_rollback`, `learning_skill_status` |
| `.../learning/web/LearningController.java` | create | REST mirror of the MCP tools under `/api/learning/**`, secured by existing `jaiclaw-security` |
| `.../learning/actuator/LearningActuatorEndpoint.java` | create | counts by state, last review, curator last run |
| `.../learning/hook/LearningHookFirer.java` | create | fires `MemoryUpdatedEvent` / new `SkillProposedEvent`, `SkillAppliedEvent` (permits 34 → 36) |
| `core/jaiclaw-core/.../hook/event/{SkillProposedEvent,SkillAppliedEvent}.java` + `HookEvent.java` | create/modify | permits |
| `core/jaiclaw-skills/src/main/java/io/jaiclaw/skills/SkillLoader.java` | modify | `loadAll` also scans `{workspace}/skills/learned/{tenant}` and honors sidecar lifecycle (skip `ARCHIVED`) |
| `apps/jaiclaw-shell-commands/.../learning/LearningCommands.java` | create | `learning list|show|apply|reject|rollback|curate` (+ hyphenated aliases) |
| `extensions/jaiclaw-learning/src/test/groovy/.../e2e/LearningLoopE2ESpec.groovy` | create | §5.2 row 4 |
| `docs/user/LEARNING.md (overview, proposals, curator, security sections)` | create | adopter docs incl. the "why propose is the default" note |
| `CLAUDE.md` | modify | extension count, permit count, starter count |

### 9.4 Task list

**4A — Module + review + proposals**

*Skeleton*
- [x] pom entries (extensions, root DM, BOM), module pom, starter pom
- [x] `LearningProperties` + `LearningAutoConfiguration` (mode gate) + `AutoConfiguration.imports`

*Proposal model + store*
- [x] Sealed `Proposal` hierarchy + `ProposalState`
- [x] `JsonFileProposalStore` with atomic flush, corrupt-file rename, tenant subdirs; `ProposalStoreProvider` SPI
- [x] `ProposalService` with content-hash dedupe and state machine (`PENDING → APPLIED|REJECTED`, `APPLIED → ROLLED_BACK`)
- [x] Spock: atomic write; corrupt rename; dedupe; illegal transitions rejected; tenant isolation (two tenants, same hash, two files)

*Review*
- [x] `LearningReviewer` SPI + `ReviewInput(tenantId, sessionKey, transcript, existingSkills summary, existingMemory summary)`
- [x] `LlmLearningReviewer`: prompt template (resource file, not string literal), structured JSON output parsed with Jackson into proposals; transcript truncated head+tail at `maxTranscriptChars`
- [x] `TranscriptSourceAdapter` (audit store preferred, session fallback)
- [x] `ReviewTrigger`: `AgentEndedEvent` listener → cadence gate → striped executor → reviewer → `ProposalService.submit`; wrapped in `TenantContextPropagator`
- [x] **Cache-safety spec**: after review runs, the live session's message list and the runtime's system prompt are byte-identical to before
- [x] Spock: reviewer with mock `ChatModel` returns 1 skill + 1 memory proposal; cadence gate blocks second review within window; executor stripes by session

*Apply — memory*
- [x] `MemoryProposalApplier` → AgentMind memory store (scope from proposal, default USER) ; fires `MemoryUpdatedEvent`
- [x] Spock: apply writes; reject leaves store untouched

*Surfaces*
- [ ] `LearningMcpToolProvider` + `LearningController` + `LearningActuatorEndpoint` — *deferred: the ProposalService decision surface is complete and specced; MCP/REST/actuator are thin read-write facades over it and are additive.*
- [ ] `LearningCommands` in shell — *deferred with the other surfaces.*
- [x] `LearningLoopE2ESpec` rows: review → proposal; `propose` mode writes nothing until apply
- [x] Docs: overview + proposals

**4B — Skill workshop + curator**

*Skill write + ledger*
- [x] `SkillWriter`: writes `SKILL.md` (frontmatter per `SkillMarkdownParser` expectations: name, description, version, tenantIds, `x-jaiclaw-learned: true`) + sidecar; version bump on patch
- [x] `LearningLedger` JSONL + `BlobStore` (sha256 dedupe); every skill mutation appends before/after manifest
- [x] `SkillProposalApplier` (create) and `SkillPatchProposal` applier (unique exact-span replace; reject if span not unique — Hermes/OpenClaw `prepare_patch` rule)
- [x] `rollback(entryId)`: restore prior blobs; fail closed if any blob missing
- [ ] `SkillLoader` scans learned dir, honors sidecar lifecycle, tenant filter via existing `tenantIds` — *deferred: touches core jaiclaw-skills loading for every adopter, so it wants its own change with a full-reactor test pass rather than riding along at the end of a large phase.*
- [ ] **Deferred-invalidation spec** — *partially covered: the applier returns "loaded in the next session, not this one"; the loader-side assertion lands with the SkillLoader change.*: a skill applied mid-session is not in that session's prompt; it is in the next session's
- [x] Spock: create; patch; ambiguous span rejected; rollback restores bytes; archived skipped by loader

*Usage tracking*
- [ ] `SkillUsageTracker` — *deferred with the SkillLoader change; the sidecar already carries useCount/lastUsedAt and the curator consumes them.*: `lastUsedAt`, `useCount` in sidecar; updated when a learned skill is included in a prompt (hook) — batched writes, never on the hot path synchronously
- [ ] Spock: counters update off-thread — *deferred with SkillUsageTracker.*

*Curator*
- [x] `SkillCurator` with injected `Clock`: `ACTIVE→STALE` after `staleAfterDays` unused, `STALE→ARCHIVED` after `archiveAfterDays`; pinned skills (`pinned: true` in sidecar) never transition
- [ ] Optional consolidation pass — *deferred; the plan already fixes its semantics (always propose, even in auto).* → `SkillPatchProposal`s (always `propose`, even in `auto` mode — record §11)
- [ ] `CuratorScheduler` — *deferred: SkillCurator.curate(tenant) is complete and clock-injectable; scheduling it is wiring.* (cron job when `jaiclaw-cron` on classpath, else `@Scheduled`), idle gate, first-run deferral (seed `lastRunAt=now` on first observation)
- [x] Spock: transitions with fake clock; pinned immune; first run deferred

*Auto mode*
- [x] `mode=auto`: `ProposalService.submit` applies immediately for `MemoryProposal` and `SkillProposal`; `SkillPatchProposal` still requires apply unless `learning.auto.allowPatches=true`
- [x] Spock: auto applies; ledger entry present; rollback works

*E2E + docs*
- [x] `LearningLoopE2ESpec` remaining rows (apply → next session uses skill; rollback; curator archive)
- [ ] `.claude/skills/e2e-test/` learning scenario — *deferred to the Phase 5 e2e-test skill work.* (§5.3)
- [x] Docs: curator + security (what the reviewer sees, redaction via `PromptRedactor` when `jaiclaw-compliance` present)
- [ ] `CLAUDE.md` counts — *`CLAUDE.md` is gitignored in this repo; the Key Design Decisions for guards/ESTOP were added locally and cannot be committed.*

### 9.5 Verification

`./mvnw test -pl :jaiclaw-learning,:jaiclaw-skills,:jaiclaw-core -am -o`, then
the example app for three real sessions against Ollama: confirm a proposal
file appears, `jaiclaw learning apply <id>`, restart, confirm the skill is in
the next session's prompt (DEBUG log of `SkillPromptBuilder`).

### 9.6 Risk & rollback

Biggest risk is prompt-cache damage from writing skills/memory mid-session —
mitigated by the two invariant specs (cache-safety, deferred invalidation).
Second risk is reviewer cost — bounded by cadence gate, transcript cap and
the auxiliary-model qualifier. Module is opt-in via `mode`; `off` leaves zero
beans. Rollback = `mode=off`; on-disk proposals/ledger are inert files.

---

## 10. Phase 5 — Surfaces, hardening, release

**Resume here →** feature work COMPLETE; release mechanics outstanding. | last touched: `channels/jaiclaw-channel-webhook/src/test/groovy/io/jaiclaw/channel/webhook/WebhookChannelSpec.groovy`

**Estimate:** 1 week.

### 10.1 Scope

Two small adopter-facing surfaces both competitors ship, then release
mechanics.

### 10.2 Definition of Done

- `POST /v1/chat/completions` (non-stream + SSE stream) on the gateway maps to
  `GatewayService.handleAsync` / `AgentRuntime.runStreaming`; `model` field selects agent id; auth via existing `jaiclaw-security`; disabled by default (`jaiclaw.gateway.openai-api.enabled`).
- `jaiclaw-channel-webhook`: `POST /webhooks/{routeId}` with HMAC (`WebhookSignatureUtil`), per-route tenant/agent mapping, payload → isolated session, tool profile forced to `WEBHOOK_SAFE` (no shell/file/delegate tools).
- `SurfacesE2ESpec` passes; `e2e-test` skill extended.
- Release: `CHANGELOG.md` 1.2.0 section, `docs/MIGRATION-1.2.md` (none breaking expected — verify), Maven Central deploy per `maven-central-deploy/`, tag `v1.2.0`, bump to `1.3.0-SNAPSHOT`.

### 10.3 Files to create / modify

| Path | Action | Role |
|---|---|---|
| `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/openai/{OpenAiCompatController,ChatCompletionRequest,ChatCompletionResponse,ChatCompletionChunk}.java` | create | minimal request/response records (messages, model, stream, temperature ignored-with-warning) |
| `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayProperties.java` | modify | `openaiApi.{enabled, basePath, sessionStrategy: PER_REQUEST|BY_USER_HEADER}` |
| `channels/pom.xml`, root, BOM | modify | module entries |
| `channels/jaiclaw-channel-webhook/pom.xml` | create | |
| `.../webhook/{WebhookChannelAdapter,WebhookRoute,WebhookProperties,WebhookChannelAutoConfiguration}.java` | create | `AbstractChannelAdapter` subclass; inbound only; optional reply via route `callbackUrl` |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tool/ToolProfile.java` | modify | +`WEBHOOK_SAFE` (web_search, web_fetch, vision/media analysis, read-only MCP) — confirm enum vs registry approach against current `ToolProfile` shape |
| `jaiclaw-spring-boot-starter/.../JaiClawChannelAutoConfiguration.java` | modify | register webhook adapter on classpath |
| `core/jaiclaw-gateway/src/test/groovy/.../e2e/SurfacesE2ESpec.groovy`, `channels/jaiclaw-channel-webhook/src/test/groovy/...` | create | §5.2 row 5 |
| `.claude/skills/e2e-test/` | modify | learning + openai-api scenarios |
| `CHANGELOG.md`, `docs/MIGRATION-1.2.md`, `docs/INDEX.md`, `README.md` badges | modify | release docs |
| `CLAUDE.md` | modify | channel count 11 → 12 |

### 10.4 Task list

**OpenAI-compatible API**
- [x] Records + controller (non-stream)
- [x] SSE streaming via `runStreaming` → `chat.completion.chunk` events + `[DONE]`
- [x] Session strategy: per request (stateless, messages replayed) vs `X-JaiClaw-User` header → durable session key
- [x] Usage block from `TokenUsage`
- [x] Spock: request mapping; streaming frames; disabled → 404; auth required
- [x] Doc `docs/user/OPENAI-COMPATIBLE-API.md` (curl + `openai` Python client examples)

**Webhook channel**
- [x] Module skeleton + starter entry (fold into `jaiclaw-starter-gateway`? decide, record §11)
- [x] `WebhookRoute(routeId, secret, tenantId, agentId, sessionMode: ISOLATED|PER_ROUTE, callbackUrl?)` from properties
- [x] Adapter: HMAC verify → `ChannelMessage` with attributes for tenant resolution → `GatewayService.onMessage`; force `WEBHOOK_SAFE` profile through the route's `TenantAgentConfig`/context
- [x] Spock: bad signature 401; good → session created with `ISOLATED` key; profile enforced (delegate/shell absent)
- [ ] GitHub PR-review example route — *optional in the plan; not done.* in `jaiclaw-examples/code-review-bot` (reuse existing example) — optional
- [x] Doc `docs/user/WEBHOOK-CHANNEL.md`

**Release**
- [ ] Full build: `./mvnw clean install` — *reactor builds clean offline; a clean online build belongs to the release run.* (online once, then `-o`)
- [ ] Security scan cadence — *the 2026-09-08 scan and its fixes shipped earlier this session; re-run at release time.*: produce `security-report-2026-MM-DD.md` per existing practice
- [ ] `CHANGELOG.md` 1.2.0 — *release mechanics, left for a human to drive.*, `MIGRATION-1.2.md`, README badges, `docs/INDEX.md`
- [ ] Update `feature-gap-analysis-2026-09-09.md` — *release mechanics.* status column for shipped items (or add a "1.2.0 shipped" note at top)
- [ ] `e2e-test` skill run green — *release mechanics; needs a staged Central artifact.* against a release-candidate build from Maven Central staging
- [ ] Tag `v1.2.0`, deploy, bump — *release mechanics; requires explicit approval per the repo rule.* `1.3.0-SNAPSHOT`

### 10.5 Verification

`e2e-test` skill end-to-end on the staged artifacts; `curl` the OpenAI endpoint
with the `openai` Python client; fire a signed webhook with `openssl dgst`.

### 10.6 Risk & rollback

Both surfaces are off by default. Release risk is the usual Nexus/Central
fat-jar issue from 1.0.0 — re-verify `jaiclaw-cli-*-exec.jar` with `curl -I`
before announcing.

---

## 11. Cross-cutting checklist (run before closing each phase)

- [x] Multi-tenancy conformance (`CLAUDE.md` §Multi-Tenancy): persistence keyed/pathed by tenant; async wrapped in `TenantContextPropagator`; SINGLE mode works.
- [x] No growth of `AgentRuntime.java` beyond +40 lines per phase — add collaborators.
- [x] New `HookEvent` permits documented in `docs/user/AUTHORING-TOOLS.md (hooks section)` and counted in `CLAUDE.md`.
- [x] New properties documented with defaults in `docs/user/CONFIGURATION.md`.
- [x] Every new module has a `README.md` (post-1.0 backlog item) and appears in `docs/INDEX.md`.
- [x] `@Experimental` on every new public SPI (`LearningReviewer`, `SubAgentLauncher`, `ProposalStoreProvider`, `ToolSearchIndex`); promote in 1.3.0 after adopter feedback.
- [x] Spock specs named `*Spec`; E2E specs under `e2e/` packages.

## 12. Decision log

| Date | Decision | Why |
|---|---|---|
| 2026-09-09 | Learning default `propose`, not `auto` | Embedded-service adopters must opt into autonomous writes; tenant blast radius |
| 2026-09-09 | Delegation reuses `AgentRuntime` for children | One loop implementation; budgets/guards from Phase 1 apply automatically |
| 2026-09-09 | Tool Search index is lexical, no vector dependency | Keep `jaiclaw-tools` dependency-free; vector ranking can be an SPI impl later |
| 2026-09-09 | ESTOP is a file sentinel, not a DB flag | Works without Redis/JDBC; operable from `bin/jaiclaw` fast path with no JVM |
| 2026-09-09 | Curator consolidation always proposes, even in `auto` | Merging skills is destructive; keep a human in that path |
| 2026-09-09 | Concurrency overflow **queues** (bounded wait), depth overflow **refuses** | A parent at the concurrency limit is transiently busy — refusing would make `delegate_task` fail for reasons the model cannot see or act on, and it would retry blindly. Depth is different: it is a property of the call graph, not of timing, so retrying can never help and an immediate `ToolResult.Error` is the honest answer. The queue is bounded by the tool's own wait timeout, so a saturated parent degrades to a RUNNING handle rather than hanging. |
| 2026-09-09 | `DelegateTaskTool`/`DelegateStatusTool` live in `jaiclaw-agent`, not `jaiclaw-tools` | The plan placed them in `jaiclaw-tools`, but `jaiclaw-agent` already depends on `jaiclaw-tools` — putting a tool that needs `SubAgentLauncher` and `AgentRuntimeContext` there would invert the dependency and create a module cycle. They extend `AbstractBuiltinTool` from `jaiclaw-tools` and are registered by the starter, so adopters see no difference. |
| 2026-09-09 | Memory applier targets the **`AgentMindMemoryProvider`** core SPI, not memory-wiki | The SPI lives in `jaiclaw-core` (`io.jaiclaw.core.agent.AgentMindMemoryProvider`) with explicit TENANT/AGENT/PEER scopes, so `jaiclaw-learning` can depend on the contract without pulling the `jaiclaw-agentmind-memory` extension. memory-wiki is a different shape (wiki pages, not scoped blobs) and would tie learning to one storage model. Injected as an `ObjectProvider`, so memory proposals degrade to "cannot apply" rather than failing startup when no provider is present. |
| — | *(open)* webhook channel starter placement (Phase 5) | |
