# Hermes Memory / Soul / Tendencies — Implementation Plan

> **Status:** Phase 1 complete; Phase 2 not started.
> **Companion analysis:** [`HERMES-MEMORY-SOUL-ANALYSIS.md`](./HERMES-MEMORY-SOUL-ANALYSIS.md)
> **Resume here →** Phase 2, task 2.1 (MemoryDocument record)
> **Last updated:** 2026-06-14 (Phase 1 shipped — Soul + tenant Soul live in `extensions/jaiclaw-hermes-soul`)

Multi-session execution plan for porting hermes-agent's three concepts —
**Soul**, **Memory**, **Tendencies** — into JaiClaw. Mirrors the kanban
plan's structure. Read the companion analysis first; this document is the
"when and what-to-do-next" companion.

---

## §1 — How to use this plan

1. Find the **Resume here →** pointer above and the per-phase pointer in §3.
2. Pick the next `[ ]` task in that phase. Flip to `[x]` when done; add an
   inline `Blocked: <reason>` note if stuck.
3. A phase is not complete until **every checkbox** is ticked AND the
   **Definition of Done** is met AND the multi-tenancy conformance review
   passes.
4. Commit per task where feasible. Spock specs ship alongside production code
   — never as a follow-up PR.
5. Repo rules: `@Configuration` + `@Bean` over `@Component`; no
   `Co-Authored-By` in commit messages; example apps set
   `jaiclaw.skills.allow-bundled: []` and include the `jaiclaw-maven-plugin`.

### Two non-negotiable rules for this plan

- **Every new feature defaults OFF.** Add the autoconfig gate first, write
  the Spock spec that proves zero-beans-when-disabled, then build the
  feature. Reviewers reject PRs that skip the off-by-default spec.
- **Every new pluggable point ships an SPI + default impl + activation
  property.** If a commit hard-codes where the analysis promised an SPI,
  reviewers reject. Argue in the PR description if you genuinely think the
  SPI isn't warranted — don't silently hardcode.

---

## §2 — Module layout

See analysis §7. Do not duplicate here — it drifts.

Briefly: two new modules (`extensions/jaiclaw-hermes-soul/`,
`extensions/jaiclaw-hermes-tendencies/`) plus additions to the existing
`core/jaiclaw-memory/` for the Memory pillar, an optional sub-module
(`extensions/jaiclaw-tendencies-honcho/`), three starters, an example app
(`jaiclaw-examples/hermes-demo/`), and an e2e skill (`.claude/skills/hermes-e2e/`).

---

## §3 — Resume pointer

Updated each session. One line per phase.

- **Phase 1 (Soul foundation + tenant Soul):** ✅ **Complete** (2026-06-14).
  Shipped in 12 commits on `main` from `2428d57` through `ee5de27`. Module
  `extensions/jaiclaw-hermes-soul/` + starter `jaiclaw-starter-hermes-soul/`
  live; 107 specs green.
- **Phase 2 (Memory port + tenant Memory):** Resume here → task 2.1
  (MemoryDocument record in jaiclaw-core).
- **Phase 3 (Tendencies core, per-user):** Not started.
- **Phase 4 (Honcho + demo + e2e):** Not started.
- **Phase 5 (Tenant Tendencies + rollup):** Not started — depends on
  Phase 3 per-user pipeline.

---

## §4 — Shared E2E approach

Referenced by every phase's verification section.

### Shared fixture

- One synthetic tenant (`tenant-hermes-fixture`).
- One synthetic canonical user (`user-fixture-001`) linked to one channel.
- One synthetic per-agent `SOUL.md` with all four sections populated
  (`# Identity`, `# Style`, `# Avoid`, `# Defaults`).
- **One synthetic `TENANT-SOUL.md`** with deliberately conflicting
  `# Style` content ("bullet points") vs the per-agent Soul `# Style`
  ("technical tone"). The conflict makes additive-layering observable in
  goldens — reviewers see which scope rendered first.
- **One synthetic `TENANT.md` (tenant Memory)** with an institutional fact
  (e.g. "we use Slack #incidents").
- 10 synthetic transcript turns spanning two topics (so cadence-gated
  tendencies have substrate).
- One synthetic per-user `Tendencies` record for warm-path tests.
- **One synthetic tenant `Tendencies` record** for Phase 5 warm-path
  tests (seeded directly; the rollup pipeline doesn't have to run for
  every spec).
- Plus 4 additional synthetic per-user `Tendencies` records (different
  `userKey`s, mixed trait sets) so Phase 5's `MajorityTraitsRollupProvider`
  has an input set above the `rollup-min-active-users=3` floor.

Lives under `extensions/jaiclaw-hermes-{soul,tendencies}/src/test/resources/
hermes/fixture/` and is shared across phases — a failure points at the layer,
not the fixture.

### In-module harness

Each phase grows the in-module `@SpringBootTest` harness:

- **Phase 1** — `HermesSoulE2ESpec`: `@SpringBootTest(webEnvironment=NONE)`
  loads the fixture, fires `SessionStartedEvent`, asserts the rendered
  system prompt contains the four Soul sections verbatim after the identity
  line. Asserts the `soul` tool's add/replace/remove round-trip. Plus
  `HermesPromptCompositionOrderSpec` (TENANT before AGENT, with the
  conflicting `# Style` sections both visible),
  `HermesScopeFallThroughSpec` (Soul scopes: tenant-only / agent-only /
  both), `HermesTenantSoulOperatorOnlySpec` (agent tool rejects
  `scope=TENANT` with auth error; REST + MCP path accepts with role).
- **Phase 2** — `HermesMemoryE2ESpec`: same context plus
  `jaiclaw.hermes.memory.enabled=true`; asserts MEMORY.md + USER.md spliced
  exactly once per session (spy-counted), asserts overflow surfaces as a
  tool error. Plus `HermesScopeFallThroughSpec` extended for Memory scopes
  (TENANT/AGENT/PEER fall-through combinations);
  `HermesMemoryThreeScopeIsolationSpec` (cross-tenant + cross-user reads
  return empty across all three scopes);
  `HermesTenantMemoryAgentWriteGateSpec` (with
  `agent-write-enabled=false`, agent `memory` tool rejects
  `scope=TENANT`; with `=true`, succeeds and emits the audit counter).
- **Phase 3** — `HermesTendenciesE2ESpec` +
  `HermesTendenciesStorageE2ESpec`: `@SpringBootTest(webEnvironment=
  RANDOM_PORT)`; fires 5 `MessageReceivedEvent`s then `SessionEndedEvent`,
  asserts exactly one dialectic pass ran (cadence gate), asserts a
  `<tendencies-context>…</tendencies-context>` block lands in the next user
  message. Asserts striped-executor ordering under 10×same-user concurrent
  writes. Runs the shared `TendenciesStoreContractSpec` against JSON, H2,
  Postgres-via-Testcontainers, Redis-via-Testcontainers.
- **Phase 4** — `HermesDemoE2ESpec` (in-module) plus the out-of-process
  `.claude/skills/hermes-e2e/` skill. Skill boots `hermes-demo`, drives a
  full agent loop with the deterministic learning provider (no LLM key
  needed in CI), captures the assembled system prompt + user message, and
  byte-compares against golden files (mirroring the kanban-e2e ASCII golden
  pattern). Goldens include the tenant-scope content from the extended
  fixture.
- **Phase 5** — `HermesTenantTendenciesRollupSpec` +
  `HermesTenantTendenciesPromptSpec`. The first seeds the 5 per-user
  fixture records, advances the scheduler to fire
  `ScheduledRollupCadenceGate`, asserts `MajorityTraitsRollupProvider`
  produces the expected tenant trait map (traits present in ≥3 of 5
  per-user records land in tenant; others don't). The second asserts the
  `<tenant-tendencies>` block lands in the user message *before* the
  per-user `<tendencies-context>` block. Plus
  `HermesTenantTendenciesRollupCostBudgetSpec` (per-tenant token cap +
  circuit breaker trip behavior under the `LlmSummarizingRollupProvider`
  opt-in path).

### External E2E (Phase 4 only)

- `jaiclaw-examples/hermes-demo/` — runnable Spring Boot app with the
  fixture, a `DeterministicTendenciesLearningProvider` bean (no LLM key
  needed), a README walking through curl/scenarios.
- `.claude/skills/hermes-e2e/SKILL.md` — out-of-process e2e skill mirroring
  `.claude/skills/kanban-e2e/SKILL.md`. Six discrete phases (build, boot,
  surface checks, scenario lifecycle, prompt-golden compare, teardown).

---

## §5 — Phase 1: Soul foundation

**Scope.** Wire the prerequisite session/message events from
`SessionManager` + channel adapters. Ship the Soul pillar end-to-end with
the JSON default storage backend. Defaults OFF.

### Definition of Done

- `jaiclaw.hermes.soul.enabled=false` (default) → zero hermes beans created,
  zero hook subscriptions. `SoulAutoConfigDisabledSpec` proves it.
- `jaiclaw.hermes.soul.enabled=true` → Soul markdown injected verbatim as
  section 1 of the system prompt at session start, after the identity line
  built by `SystemPromptBuilder`.
- `soul` agent tool: `add` / `replace` / `remove` round-trip on the
  `JsonHermesStoreProvider` default backend; section addressing by markdown
  heading; **no `read` agent tool**.
- REST debug-read endpoint behind `jaiclaw.hermes.soul.rest.enabled=false`
  default.
- `SessionStartedEvent` / `SessionEndedEvent` / `MessageReceivedEvent` fire
  from `SessionManager` and from each channel adapter.
- Multi-tenancy conformance checklist (analysis §5.8) green.
- `HermesSoulE2ESpec` passes per §4.

### Files to create / modify

| Path | Action |
|---|---|
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/SessionManager.java` | Modify — fire `SessionStartedEvent` / `SessionEndedEvent` |
| `channels/jaiclaw-channel-{telegram,slack,discord,email,sms,signal,teams}/.../adapter/*.java` | Modify — fire `MessageReceivedEvent` on inbound |
| `extensions/jaiclaw-identity/src/main/java/io/jaiclaw/identity/IdentityResolver.java` | Modify — add `Optional<String> resolveExisting(channel, channelUserId)` |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/Soul.java` | Create |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/SoulProvider.java` | Create — SPI |
| `extensions/jaiclaw-hermes-soul/` | Create — new module |
| ↳ `…/io/jaiclaw/hermes/soul/{HermesSoulAutoConfiguration,HermesSoulProperties}.java` | Create |
| ↳ `…/io/jaiclaw/hermes/soul/{FileSoulProvider,JsonHermesStoreProvider,TenantRoutingHermesStore,HermesUserKeyResolver,IdentityLinkUserKeyResolver}.java` | Create |
| ↳ `…/io/jaiclaw/hermes/soul/hook/SoulPromptInjector.java` | Create — `BeforePromptBuildEvent` subscriber |
| ↳ `…/io/jaiclaw/hermes/soul/tool/SoulAgentTool.java` | Create — `add` / `replace` / `remove` |
| ↳ `…/io/jaiclaw/hermes/soul/web/SoulDebugController.java` | Create — gated off by default |
| ↳ `…/io/jaiclaw/hermes/soul/mcp/SoulMcpToolProvider.java` | Create |
| ↳ `…/io/jaiclaw/hermes/soul/web/TenantSoulController.java` | Create — operator-only write path, role-guarded |
| ↳ `…/io/jaiclaw/hermes/soul/mcp/TenantSoulMcpToolProvider.java` | Create — admin `soul.tenant.write` tool |
| ↳ `…/io/jaiclaw/hermes/soul/conflict/SoulConflictDetector.java` | Create — logs tenant↔agent section heading collisions |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/Soul.java` | Note — record carries `SoulScope { TENANT, AGENT }` from creation in 1.3 (not added in a later phase) |
| `jaiclaw-starter-hermes-soul/` | Create — starter |
| `jaiclaw-bom/pom.xml` | Modify — add new artifacts |

### Task groups

- [x] **1.1 Prerequisite event wiring.** Fire `SessionStartedEvent` and
  `SessionEndedEvent` from `SessionManager` (session lifecycle); fire
  `MessageReceivedEvent` from each of the 7 channel adapters on inbound
  message. Add one Spock spec per adapter (`{Adapter}EventWiringSpec`)
  asserting the event lands when a message arrives. Add
  `SessionManagerEventWiringSpec`.
- [x] **1.2 Module scaffold.** `extensions/jaiclaw-hermes-soul/` pom with
  optional Spring dep + Spock test deps + `jaiclaw-maven-plugin`.
  `HermesSoulAutoConfiguration` gated by
  `@ConditionalOnProperty("jaiclaw.hermes.soul.enabled", havingValue="true")`.
  Starter `jaiclaw-starter-hermes-soul/`. BOM entries.
- [x] **1.3 Soul record + provider SPI.** `Soul(scope, agentId, tenantId,
  markdown, lastModified, version)` record in `jaiclaw-core` (zero Spring
  dep) plus `enum SoulScope { TENANT, AGENT }`. `agentId` nullable when
  `scope == TENANT`. `SoulProvider` SPI in `jaiclaw-core`; implementations
  MUST handle both enum values (assertion in the SPI Javadoc).
  `FileSoulProvider` default impl in the new module — dispatches on scope
  for path selection (per analysis §5.3).
- [x] **1.4 User-key resolver.** `HermesUserKeyResolver` SPI +
  `IdentityLinkUserKeyResolver` default delegating to `IdentityResolver`.
  Deterministic-hash fallback path when `IdentityResolver` is absent from
  the classpath, gated by `@ConditionalOnClass`. WARN log at startup naming
  degraded features when fallback is in effect.
- [x] **1.5 Store provider SPI.** `HermesStoreProvider` SPI (returns
  `SoulStore` / `MemoryStore` / `TendenciesStore` sub-stores).
  `JsonHermesStoreProvider` default (mirrors `JsonTaskStoreProvider`).
  `TenantRoutingHermesStore` (mirrors `TenantRoutingTaskStore`). Only
  `SoulStore` is wired for Phase 1; the other two are stub-thrown
  `UnsupportedOperationException` for now and filled in by their respective
  phases.
- [x] **1.6 Prompt injection.** `SoulPromptInjector` subscribes to
  `BeforePromptBuildEvent`; injects the Soul markdown as section 1 of the
  system prompt, immediately after the identity line. Spec verifies the
  injected section ordering.
- [x] **1.7 `soul` agent tool.** `SoulAgentTool extends AbstractBuiltinTool`
  with `add` / `replace` / `remove` actions addressing sections by markdown
  heading (`# Identity`, `# Style`, `# Avoid`, `# Defaults`). Advisory file
  lock + `version` field reject stale writes with a typed error.
- [x] **1.8 REST debug controller.** `SoulDebugController` exposes a
  read-only endpoint at `/api/hermes/soul`, gated by
  `jaiclaw.hermes.soul.rest.enabled=false` default.
- [x] **1.9 MCP provider.** `SoulMcpToolProvider` exposes `soul.read` and
  `soul.reflect` (debug + ops surface), forwarding `TenantContext` per
  CLAUDE.md rule.
- [x] **1.10 Multi-tenancy conformance review.** Run the analysis §5.8
  checklist; verify per-tenant paths, `TenantGuard` injection, SINGLE-mode
  collapse, MCP `TenantContext` forwarding. Document the result in a
  comment block at the top of `HermesSoulAutoConfiguration`.
- [x] **1.11 Actuator counters.** Register `jaiclaw.soul.writes` (counter,
  tagged with action) and `jaiclaw.soul.size.bytes` (gauge) via Micrometer.
- [x] **1.12 Spock specs.** Per component: `FileSoulProviderSpec`,
  `JsonHermesStoreProviderSpec`, `HermesUserKeyResolverSpec`,
  `SoulAgentToolSpec`, `SoulMcpToolProviderSpec`,
  `SoulAutoConfigDisabledSpec` (zero beans when off),
  `SoulPromptInjectorSpec`, `HermesStoreIsolationSpec` (cross-tenant +
  cross-user reads return empty).
- [x] **1.13 Open question (resolved).** Per-agent Soul or per-tenant Soul?
  Resolved 2026-06-13: **both**, via `SoulScope { TENANT, AGENT }` with
  additive layering (tenant first, agent second). Analysis §10 updated.
- [x] **1.14 E2E spec.** `HermesSoulE2ESpec` per §4 — full Spring context
  loads the fixture, fires `SessionStartedEvent`, asserts system prompt
  composition, drives `soul` tool round-trip.
- [x] **1.15 Tenant Soul scope dispatch.** `FileSoulProvider` and
  `JsonHermesStoreProvider`'s `SoulStore` dispatch on `SoulScope`:
  `TENANT` writes to `${root}/{tenantId}/TENANT-SOUL.md`, `AGENT` writes to
  `${root}/{tenantId}/agents/{agentId}/SOUL.md`. Both reads pull the
  current `version` for CAS. Spock: `FileSoulProviderTenantScopeSpec`.
- [x] **1.16 Operator-only tenant Soul write path.**
  `TenantSoulController` exposes `GET /api/hermes/soul/tenant`,
  `PUT /api/hermes/soul/tenant`, `DELETE /api/hermes/soul/tenant`,
  role-guarded by `jaiclaw.hermes.soul.tenant.write.roles` (default
  `ADMIN,OPERATOR`). MCP provider `TenantSoulMcpToolProvider` exposes
  `soul.tenant.write` with the same role guard. **The `soul` agent tool
  (built in 1.7) explicitly rejects `scope=TENANT` with a typed
  authorization error** — covered in `HermesTenantSoulOperatorOnlySpec`.
  Both REST and MCP forward `TenantContext` per CLAUDE.md rule. All
  endpoints gated by `jaiclaw.hermes.soul.tenant.enabled=false` default.
- [x] **1.17 Additive layering in prompt injector.** `SoulPromptInjector`
  reads both TENANT- and AGENT-scope Souls (in that order) via the store,
  and emits them as two adjacent sections in the system prompt (tenant
  first, agent second), each preceded by the four standard markdown
  headings. **Empty sections are omitted entirely** — no placeholder
  output when a scope record is absent. `SoulConflictDetector` is invoked
  at write-time (not at prompt-build time) and logs collisions on `#
  Style`, `# Avoid`, etc. with a tagged
  `jaiclaw.hermes.soul.conflicts` counter.
- [x] **1.18 Tenant Soul specs.** `HermesPromptCompositionOrderSpec`
  (tenant block precedes agent block; empty scopes omitted),
  `HermesScopeFallThroughSpec` partial — Soul portion (tenant-only,
  agent-only, both populated cases),
  `HermesTenantSoulOperatorOnlySpec` (agent tool rejects `scope=TENANT`;
  REST + MCP accept with role, reject without),
  `TenantSoulAutoConfigDisabledSpec` (with `tenant.enabled=false`, the
  `TenantSoulController` bean is not created even if
  `soul.enabled=true`).

### Risk & rollback

Off-by-default toggle makes rollback trivial — flip the property to `false`.
Event wiring is the only non-hermes core touch; ship it even if Soul slips
to a later release, via the dedicated `SessionEventWiringSpec` as the gate.

---

## §6 — Phase 2: Memory port (inside `core/jaiclaw-memory/`)

**Scope.** Add hermes-shape Memory inside the existing `jaiclaw-memory`
module — **no new module**. Bounded markdown blobs across **three scopes**:
TENANT (institutional knowledge, one per tenant), AGENT (one per
tenant+agent, ≈ hermes MEMORY.md), PEER (one per tenant+agent+user, ≈
hermes USER.md). All scopes share the same no-read tool semantics and
overflow-as-error contract. Defaults OFF; TENANT scope opt-in independent
of pillar opt-in.

### Definition of Done

- `jaiclaw.hermes.memory.enabled=false` (default) → existing
  `WorkspaceMemoryProvider` behavior unchanged; `HermesMemoryProvider` not
  registered. `HermesMemoryAutoConfigDisabledSpec` proves it.
- `jaiclaw.hermes.memory.enabled=true` → AGENT-scope MEMORY.md and
  PEER-scope USER.md spliced into the system prompt at session start exactly
  once per session; spy provider counts `loadMemory` calls.
- `memory` agent tool exposes `add` / `replace` / `remove`; **no `read`**.
- Char-budget overflow surfaces as `MemoryOverflowException` → tool-error
  result; document unchanged; LLM forced to consolidate in-turn.
- Multi-tenancy conformance green; per-tenant + per-user file path
  structure.
- `HermesMemoryE2ESpec` passes per §4.

### Files to create / modify

| Path | Action |
|---|---|
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/MemoryDocument.java` | Create — record with `MemoryScope { TENANT, AGENT, PEER }` |
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/MemoryOverflowException.java` | Create — runtime exception |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/HermesMemoryProvider.java` | Create — extends existing `MemoryProvider`; dispatches on scope |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/BoundedBlobMemoryStore.java` | Create — JSON-file backend, scope-aware path selection per analysis §5.3 |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/MemoryOverflowPolicy.java` | Create — SPI |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/FailFastOverflowPolicy.java` | Create — default impl |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/MemoryAgentTool.java` | Create — `add` / `replace` / `remove`; rejects `scope=TENANT` unless `agent-write-enabled=true` |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/MemorySessionListener.java` | Create — `SessionStartedEvent` listener captures TENANT + AGENT + PEER snapshot |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/MemoryDebugController.java` | Create — gated off by default |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/TenantMemoryController.java` | Create — operator-only write path, role-guarded |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/mcp/MemoryMcpToolProvider.java` | Create |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/mcp/TenantMemoryMcpToolProvider.java` | Create — admin `memory.tenant.write` tool |
| `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/hermes/HermesMemoryAutoConfiguration.java` | Create — gated by `@ConditionalOnProperty`; tenant-scope sub-config gated separately |

### Task groups

- [ ] **2.1 MemoryDocument record.** `MemoryDocument(scope, tenantId,
  agentId, peerId, content, charBudget, updatedAt, version)` record in
  `jaiclaw-core` (zero Spring dep). Enum `MemoryScope { TENANT, AGENT,
  PEER }`. `agentId` nullable when `scope == TENANT`; `peerId` nullable
  when `scope ∈ {TENANT, AGENT}`. Record-level invariants documented in
  Javadoc.
- [ ] **2.2 HermesMemoryProvider.** Extend existing `MemoryProvider` SPI in
  `jaiclaw-memory`. `@ConditionalOnProperty` gate. Register with higher
  precedence than `WorkspaceMemoryProvider` when enabled.
- [ ] **2.3 BoundedBlobMemoryStore.** JSON-file backend, scope-aware
  dispatch: TENANT-scope at `${root}/{tenantId}/TENANT.md`; AGENT-scope at
  `${root}/{tenantId}/agents/{agentId}/MEMORY.md`; PEER-scope at
  `${root}/{tenantId}/users/{userKey}/USER.md`. Reuses
  `JsonHermesStoreProvider` sub-store contract from Phase 1.5 — Phase 1's
  `UnsupportedOperationException` stub for `MemoryStore` is filled here.
  TENANT scope writes only enabled when
  `jaiclaw.hermes.memory.tenant.enabled=true`.
- [ ] **2.4 MemoryOverflowPolicy SPI.** Default `FailFastOverflowPolicy`
  raises `MemoryOverflowException` when an `add` or `replace` would push
  the document past `charBudget`.
- [ ] **2.5 `memory` agent tool.** Mirror `MemorySaveTool.java` shape with
  add/replace/remove; on overflow, surface the exception as a tool-error
  result (not a runtime crash) so the LLM gets a structured prompt back.
- [ ] **2.6 Session-start snapshot capture.** `MemorySessionListener`
  subscribes to `SessionStartedEvent`, reads TENANT (if enabled), AGENT,
  and PEER documents once each, caches all three into a session attribute
  `hermes.memory.snapshot`. Prompt injector emits them in TENANT → AGENT →
  PEER order; missing scopes are omitted from the prompt (no blank
  headers). Spec uses a spy provider to verify `loadMemory` is called
  exactly once per enabled scope for the session lifetime.
- [ ] **2.7 Optional `BeforeCompactionEvent` subscriber.** When the LLM is
  about to compact, prompt it with a `memory replace` recommendation in the
  compaction system prompt. Off by default
  (`jaiclaw.hermes.memory.compaction-hint.enabled=false`).
- [ ] **2.8 REST debug controller.** `MemoryDebugController` exposes
  read-only endpoint at `/api/hermes/memory`, gated by
  `jaiclaw.hermes.memory.rest.enabled=false` default.
- [ ] **2.9 MCP provider.** `MemoryMcpToolProvider` exposes `memory.recall`,
  `memory.save`, `memory.search` (search delegates to existing
  `MemorySearchManager`).
- [ ] **2.10 Open question.** Resolve char-budget defaults. Match hermes
  (2,200 / 1,375)? Or rescale for Java context windows? Record decision in
  analysis §10.
- [ ] **2.11 Multi-tenancy conformance review.** Run analysis §5.8 against
  Memory. Verify per-tenant + per-user paths, no cross-leakage, SINGLE-mode
  collapse.
- [ ] **2.12 Actuator counters.** Register `jaiclaw.memory.writes` (counter
  tagged with action), `jaiclaw.memory.overflows` (counter), and
  `jaiclaw.memory.size.bytes` (gauge).
- [ ] **2.13 Spock specs.** `HermesMemoryProviderSpec`,
  `BoundedBlobMemoryStoreSpec`, `MemoryOverflowPolicySpec`,
  `MemoryAgentToolSpec` (overflow contract), `MemorySessionListenerSpec`
  (single-shot read), `MemoryMcpToolProviderSpec`,
  `HermesMemoryAutoConfigDisabledSpec`.
- [ ] **2.14 E2E spec.** `HermesMemoryE2ESpec` per §4.
- [ ] **2.15 TENANT scope in `BoundedBlobMemoryStore`.** Per task 2.3
  this is implemented as part of the store, but it gets its own checkbox
  so the agent-write gate (2.16) and operator path (2.17) have a clear
  prerequisite. Extends `HermesScopeFallThroughSpec` (created in Phase 1)
  with Memory cases: tenant-only, agent-only, peer-only, two-of-three,
  all-three. Adds `HermesMemoryThreeScopeIsolationSpec` verifying
  cross-tenant + cross-user reads return empty across all three scopes.
- [ ] **2.16 Agent-write gate for tenant Memory.** The `memory` agent
  tool (built in 2.5) rejects `scope=TENANT` with an authorization-style
  tool error when `jaiclaw.hermes.memory.tenant.agent-write-enabled=false`
  (default). When `=true`, writes succeed and increment a Micrometer
  counter `jaiclaw.hermes.memory.tenant.writes` tagged with `agentId` and
  `action`. Spec: `HermesTenantMemoryAgentWriteGateSpec` covers both
  states.
- [ ] **2.17 Operator tenant Memory write path.**
  `TenantMemoryController` exposes `GET /api/hermes/memory/tenant`,
  `PUT /api/hermes/memory/tenant`, `DELETE /api/hermes/memory/tenant`,
  role-guarded by `jaiclaw.hermes.memory.tenant.write.roles` (default
  `ADMIN,OPERATOR`). `TenantMemoryMcpToolProvider` exposes
  `memory.tenant.write`. Both forward `TenantContext`. All endpoints
  gated by `jaiclaw.hermes.memory.tenant.enabled=false` default. Spec:
  `TenantMemoryAutoConfigDisabledSpec` (with `tenant.enabled=false`, the
  `TenantMemoryController` bean is not created even if
  `memory.enabled=true`).

### Risk & rollback

Off-by-default toggle. `WorkspaceMemoryProvider` keeps its workspace-dir
semantics unchanged — backward-compat is the no-op state. Tenant-scope
Memory is independently gated; flipping `memory.enabled=true` without
also flipping `memory.tenant.enabled=true` ships AGENT + PEER only and
no tenant Memory beans are created.

---

## §7 — Phase 3: Tendencies core

**Scope.** New `extensions/jaiclaw-hermes-tendencies/` module. Full
`TendenciesStoreProvider` SPI from day one (JSON / JDBC H2 / JDBC Postgres /
Redis, mirroring kanban Phase 4). Striped per-user single-worker dialectic
executor. `LocalLlmTendenciesProvider` + `DeterministicTendenciesProvider`
defaults. User-message injection via `MessageReceivedEvent`. Cadence-gated
trigger on `SessionEndedEvent`. Defaults OFF.

### Definition of Done

- `jaiclaw.hermes.tendencies.enabled=false` (default) → no beans, no hook
  subscriptions. `TendenciesAutoConfigDisabledSpec` proves it.
- `jaiclaw.hermes.tendencies.enabled=true` + default provider → per-user
  `<tendencies-context>…</tendencies-context>` block spliced into user
  message after cadence gate fires.
- Striped executor processes writes for the same `(tenantId, userKey)` in
  submission order under concurrent-write race (10×same-user submissions).
- `TendenciesStoreContractSpec` (shared Spock) passes against JSON, H2,
  Postgres-via-Testcontainers, Redis-via-Testcontainers.
- `TendenciesLearningProviderContractSpec` passes against
  `LocalLlmTendenciesProvider` (with a mock `ChatModel`) and
  `DeterministicTendenciesProvider`.
- Actuator `/actuator/hermes/tendencies` reports passes-per-day, estimated
  tokens, cadence-gate hits/misses, circuit-breaker state.
- Multi-tenancy conformance green.
- `HermesTendenciesE2ESpec` + `HermesTendenciesStorageE2ESpec` pass per §4.

### Files to create / modify

| Path | Action |
|---|---|
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/Tendencies.java` | Create — record |
| `extensions/jaiclaw-hermes-tendencies/` | Create — new module |
| ↳ `…/io/jaiclaw/hermes/tendencies/{HermesTendenciesAutoConfiguration,HermesTendenciesProperties}.java` | Create |
| ↳ `…/store/{TendenciesStoreProvider,TendenciesStore,TenantRoutingTendenciesStore}.java` | Create — SPI |
| ↳ `…/store/{JsonTendenciesStoreProvider,JdbcTendenciesStoreProvider,RedisTendenciesStoreProvider}.java` | Create |
| ↳ `…/learning/{TendenciesLearningProvider,LocalLlmTendenciesProvider,DeterministicTendenciesProvider}.java` | Create |
| ↳ `…/cadence/{TendenciesCadenceGate,TimeAndTurnCadenceGate}.java` | Create |
| ↳ `…/executor/StripedDialecticExecutor.java` | Create — `ConcurrentHashMap<UserKey, ExecutorService>` |
| ↳ `…/hook/TendenciesUserMessageInjector.java` | Create — `MessageReceivedEvent` listener |
| ↳ `…/hook/TendenciesDialecticTrigger.java` | Create — `SessionEndedEvent` listener |
| ↳ `…/cost/{TendenciesTokenBudget,TendenciesCircuitBreaker}.java` | Create |
| ↳ `…/web/TendenciesController.java` | Create |
| ↳ `…/mcp/TendenciesMcpToolProvider.java` | Create |
| ↳ `…/actuator/TendenciesActuatorEndpoint.java` | Create — `@Endpoint(id="hermes/tendencies")` |
| `jaiclaw-starter-hermes-tendencies/` | Create — starter |
| `extensions/jaiclaw-hermes-tendencies/src/test/groovy/.../store/TendenciesStoreContractSpec.groovy` | Create — shared Spock base |

### Task groups

- [ ] **3.1 Module scaffold + starter.** `extensions/jaiclaw-hermes-tendencies/`
  pom (optional Spring, Spock, `jaiclaw-maven-plugin`).
  `HermesTendenciesAutoConfiguration` gated by `@ConditionalOnProperty`.
  Starter. BOM entries.
- [ ] **3.2 Tendencies record.** `Tendencies(tenantId, canonicalUserId,
  peerCardMarkdown, traits Map<String,String>, updatedAt, lastDialecticAt,
  dialecticPasses, version)` in `jaiclaw-core`.
- [ ] **3.3 TendenciesStoreProvider SPI + backends.** SPI in the new
  module. `JsonTendenciesStoreProvider` default.
  `JdbcTendenciesStoreProvider` for H2 + Postgres (composite PK `(tenant_id,
  user_key)`, index `(tenant_id, last_observed DESC)`).
  `RedisTendenciesStoreProvider` with `WATCH`/`MULTI`/`EXEC` CAS via
  `version` field. `TenantRoutingTendenciesStore`.
- [ ] **3.4 Shared contract spec.** `TendenciesStoreContractSpec` (Spock
  abstract base) mirrors `TaskStoreContractSpec`. Subclasses run against
  JSON, H2, Postgres (Testcontainers), Redis (Testcontainers).
- [ ] **3.5 TendenciesLearningProvider SPI + defaults.**
  `LocalLlmTendenciesProvider` runs one pass against the configured
  `ChatModel` (prompted with the recent transcript window) and an extraction
  call to populate the trait map. `DeterministicTendenciesProvider` uses
  rule/regex extraction (no LLM cost). Shared
  `TendenciesLearningProviderContractSpec`.
- [ ] **3.6 Cadence gate.** `TendenciesCadenceGate` SPI +
  `TimeAndTurnCadenceGate` default (`min-interval=PT15M`, `min-turns=5`).
- [ ] **3.7 Striped dialectic executor.** `ConcurrentHashMap<UserKey,
  ExecutorService>` over virtual threads. Bounded queue per stripe
  (default 4), drop-oldest on overflow. Micrometer-instrumented
  (`jaiclaw.tendencies.queue.depth`, `jaiclaw.tendencies.queue.drops`).
  Concurrent-submit race spec verifies same-user submission order.
- [ ] **3.8 User-message injector.** `TendenciesUserMessageInjector`
  subscribes to `MessageReceivedEvent`; resolves the user key via
  `HermesUserKeyResolver`; splices the rendered
  `<tendencies-context>…</tendencies-context>` block into the user message.
  Caches the rendered block per `(tenantId, userKey)` via a size-bounded
  Caffeine cache; invalidates when `updatedAt` advances.
- [ ] **3.9 Dialectic trigger.** `TendenciesDialecticTrigger` subscribes to
  `SessionEndedEvent`, consults the cadence gate, and submits to the
  striped executor when green.
- [ ] **3.10 REST controller + MCP provider.** `/api/hermes/tendencies/
  {userKey}` (GET, DELETE). `TendenciesMcpToolProvider` exposes
  `tendencies.observe`, `tendencies.query`. Forward `TenantContext`.
- [ ] **3.11 Cost guard.** `TendenciesTokenBudget` enforces per-tenant
  daily cap (`jaiclaw.hermes.tendencies.cost.daily-token-cap`, default 100k).
  `TendenciesCircuitBreaker` mirrors `MemoryCircuitBreaker.java` shape;
  trips on cap-hit and stays open until the next UTC day.
- [ ] **3.12 Actuator endpoint.** `/actuator/hermes/tendencies` reports
  passes-per-day, estimated tokens, gate hits/misses, queue depth, circuit
  state.
- [ ] **3.13 Open question.** Deterministic provider's trait vocabulary —
  ship a default vocabulary or be schema-free? Record decision in analysis
  §10.
- [ ] **3.14 Multi-tenancy conformance review.** Run §5.8 against
  Tendencies — confirm `TenantContextPropagator.wrap(...)` on every executor
  submission.
- [ ] **3.15 Spock specs.** Per concern: SPI specs,
  `TendenciesAutoConfigDisabledSpec`, `TimeAndTurnCadenceGateSpec`,
  `StripedDialecticExecutorSpec` (ordering race),
  `TendenciesCircuitBreakerSpec`, `TendenciesUserMessageInjectorSpec`,
  `TendenciesActuatorEndpointSpec`.
- [ ] **3.16 E2E specs.** `HermesTendenciesE2ESpec` (end-to-end
  message-receive → cadence-gate → dialectic-pass → next-message-injection
  on `LocalLlmTendenciesProvider` with mock `ChatModel`).
  `HermesTendenciesStorageE2ESpec` runs the contract spec against all 4
  backends.

### Risk & rollback

Off-by-default. Largest phase by far; sub-split if needed into 3a (storage
SPI + JSON + JDBC), 3b (Redis + contract spec), 3c (learning providers +
executor + injection). The 3a slice is independently useful (provides a
queryable per-user record surface even without learning) and standalone
shippable.

**Phase 5 prerequisite.** This phase ships the per-user dialectic
pipeline (`LocalLlmTendenciesProvider`, striped executor, cadence gate,
storage). The Phase 5 tenant Tendencies rollup aggregates over the
per-user records this pipeline produces — so Phase 3 must land first.
Phase 5 reuses every SPI here (just adds the rollup provider on top of
the storage layer), so no rework is needed in Phase 3 to accommodate it.

---

## §8 — Phase 4: Honcho + persona overlays + demo + e2e

**Scope.** Optional remote Honcho provider as a sub-module. Persona overlays
for Soul. Demo app + e2e skill. Dashboard contract checkbox. Docs sync.

### Definition of Done

- `extensions/jaiclaw-tendencies-honcho/` ships and **only** activates when
  the optional Honcho client dep is on classpath AND
  `jaiclaw.hermes.tendencies.provider=honcho` AND endpoint is configured.
  Without the dep, autoconfig is a no-op; without the property, the bean is
  not created. `HonchoAutoConfigDisabledSpec` and `HonchoAutoConfigEnabledSpec`
  prove both directions.
- `HonchoRemoteTendenciesProvider` passes the same
  `TendenciesLearningProviderContractSpec` as the local providers.
- Soul persona overlays — opt-in via `jaiclaw.hermes.soul.personas.dir`
  default OFF; the question of shipping the 14 hermes personas verbatim is
  resolved here.
- `jaiclaw-examples/hermes-demo/` runs end-to-end with the deterministic
  learning provider (no LLM key required); README with Problem / Solution /
  Architecture / Design / Build & Run sections per repo rules.
- `.claude/skills/hermes-e2e/` drives the demo out-of-process and
  byte-compares the assembled system prompt + user message against golden
  files.
- Docs sync — CLAUDE.md (module count + section), `docs/dev/ARCHITECTURE.md`
  (dependency graph), `docs/user/OPERATIONS.md` (config surface), dev-guide
  satellites at `/Users/tap/dev/docs/jaiclaw/`.
- Dashboard UI tracked as a single link-out checkbox; design doc filed at
  `/Users/tap/dev/docs/jaiclaw/hermes-dashboard-design.md`.

### Files to create / modify

| Path | Action |
|---|---|
| `extensions/jaiclaw-tendencies-honcho/` | Create — optional sub-module |
| ↳ `…/io/jaiclaw/hermes/tendencies/honcho/{HonchoAutoConfiguration,HonchoProperties,HonchoRemoteTendenciesProvider}.java` | Create |
| `extensions/jaiclaw-hermes-soul/.../personas/{PersonaOverlayManager,PersonaAgentTool}.java` | Create |
| `extensions/jaiclaw-hermes-soul/src/main/resources/personas/*.md` | Create (only if Phase 4 task 4.3 resolves to "ship verbatim") |
| `jaiclaw-examples/hermes-demo/` | Create — runnable Spring Boot app |
| `.claude/skills/hermes-e2e/SKILL.md` | Create |
| `.claude/skills/hermes-e2e/golden/*.txt` | Create — assembled-prompt goldens |
| `CLAUDE.md` | Modify — module count + hermes section |
| `docs/dev/ARCHITECTURE.md` | Modify — dependency graph |
| `docs/user/OPERATIONS.md` | Modify — config surface |
| `releases/release-<version>.md` | Create — release notes |

### Task groups

- [ ] **4.1 Honcho sub-module scaffold.** `extensions/jaiclaw-tendencies-honcho/`
  pom with optional Honcho client Maven dep. `HonchoAutoConfiguration` gated
  by `@ConditionalOnClass(HonchoClient.class)` AND
  `@ConditionalOnProperty("jaiclaw.hermes.tendencies.provider", "honcho")`.
- [ ] **4.2 HonchoRemoteTendenciesProvider.** Implements
  `TendenciesLearningProvider`. Runs the shared
  `TendenciesLearningProviderContractSpec`. Maps Honcho's `workspace` →
  `tenantId`, `peerName` → `userKey`.
- [ ] **4.3 Soul persona overlays.** `jaiclaw.hermes.soul.personas.dir`
  opt-in. `PersonaOverlayManager` reads `.md` files from the configured
  directory; `/personality {name}` agent tool swaps in a persona by
  filename. **Open question:** ship the 14 hermes personas verbatim, or
  just the SPI? Resolve and record in analysis §10.
- [ ] **4.4 `jaiclaw-examples/hermes-demo/`.** Runnable Spring Boot app
  with the fixture board, `DeterministicTendenciesLearningProvider` bean
  (no LLM key required), `application.yml` enables all three pillars
  **plus tenant-scope variants for all three** (Soul, Memory, Tendencies),
  `jaiclaw.skills.allow-bundled: []`, `jaiclaw-maven-plugin` in the pom.
  Fixture ships a tenant Soul + tenant Memory + (seeded) tenant Tendencies
  so the demo exercises all six scope+concept combinations in one run.
  README with Problem / Solution / Architecture / Design / Build & Run.
- [ ] **4.5 `.claude/skills/hermes-e2e/SKILL.md`.** Skill mirroring
  `kanban-e2e/SKILL.md` shape — six phases: build, boot demo, surface
  checks (Soul rendered, Memory rendered, Tendencies REST), agent loop
  (drive a conversation, fire `SessionEndedEvent`, assert next message
  carries `<tendencies-context>`), golden byte-compare on assembled system
  prompt + user message, teardown.
- [ ] **4.6 Goldens.** Capture three goldens from the demo against the
  fixture, committed under `.claude/skills/hermes-e2e/golden/`:
  (a) assembled-system-prompt with TENANT + AGENT Soul and all three
  Memory scopes populated — verifies composition order observability;
  (b) assembled-user-message with both `<tenant-tendencies>` and
  `<tendencies-context>` blocks; (c) scope-absent variant
  (only AGENT Soul + AGENT/PEER Memory + per-user Tendencies populated) —
  verifies empty-section omission.
- [ ] **4.7 First-class `HookEvent` audit.** Does Tendencies need its own
  permit (`TendenciesUpdatedEvent`)? Audit existing exhaustive switches on
  `HookEvent`. Kanban Phase 4 added `TaskStateChangedEvent` as a first-class
  permit; same decision pending here.
- [ ] **4.8 Dashboard UI placeholder.** Single checkbox + link-out to
  `/Users/tap/dev/docs/jaiclaw/hermes-dashboard-design.md`. Not in scope to
  build the UI; the REST + SSE contract from Phase 3 is the gate.
- [ ] **4.9 Docs sync.** Update `CLAUDE.md` (module count, hermes section
  one-liner), `docs/dev/ARCHITECTURE.md` (dependency graph entries),
  `docs/user/OPERATIONS.md` (full hermes configuration section), dev-guide
  satellites at `/Users/tap/dev/docs/jaiclaw/dev-guide/`.
- [ ] **4.10 Release notes.** `releases/release-<version>.md` with
  highlights, new modules, breaking changes (none expected — all opt-in),
  config surface summary.
- [ ] **4.11 In-module demo spec.** `HermesDemoE2ESpec` in the demo module
  drives the full agent loop scenario.

### Risk & rollback

All Phase 4 outputs are additive and opt-in. Honcho sub-module never
activates without explicit configuration. Demo app is a runnable example,
not a production dependency.

---

## §9 — Phase 5: Tenant Tendencies + rollup

**Scope.** Inside the existing `extensions/jaiclaw-hermes-tendencies/`
module — **no new module**. Add `TendenciesScope { TENANT, USER }` to the
`Tendencies` record (pre-Phase-3 if executed in order; nullable
`canonicalUserId` for TENANT scope). Ship the rollup pipeline: new
`TenantTendenciesRollupProvider` SPI with deterministic
`MajorityTraitsRollupProvider` default, scheduled cadence gate, tenant
Tendencies prompt injector, alternative rollup providers behind property
switches. Defaults OFF; depends on Phase 3 per-user pipeline being in
place.

### Definition of Done

- `jaiclaw.hermes.tendencies.tenant.enabled=false` (default) → no rollup
  beans, no scheduled cadence gate, no `<tenant-tendencies>` prompt
  injection. `TenantTendenciesAutoConfigDisabledSpec` proves it.
- `jaiclaw.hermes.tendencies.tenant.enabled=true` + default provider →
  scheduled rollup fires per cadence (default `PT24H`), produces a
  `Tendencies(scope=TENANT, …)` record via `MajorityTraitsRollupProvider`
  applied to active-window per-user records, splices a
  `<tenant-tendencies>…</tenant-tendencies>` block into the user message
  **before** the existing `<tendencies-context>` block.
- `rollup-min-active-users=3` floor skips rollups below threshold (logged
  INFO).
- `TendenciesStoreContractSpec` extended to cover the TENANT scope
  across JSON, H2, Postgres, Redis backends — no separate spec needed.
- Alternative `LlmSummarizingRollupProvider` and `ConsensusRollupProvider`
  both pass `TendenciesLearningProviderContractSpec` (extended to cover
  rollup semantics).
- Actuator `/actuator/hermes/tendencies/tenant` reports
  rollups-per-day, last-rollup-timestamp, active-user-count per tenant,
  estimated-tokens (LLM provider only), circuit state.
- Multi-tenancy conformance green (per-tenant scheduled jobs do not leak
  across tenants; `TenantContextPropagator` on every executor submission).
- `HermesTenantTendenciesRollupSpec` + `HermesTenantTendenciesPromptSpec`
  + `HermesTenantTendenciesRollupCostBudgetSpec` pass per §4.

### Files to create / modify

| Path | Action |
|---|---|
| `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/Tendencies.java` | Modify — add `TendenciesScope { TENANT, USER }` discriminator; nullable `canonicalUserId` when TENANT |
| `extensions/jaiclaw-hermes-tendencies/.../rollup/TenantTendenciesRollupProvider.java` | Create — SPI |
| ↳ `…/rollup/MajorityTraitsRollupProvider.java` | Create — default impl |
| ↳ `…/rollup/LlmSummarizingRollupProvider.java` | Create — opt-in via `…rollup-provider=llm` |
| ↳ `…/rollup/ConsensusRollupProvider.java` | Create — opt-in via `…rollup-provider=consensus` |
| ↳ `…/cadence/TenantTendenciesCadenceGate.java` | Create — SPI |
| ↳ `…/cadence/ScheduledRollupCadenceGate.java` | Create — default; `@Scheduled` / cron |
| ↳ `…/hook/TenantTendenciesPromptInjector.java` | Create — splices `<tenant-tendencies>` into user message before per-user block |
| ↳ `…/executor/TenantRollupExecutor.java` | Create — single virtual-thread executor (no striping needed — one job per tenant per cadence) |
| ↳ `…/cost/TenantTendenciesCostGuard.java` | Create — per-tenant rollup token cap + circuit breaker |
| ↳ `…/web/TenantTendenciesController.java` | Create — `GET /api/hermes/tendencies/tenant` read-only |
| ↳ `…/mcp/TenantTendenciesMcpToolProvider.java` | Create — `tendencies.tenant.query` |
| ↳ `…/actuator/TenantTendenciesActuatorEndpoint.java` | Create — `@Endpoint(id="hermes/tendencies/tenant")` |
| `extensions/jaiclaw-hermes-tendencies/src/test/groovy/.../TendenciesStoreContractSpec.groovy` | Modify — extend to cover TENANT scope (one extra `where:` row per case) |

### Task groups

- [ ] **5.1 `Tendencies` record scope discriminator.** Add `enum
  TendenciesScope { TENANT, USER }` to `jaiclaw-core`; `Tendencies`
  record carries `scope` field; `canonicalUserId` nullable when TENANT.
  If Phase 3 has shipped the per-user record already, this is an
  additive change (default scope = USER for forward-compat); otherwise
  it lands as part of Phase 3 task 3.2 and Phase 5 only ships the
  rollup pipeline on top. **Resolve which case applies at the start of
  this phase.**
- [ ] **5.2 Module scaffold extension.** No new module — additions
  inside `extensions/jaiclaw-hermes-tendencies/`. Tenant-scope sub-config
  gated by `@ConditionalOnProperty("jaiclaw.hermes.tendencies.tenant
  .enabled", havingValue="true")`. Independent of the pillar toggle.
- [ ] **5.3 `TenantTendenciesRollupProvider` SPI +
  `MajorityTraitsRollupProvider` default.** SPI: `Tendencies rollup(String
  tenantId, Collection<Tendencies> userTendencies)`. Default impl:
  trait in tenant map iff ≥50% of `userTendencies` have it; tenant
  `peerCardMarkdown` rendered from the trait majority via Mustache-style
  template. Zero LLM calls. Property switch
  `jaiclaw.hermes.tendencies.tenant.rollup-provider=majority` (default).
- [ ] **5.4 Alternative rollup providers.**
  `LlmSummarizingRollupProvider` (one LLM call summarizes the trait set;
  opt-in via `…rollup-provider=llm`) and `ConsensusRollupProvider`
  (≥75% threshold; opt-in). Both pass the shared
  `TendenciesLearningProviderContractSpec` extended for rollup.
- [ ] **5.5 `TenantTendenciesCadenceGate` SPI +
  `ScheduledRollupCadenceGate` default.** Default impl: cron-driven via
  `jaiclaw.hermes.tendencies.tenant.rollup-cadence` (default `PT24H`).
  Sampling guard: `rollup-min-active-users=3` floor (logged INFO when
  skipping). Active window: `active-window=PT168H` bounds the input
  per-user record set.
- [ ] **5.6 Tenant rollup executor.** Single virtual-thread executor
  (one job per tenant per cadence — no striping needed). Wrapped via
  `TenantContextPropagator` per repo rule. Micrometer-instrumented
  (`jaiclaw.tendencies.rollup.duration`,
  `jaiclaw.tendencies.rollup.skipped`).
- [ ] **5.7 Tenant Tendencies prompt injector.**
  `TenantTendenciesPromptInjector` subscribes to `MessageReceivedEvent`
  (alongside the existing per-user injector); splices the rendered
  `<tenant-tendencies>…</tenant-tendencies>` block into the user message
  **before** the existing `<tendencies-context>` block. Caches the
  rendered tenant block on a separate Caffeine cache keyed by
  `tenantId` only (per analysis §8.3); invalidates when the tenant
  `Tendencies.updatedAt` advances.
- [ ] **5.8 Per-tenant cost guard.** `TenantTendenciesCostGuard`
  enforces a daily token cap on LLM-provider rollups (`…cost
  .daily-token-cap`, default 100k, separate from the per-user budget).
  Circuit breaker mirrors the per-user `TendenciesCircuitBreaker` shape;
  trips on cap-hit, stays open until next UTC day.
- [ ] **5.9 REST + MCP read surfaces.** `TenantTendenciesController`
  exposes `GET /api/hermes/tendencies/tenant` (no write — rollup is the
  only writer). `TenantTendenciesMcpToolProvider` exposes
  `tendencies.tenant.query`. Both forward `TenantContext`. Both gated
  by `tendencies.tenant.enabled=true`.
- [ ] **5.10 Actuator endpoint.** `/actuator/hermes/tendencies/tenant`
  reports rollups-per-day, last-rollup-timestamp, active-user-count per
  tenant, estimated-tokens (LLM provider only), circuit state, skip
  count + reason (active-user-floor / disabled / circuit-open).
- [ ] **5.11 Contract spec extension.** `TendenciesStoreContractSpec`
  gains TENANT-scope coverage (one extra parameter case per existing
  `where:` row). Runs against JSON, H2, Postgres, Redis backends — no
  separate spec needed.
- [ ] **5.12 Open question.** Default trait vocabulary for
  `MajorityTraitsRollupProvider`? Ship a default taxonomy (e.g.
  `working_hours`, `comm_style`, `tech_stack`) that constrains the
  tenant map, or be schema-free? Resolve and record in analysis §10.
- [ ] **5.13 Multi-tenancy conformance review.** Confirm
  `TenantContextPropagator.wrap(...)` on every rollup executor
  submission, on every `@Scheduled` invocation, and that the scheduler
  iterates tenants explicitly (not implicitly via any global state).
- [ ] **5.14 Spock specs.** `TenantTendenciesAutoConfigDisabledSpec`
  (off-by-default — independent of pillar toggle),
  `MajorityTraitsRollupProviderSpec`,
  `ConsensusRollupProviderSpec`, `ScheduledRollupCadenceGateSpec`,
  `TenantTendenciesPromptInjectorSpec`,
  `TenantTendenciesCostGuardSpec`,
  `TenantTendenciesActuatorEndpointSpec`.
- [ ] **5.15 E2E specs.** `HermesTenantTendenciesRollupSpec` (seed 5
  per-user records, advance the scheduler, assert majority-rules trait
  map), `HermesTenantTendenciesPromptSpec` (assert composition order:
  tenant block precedes per-user block in the user message),
  `HermesTenantTendenciesRollupCostBudgetSpec` (verify cost cap +
  circuit breaker trip under the LLM provider).

### Risk & rollback

Off-by-default. Phase 5 is additive on top of Phase 3 — disabling
`jaiclaw.hermes.tendencies.tenant.enabled=false` leaves the per-user
pipeline untouched. The rollup is a scheduled batch, never on the
response hot path; failures of an individual rollup job do not affect
in-flight chat sessions. Recovery on failure: the next scheduled
cadence retries automatically; no per-job state machine needed (last-
write-wins semantics on `compareAndSave`).

---

## §10 — Cross-cutting checklist (applied per task)

- **Off by default.** Add the autoconfig gate + the `*AutoConfigDisabledSpec`
  first. Everything else follows.
- **Tenant-scope opt-in is independent of pillar opt-in.** Each pillar's
  TENANT-scope behavior is gated by its own
  `jaiclaw.hermes.{soul,memory,tendencies}.tenant.enabled` property, which
  must be flipped in addition to the pillar-level toggle. Every
  tenant-scope feature ships a dedicated `Tenant*AutoConfigDisabledSpec`
  verifying that the pillar can be enabled without the tenant beans
  appearing.
- **SPI + default for every pluggable point.** If you're not adding an SPI,
  argue it in the PR description — don't silently hardcode.
- **Multi-tenancy.** `TenantGuard` injection (never `TenantContextHolder`
  directly); `TenantContextPropagator` on every async hop; per-tenant
  path/key prefixing; SINGLE-mode null-safety; MCP `TenantContext`
  forwarding. Run analysis §5.8 checklist per phase.
- **Spock spec parity.** Specs end in `Spec`. Specs ship with production
  code per commit — never as a follow-up PR.
- **No `Co-Authored-By`** in commit messages (repo rule).
- **`jaiclaw.skills.allow-bundled: []`** in every example app's
  `application.yml` (repo rule).
- **`jaiclaw-maven-plugin`** in every example pom (repo rule).
- **SPI freeze marker.** End of Phase 3 freezes `SoulProvider`,
  `HermesMemoryProvider` extension contract, `HermesStoreProvider`,
  `TendenciesStoreProvider`, `TendenciesLearningProvider`,
  `HermesUserKeyResolver`, `TendenciesCadenceGate`, `MemoryOverflowPolicy`.
  End of Phase 5 also freezes `TenantTendenciesRollupProvider` and
  `TenantTendenciesCadenceGate`. Changes after the corresponding freeze
  point require a deprecation note appended to this plan.
- **Render cache hygiene.** Tendencies' rendered-block cache (Caffeine)
  must be size-bounded and tenant-aware. Multi-tenant deployments with
  many users can't accumulate unbounded entries.
- **Stretch goal: SPI for Soul section parsing.** Currently inline; if a
  consumer asks for a non-markdown section format, lift to
  `SoulSectionParser` SPI.

---

## §11 — Open questions

Mirrors analysis §10. Resolved inline as phases land.

- **Memory char-budget defaults** — match hermes (2,200 / 1,375) or rescale?
  **Open** — Phase 2 task 2.10.
- **Tendencies deterministic vocabulary** — default vocabulary or
  schema-free? **Open** — Phase 3 task 3.13.
- **Persona overlays** — ship 14 verbatim or SPI-only? **Open** — Phase 4
  task 4.3.
- **Cross-agent Soul sharing** — per-agent or per-tenant? **Resolved
  2026-06-13: both**, via `SoulScope { TENANT, AGENT }` with additive
  layering.
- **Tenant Soul role-guard defaults across deployment styles** — is
  `ADMIN,OPERATOR` the right default everywhere, or should single-tenant
  deployments default to allowing the gateway-owner role? **Resolved
  2026-06-14:** ship `[ADMIN, OPERATOR]` as the universal default.
  Single-tenant deployments override via
  `jaiclaw.hermes.soul.tenant.write.roles` to widen the list. The role
  check is performed against the standard servlet
  `HttpServletRequest.isUserInRole`, so any auth integration that
  surfaces roles on the request lights up the guard automatically — no
  Spring Security dependency required.
- **Tenant Memory char-budget default** — 4,096 vs scaling vs fixed?
  **Open** — Phase 2 task 2.10 (resolved alongside per-user budgets).
- **Tenant Tendencies rollup vocabulary** — ship a default trait
  taxonomy or be schema-free? **Open** — Phase 5 task 5.12.

---

## §12 — Decision log

Append-only. Records decisions made **during execution** (not commits — `git
log` covers commits). Format: `YYYY-MM-DD — decision — rationale`.

- **2026-06-14 — Phase 1 shipped.** All 18 Phase 1 tasks (1.1–1.18) landed
  on `main` across 12 commits (`2428d57` → `ee5de27`). 107 specs green in
  `extensions/jaiclaw-hermes-soul`; agent + gateway event wiring covered
  by 21 specs across `core/jaiclaw-agent` and `core/jaiclaw-gateway`.
  Notable execution-time decisions:
    - **MessageReceivedEvent funnel point.** Wired from
      `GatewayService.onMessage` (the single inbound funnel for all 11
      channel adapters) instead of per-adapter — one dispatch site instead
      of 11. Plan §5 task 1.1 originally said "fire from each of the 7
      channel adapters"; the real channel count is 11 and the funnel
      design is strictly cleaner.
    - **HermesUserKeyResolver lives in the soul module, not core.** Per
      analysis §5.7 the resolver is a thin internal interface that becomes
      mechanical to swap when `UserContext` lands. Plan §1.4 already
      anticipated this.
    - **Role-guard implementation.** `TenantSoulController` uses the
      standard servlet `HttpServletRequest.isUserInRole` so the guard
      lights up against any auth integration (Spring Security, custom
      JWT, plain servlet filter) without requiring a hard Spring Security
      dep.
    - **MCP server segregation.** Soul read (`hermes-soul`) and tenant
      admin write (`hermes-soul-tenant-admin`) live on separate server
      names so the host can restrict the admin server to admin clients
      without duplicating role logic in the tool layer — MCP transports
      do not carry the role concept the REST layer relies on.
    - **InstrumentedSoulProvider deferred gauge.** The
      `jaiclaw.soul.size.bytes` gauge planned for task 1.11 was deferred
      behind a TODO in the decorator's Javadoc. Gauges require a polling
      cadence and the file backend does not scan disk on read; a
      per-write size distribution-summary will land in a follow-up rather
      than paying polling cost.
    - **Pre-existing Schedulers import fix.** `WebClientSseTransport` in
      `core/jaiclaw-gateway` had an unrelated missing
      `reactor.core.scheduler.Schedulers` import that blocked compilation
      of the gateway module; fixed in the task 1.1 commit so the
      `GatewayServiceMessageEventSpec` could compile.
- **2026-06-13 — Tenant-scope variants added across all three concepts.**
  Soul and Memory gain enum-discriminated TENANT scope inside their
  existing Phase 1/2 ship; Tendencies tenant-scope variant defers to a
  new Phase 5 because its rollup pipeline depends on the per-user
  pipeline shipping first. No new modules. Composition order
  (tenant → agent → user) chosen for additive layering with empty-section
  omission. All tenant-scope behavior is opt-in via independent
  `*.tenant.enabled` toggles atop the existing pillar toggles.
- **2026-06-13 — `SoulScope { TENANT, AGENT }` over a separate
  `TenantSoul` record.** Same for `MemoryScope { TENANT, AGENT, PEER }`
  and `TendenciesScope { TENANT, USER }`. Single record per concept with
  scope discriminator chosen to keep call-sites uniform and avoid
  doubling the SPI surface.
- **2026-06-13 — Per-agent Soul path corrective tweak.** Per-agent Soul
  moves from `${root}/{tenantId}/SOUL.md` to
  `${root}/{tenantId}/agents/{agentId}/SOUL.md` to free the root for
  tenant Soul. Pre-Phase 1; no migration.

---

## Verification of this plan

This plan is the persistent execution map. To verify it works as intended:

1. **Doc independence.** This plan reads end-to-end without the analysis,
   and the analysis reads end-to-end without this plan. No circular reading
   requirement.
2. **Resume pointer.** After Phase 1 task 1.1 lands in a future session,
   the resume pointer at the top updates, the `[ ]` for 1.1 flips to `[x]`,
   and another session can pick up at 1.2 without scanning the analysis.
3. **Off-by-default invariant.** Every task that adds a bean has a
   companion `*AutoConfigDisabledSpec` — gates merging. Tenant-scope
   features additionally have a `Tenant*AutoConfigDisabledSpec` proving
   that pillar-on + tenant-off produces no tenant beans.
4. **SPI inventory.** Every pluggable point in analysis §6 has a row in
   the SPI inventory table. If a commit hard-codes where an SPI was
   promised, code review rejects.
5. **Companion analysis link.** Header points at the analysis path;
   analysis §10 hands its open questions to §11 here.
6. **Phase 5 standalone-shipability.** Phase 5 can be skipped entirely
   (`jaiclaw.hermes.tendencies.tenant.enabled=false`) and Phases 1–4 are
   unaffected. The rollup is additive on top of the per-user pipeline.

The docs themselves are the only deliverable of the planning session — no
code, no module scaffolding, no commits beyond the two `.md` files.
