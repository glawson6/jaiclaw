# AgentMind Memory / Soul / Tendencies — Design Analysis

> **Status:** Draft for review.
> **Companion plan:** [`AGENTMIND-MEMORY-SOUL-PLAN.md`](./AGENTMIND-MEMORY-SOUL-PLAN.md)
> **Last updated:** 2026-06-13 (tenant-scope variants added)

This document captures the design rationale for porting three durable agent-state
concepts from `NousResearch/hermes-agent` into JaiClaw: **Soul** (personality
overlay), **Memory** (bounded markdown blobs spliced into the system prompt), and
**Tendencies** (per-user learned representation injected into the user message).

It is paired with a multi-session execution plan. This document explains *why*
and *what*; the plan explains *when* and *who-touches-what*.

---

## §1 — Why this exists

AgentMind-agent's three concepts solve a class of problem JaiClaw users have repeatedly
asked about: an agent that *remembers* across sessions, holds a stable *personality*
the operator can shape, and quietly *learns* what each user is like over time. The
combination produces continuity ("you mentioned last week…"), voice ("you sound
like the agent we set up"), and adaptation ("you got more concise after I asked
for shorter replies") — without forcing the operator into a vector-DB project.

Today JaiClaw covers parts of this surface but not the whole. There is a
`MemoryProvider` SPI with `WorkspaceMemoryProvider` and `VectorStoreSearchManager`
covering search-based memory; a `SessionTranscriptStore` covering conversation
durability; `AgentIdentity` covering a static `(name, description)` per-agent
label. What's missing:

- **No Soul concept.** `AgentIdentity` is two strings — there's no place for
  operator-authored personality, style, or "avoid" guidance that ships as a
  markdown overlay and gets spliced verbatim into the system prompt.
- **No bounded markdown-blob memory.** `MemoryProvider` retrieves content;
  there's nothing that holds a single bounded markdown document the LLM mutates
  in place via tool calls, with overflow surfaced as a tool error so the model
  is *forced* to consolidate in-turn rather than letting the document grow
  silently.
- **No per-user learned-state surface at all.** `IdentityLink.canonicalUserId`
  exists for cross-channel user identification — but no persisted per-user
  representation lives anywhere in the framework. Tendencies fills this gap.

The port preserves hermes' vocabulary (`Soul`, `Memory`, `Tendencies`) so the
lineage is obvious and operators familiar with hermes can move between the two
projects.

---

## §2 — AgentMind-agent reference model

### Three concepts at a glance

| Concept | Storage (hermes) | Format | Prompt slot | Evolution |
|---|---|---|---|---|
| **Soul** | `~/.agentmind/SOUL.md` | Free markdown with `# Identity` / `# Style` / `# Avoid` / `# Defaults` headings | Slot 1 of the system prompt, verbatim | Effectively static; "self-evolution" is the LLM calling memory-write tooling against it |
| **Memory** | `~/.agentmind/memories/MEMORY.md` (~2,200 chars) + `USER.md` (~1,375 chars); SQLite + FTS5 for session history | Markdown blob, §-delimited entries | Frozen snapshot in the system prompt at session start; never re-read mid-session (prefix caching) | `memory` tool with `add` / `replace` / `remove`; **no `read` action**; overflow returns an error so the LLM consolidates in-turn |
| **Tendencies (Honcho plugin)** | Remote Honcho API, keyed by `workspace` + `peerName` | Server-side Peer Cards, User Representation, Conclusions, semantic vectors | Injected into the **user message** (not system prompt) wrapped in `<memory-context>` fences | Dialectic 1–3-pass LLM reasoning; async cadence-gated; never on the response hot path |

### Per-turn composition order

1. **Session start.** SOUL.md is read once and slotted as system-prompt section 1.
   MEMORY.md and USER.md are read once and snapshotted into a system-prompt block.
   System prompt is now **locked** for the session.
2. **Per turn** (if cadence due). Honcho fetches base context + optionally a
   dialectic supplement; the result is spliced into the **user message** wrapped
   in `<memory-context>…</memory-context>` so the system prompt stays
   cache-stable.
3. **LLM call.**
4. **Mid-turn tools.** The LLM may call `memory` (add/replace/remove),
   `session_search` (FTS5 over history), or Honcho tools.
5. **Post-turn.** `MemoryManager.sync_all(...)` runs on a single-worker
   `ThreadPoolExecutor` — writes are serialized (turn N's writes complete
   before turn N+1's) but off the response path.

### What would surprise a Java/Spring porter

- Soul/Memory blobs are **raw markdown spliced into the prompt** — not
  normalized JPA entities. Modelling them as tables loses the design.
- Memory has **no `read` API**. Exposing one per turn would defeat prompt-prefix
  caching, which is the whole point of the snapshot-at-session-start invariant.
- Honcho injects into the **user message**, not the system prompt — opposite of
  most Spring AI advisors. Same caching reason.
- Writes use a **single-worker executor** to preserve turn ordering. A default
  Spring `@Async` pool reorders.
- Memory overflow is **error-as-control-flow** — the tool returns an error that
  forces the LLM to compact in-turn. Java instinct ("silently evict / grow")
  loses the safety property.
- Soul "self-evolution" is **emergent** — there is no `SoulUpdater` service.
  The LLM decides when to call the write tool against SOUL.md.
- Honcho is **remote, opt-in, billed per dialectic pass** — must be a
  cadence-gated pluggable provider, never on the hot path by default.

### Reference files in `NousResearch/hermes-agent`

- `agent/memory_manager.py` — orchestrator + `StreamingContextScrubber`.
- `agent/memory_provider.py` — Memory SPI.
- `website/docs/user-guide/features/memory.md` — Memory user docs.
- `website/docs/user-guide/features/personality.md` — Soul / `SOUL.md` docs.
- `plugins/memory/honcho/README.md` — Honcho plugin shape.

---

## §3 — JaiClaw existing surfaces audit

### §3.1 Soul-shaped surfaces

| Concern | What exists today | Gap to fill |
|---|---|---|
| Per-agent label | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/AgentIdentity.java` — record `(id, name, description)`; resolved by `GatewayService` lines 182-188 from `tenantConfig.identity()` | No markdown overlay, no section-addressable write API, no operator-facing personality surface |
| System-prompt assembly | `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/SystemPromptBuilder.java` — `"You are {name}, {description}."` + skills + `additionalInstructions` | No first-class slot for a Soul markdown blob; everything has to land in `additionalInstructions` |
| Skills as instructional content | `core/jaiclaw-skills/` with `TenantSkillRegistry` (tenant-scoped) | Skills aren't personality — they're tool / domain instructions. A Soul is not a skill. |
| Tenant-aware? | `TenantContext` already exists; current Soul keys on `(tenantId, agentId)` | A `TENANT` scope variant is a same-axis extension — operator-authored org voice that layers under each agent's Soul |

**Conclusion.** Soul is a new concept. `AgentIdentity` is the closest existing
thing; Soul *composes with* it rather than extends it (changing `AgentIdentity`
would force a multi-module migration). Soul gains a `SoulScope { TENANT, AGENT }`
discriminator so org-wide voice and per-agent voice coexist (see §4.1).

### §3.2 Memory-shaped surfaces

| Concern | What exists today | Gap to fill |
|---|---|---|
| Memory-into-prompt SPI | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/agent/MemoryProvider.java`; default impl `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/WorkspaceMemoryProvider.java` | Provider is search-shaped, not blob-shaped — no bounded markdown document with no-read tool semantics |
| Session-history durability | `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/SessionTranscriptStore.java` — JSONL transcripts, tenant-aware (`sessions/{tenantId}/{sessionKey}.jsonl` in MULTI) | AgentMind uses SQLite+FTS5 here; the JaiClaw equivalent (`SessionTranscriptStore` + `MemorySearchManager`) already exists and the port should **reuse**, not parallel |
| Semantic search | `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/MemorySearchManager.java` — interface; impls `InMemorySearchManager`, `VectorStoreSearchManager`, `HybridSearchManager` | Adequate as the equivalent of hermes' FTS5 surface |
| Long-form knowledge base | `extensions/jaiclaw-memory-wiki/src/main/java/io/jaiclaw/wiki/WikiRepository.java` | Different concept — not part of this port |
| Context compaction | `extensions/jaiclaw-compaction/` | Tendencies' "what changed" pipeline can reuse compaction primitives |
| Tenant-aware? | `SessionTranscriptStore` already prefixes by `tenantId` | A `TENANT` scope value on `MemoryScope` adds institutional-knowledge memory shared across all agents and users in the tenant |

**Conclusion.** Memory is a *new shape* of an existing SPI. The port adds a
`AgentMindMemoryProvider` (extending `MemoryProvider`) and a `MemoryDocument`
record **inside the existing `core/jaiclaw-memory/` module** — no new module.
`MemoryScope` grows to `{ TENANT, AGENT, PEER }` so the same record covers all
three layers (see §4.2).

### §3.3 Tendencies-shaped surfaces

| Concern | What exists today | Gap to fill |
|---|---|---|
| Per-user identifier | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/IdentityLink.java` — record `(canonicalUserId UUID, channel, channelUserId, tenantId)` | The identifier exists, but nothing is keyed off it |
| Per-user data | `extensions/jaiclaw-identity/src/main/java/io/jaiclaw/identity/IdentityLinkStore.java` — only channel↔user mappings | No persistent per-user representation surface anywhere |
| Tenant boundary | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/{TenantContext,TenantGuard}.java` | Tenant is the only isolation boundary; per-user-within-tenant is unimplemented |
| Session-level user reference | `core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/Session.java` — `sessionKey` = `agentId:channel:accountId:peerId`; carries `tenantId` but no `canonicalUserId` field | User has to be derived from `(channel, peerId)` via `IdentityLinkService` per session |
| Tenant-aware? | Nothing aggregates per-user observations into org-level patterns today | A `TENANT` scope variant is *rollup-derived* from per-user Tendencies on a scheduled cadence — neither agent-written nor operator-written directly |

**Conclusion.** Tendencies is entirely net-new. The port introduces a
`Tendencies` record + `TendenciesStoreProvider` SPI + a learning pipeline. The
codebase's per-tenant isolation pattern is preserved; the new module adds the
per-user dimension *inside the tenant boundary*. Tendencies gains a
`TendenciesScope { TENANT, USER }` discriminator; the TENANT variant is
computed by a new `TenantTendenciesRollupProvider` SPI (see §4.3 and §8.2).

---

## §4 — Concept mapping

### §4.1 Soul

- **Data model.** New record in `jaiclaw-core` (zero Spring dep, matching the
  existing `AgentIdentity` shape):
  ```
  Soul(SoulScope scope, String agentId, String tenantId, String markdown,
       Instant lastModified, long version)
  enum SoulScope { TENANT, AGENT }
  ```
  `agentId` is null when `scope == TENANT` (one Soul per tenant). The
  `markdown` field is the verbatim SOUL.md content. `version` enables
  optimistic-CAS rejection of stale writes.
- **Storage.** JSON-on-disk by default — `${root}/{tenantId}/SOUL.md`
  (SINGLE-mode: `${root}/SOUL.md`). Soul is per-tenant, ~few-KB, rarely written.
  Multi-backend (`H2`/`Postgres`/`Redis`) deferred behind the shared
  `AgentMindStoreProvider` SPI introduced in Phase 1, but JSON is the default and
  the only required backend until ops demand otherwise.
- **Prompt path.** A `BeforePromptBuildEvent` subscriber injects the Soul
  markdown verbatim as **section 1** of the system prompt, immediately after
  the identity line built by `SystemPromptBuilder` (`"You are {name},
  {description}."`). One-shot at session start; never re-read mid-session.
- **Write API + evolution.** A `soul` agent tool exposes
  `add` / `replace` / `remove` actions addressing sections by markdown heading
  (`# Identity`, `# Style`, `# Avoid`, `# Defaults`). **No `read` action** in
  the agent tool. A REST debug-read endpoint exists for ops, gated by
  `jaiclaw.agentmind.soul.rest.enabled=false` default. Evolution is emergent —
  the LLM calls the tool when it decides to; no `SoulUpdater` service.
  Concurrent writes from parallel agent runs are guarded by an advisory file
  lock + `version` field; stale writes are rejected.

- **Tenant-scope variant.** Setting `scope == TENANT` produces an org-wide
  Soul — one record per `tenantId`, representing the operator-authored
  voice/personality every agent in the tenant inherits. Write authority is
  **operator-only**: REST endpoint `PUT /api/agentmind/soul/tenant` (role-guarded
  by `jaiclaw.agentmind.soul.tenant.write.roles=ADMIN,OPERATOR`) and the
  `soul.tenant.write` MCP tool (same role guard). **Agents cannot write to
  the TENANT scope** — the `soul` agent tool returns an authorization error
  if invoked with `scope=TENANT`. Tenant Soul lands in the prompt **before**
  per-agent Soul, layered additively (both render; the LLM weights the
  later/per-agent guidance higher, so the agent voice implicitly refines but
  the tenant's intent stays in-prompt). Opt-in via
  `jaiclaw.agentmind.soul.tenant.enabled=false` (default OFF) — independent of
  the pillar-level `jaiclaw.agentmind.soul.enabled` toggle.

### §4.2 Memory

- **Data model.** New record in `jaiclaw-core`:
  ```
  MemoryDocument(MemoryScope scope, String tenantId, String agentId,
                 String peerId, String content, int charBudget, Instant updatedAt)
  enum MemoryScope { TENANT, AGENT, PEER }
  ```
  `TENANT` is one record per `tenantId` — institutional knowledge shared
  across all agents and users (new in this design). `AGENT` ≈ hermes'
  MEMORY.md (one per tenant+agent). `PEER` ≈ hermes' USER.md (one per
  tenant+agent+user). `peerId` is null when `scope == AGENT`; both `agentId`
  and `peerId` are null when `scope == TENANT`.
- **Storage.** JSON-on-disk by default — directory layout described in §5
  below. Reuses the same `AgentMindStoreProvider` SPI as Soul.
- **Prompt path.** A `SessionStartedEvent` listener reads both AGENT and PEER
  documents, captures them into a session attribute `agentmind.memory.snapshot`,
  and the `BeforePromptBuildEvent` subscriber splices the snapshot block into
  the system prompt. **One-shot read** per session lifetime — verified by a
  spy provider in the E2E spec counting `loadMemory` calls.
- **Write API + evolution.** A `memory` agent tool exposes
  `add` / `replace` / `remove` actions; **no `read`**. A REST debug-read
  endpoint exists for ops only, gated off by default. Char-budget overflow
  surfaces as a `MemoryOverflowException` translated into a tool-error result
  for the LLM — the document is unchanged and the LLM is forced to consolidate
  in-turn. Optional `BeforeCompactionEvent` subscriber prompts the LLM with a
  `memory replace` recommendation during compaction.

- **Tenant-scope variant.** Setting `scope == TENANT` produces an org-wide
  Memory — one record per `tenantId`, representing institutional knowledge
  every agent and user inherits ("we use Slack #incidents for outages",
  product taxonomy, escalation contacts). Write authority is **operator-only
  by default** via `PUT /api/agentmind/memory/tenant` (role-guarded by
  `jaiclaw.agentmind.memory.tenant.write.roles=ADMIN,OPERATOR`). Agents can
  write via the `memory` tool with `scope=TENANT` **only when**
  `jaiclaw.agentmind.memory.tenant.agent-write-enabled=true` (default OFF) —
  most deployments will only want operator-curated tenant Memory to prevent
  institutional poisoning (§9 risk 7). When agent writes are enabled, a
  Micrometer counter `jaiclaw.agentmind.memory.tenant.writes` tagged with
  `agentId` provides an audit trail. Char budget defaults to 4,096
  (configurable via `jaiclaw.agentmind.memory.tenant.char-budget`) — larger
  than AGENT/PEER scopes because shared knowledge needs room. Tenant Memory
  lands in the prompt **before** AGENT Memory which lands before PEER
  Memory, layered additively (most-general → most-specific). Opt-in via
  `jaiclaw.agentmind.memory.tenant.enabled=false` (default OFF) — independent
  of the pillar toggle.

### §4.3 Tendencies

- **Data model.** New record in `jaiclaw-core`:
  ```
  Tendencies(TendenciesScope scope, String tenantId, String canonicalUserId,
             String peerCardMarkdown, Map<String,String> traits,
             Instant updatedAt, Instant lastDialecticAt,
             long dialecticPasses, long version)
  enum TendenciesScope { TENANT, USER }
  ```
  `canonicalUserId` is null when `scope == TENANT` — one tenant-scope record
  per `tenantId`, computed by the rollup pipeline (see Tenant-scope variant
  below and §8.2). **Hybrid representation.** `peerCardMarkdown` is hermes-faithful: a free-form
  markdown blob analogous to Honcho's Peer Cards, spliced into the user message
  verbatim. `traits` is a structured map populated by the same dialectic pass —
  consumed by dashboards, REST clients, and analytics. The cost of computing
  both is one extra structured-output call per pass; the benefit is that
  JaiClaw's non-prompt consumers (which hermes doesn't have) get a structured
  surface without parsing markdown.
- **Storage.** Full `TendenciesStoreProvider` SPI from day one — JSON / JDBC
  (H2 + Postgres via composite PK `(tenant_id, user_key)`) / Redis (with
  `WATCH`/`MULTI`/`EXEC` CAS via the `version` field). Mirrors kanban's
  `TaskStoreProvider` precedent; shared `TendenciesStoreContractSpec` (Spock)
  pins SPI semantics across backends.
- **Prompt path.** A `MessageReceivedEvent` listener resolves the user key for
  the inbound message and splices the cached `<tendencies-context>…
  </tendencies-context>` block into the user message content. **Not** into the
  system prompt — system prompt stays cache-stable per the hermes design.
  Rendered block is cached and invalidated when `updatedAt` changes.
- **Evolution — the dialectic pipeline.** Cadence-gated. A
  `SessionEndedEvent` listener checks the `TendenciesCadenceGate`
  (`min-interval=PT15M`, `min-turns=5` by default) and, if green, dispatches a
  job to a striped single-thread executor keyed by `(tenantId,
  canonicalUserId)`. The job invokes the `TendenciesLearningProvider` (default
  `LocalLlmTendenciesProvider` — one pass against the configured `ChatModel`;
  alternative `DeterministicTendenciesProvider` for zero-LLM-cost extraction;
  optional `HonchoRemoteTendenciesProvider` in a separate sub-module for
  remote dialectic). The result is persisted via `compareAndSave` (CAS),
  invalidating the cached render block.

- **Tenant-scope variant.** Setting `scope == TENANT` produces an org-wide
  Tendencies record — one per `tenantId`, representing observed organization
  patterns ("this org communicates in bullet points, works in EST hours,
  prefers concise replies"). **Never written by agents or operators
  directly.** Computed by a new `TenantTendenciesRollupProvider` SPI on a
  scheduled cadence (`TenantTendenciesCadenceGate`, default `PT24H`),
  aggregating across the per-user Tendencies records in the tenant.
  - Default provider `MajorityTraitsRollupProvider`: deterministic — a trait
    is in the tenant map if ≥50% of active users (`updatedAt > now -
    PT168H`) have it. Tenant `peerCardMarkdown` is rendered from the trait
    majority via a template (no LLM call required for the default).
  - Alternative `LlmSummarizingRollupProvider`: one LLM call per rollup;
    opt-in via `jaiclaw.agentmind.tendencies.tenant.rollup-provider=llm`.
  - Alternative `ConsensusRollupProvider`: stricter ≥75% threshold; opt-in.
  - Sampling guard: `jaiclaw.agentmind.tendencies.tenant.rollup-min-active-users=3`
    skips the rollup below the threshold (avoids "tenant Tendencies = one
    user's Tendencies" anti-pattern).
  Tenant Tendencies lands in the user message as a `<tenant-tendencies>…
  </tenant-tendencies>` block **before** the per-user
  `<tendencies-context>` block, in distinct XML-tag fences so the LLM
  disambiguates org-level vs user-level context. Opt-in via
  `jaiclaw.agentmind.tendencies.tenant.enabled=false` (default OFF) —
  independent of the pillar toggle and a prerequisite for Phase 5 (see
  companion plan).

---

## §5 — Multi-tenancy and per-user keying

### §5.1 Decision: Option A — reuse `canonicalUserId` via `IdentityResolver`

Three options were considered:

- **A. Reuse `canonicalUserId`.** Keys are `(tenantId, canonicalUserId)`.
  `IdentityLinkService.resolve(channel, channelUserId)` is consulted to get
  the canonical UUID. Hard dep on `jaiclaw-identity`. Zero changes to
  `jaiclaw-core`. Touches one optional file in `jaiclaw-identity` (an additive
  `Optional<String> resolveExisting(...)` so the agentmind resolver can
  distinguish "linked" from "fallback").
- **B. Introduce a `UserContext` SPI in `jaiclaw-core`.** First-class
  per-user concept alongside `TenantContext`, with a `UserContextResolver`
  SPI that every channel implements. Larger surface — touches `jaiclaw-core`,
  every channel adapter, `TenantContextPropagator`. Cleaner long-term.
- **C. Anonymous-by-default per-session keying.** Keys only by `sessionKey`.
  Cross-session continuity for the same user only when `IdentityLinkService`
  is configured.

**Chosen: Option A**, phased toward Option B. Option A ships in Phases 1–3
and earns the right to Option B by demonstrating that the per-user concept is
load-bearing across multiple modules. Option B is deferred to a future
predecessor PR (referenced but not in scope for this plan). Option C is the
graceful-degradation behavior when `jaiclaw-identity` is absent from the
classpath, not the default.

### §5.2 Sidecar map, not a `Session` field change

The new module registers a `SessionStartedEvent` listener that resolves the
user key once and caches it on a `ConcurrentMap<String sessionKey, String
userKey>` *inside the agentmind module*. **`Session.java` is not modified** —
adding `canonicalUserId` to the record would touch every session
builder/serializer/spec across the codebase. Eviction happens on
`SessionEndedEvent`; a sweep timer cleans stuck entries.

### §5.3 Key schemas

User-key derivation, once per session:
```
userKey = IdentityResolver.resolveExisting(channel, peerId)
            .orElseGet(() -> sha256(channel + ":" + peerId).substring(0, 16))
```

The fallback is deterministic — same channel + peerId yields the same userKey
across sessions, so same-channel continuity still works without
`jaiclaw-identity`. Cross-channel does not.

| Concept | JSON file | H2 / Postgres | Redis |
|---|---|---|---|
| **Memory (TENANT)** | `${root}/{tenantId}/TENANT.md` (SINGLE: `${root}/TENANT.md`) | PK `(tenant_id)` | `agentmind:mem:tenant:{tenantId}` |
| **Memory (AGENT)** | `${root}/{tenantId}/agents/{agentId}/MEMORY.md` (SINGLE: `${root}/agents/{agentId}/MEMORY.md`) | PK `(tenant_id, agent_id)` | `agentmind:mem:agent:{tenantId}:{agentId}` |
| **Memory (PEER)** | `${root}/{tenantId}/users/{userKey}/USER.md` | PK `(tenant_id, user_key, agent_id)`; index `(tenant_id, user_key, updated_at DESC)` | `agentmind:mem:peer:{tenantId}:{userKey}:{agentId}` |
| **Soul (TENANT)** | `${root}/{tenantId}/TENANT-SOUL.md` (SINGLE: `${root}/TENANT-SOUL.md`) | PK `(tenant_id)` | `agentmind:soul:tenant:{tenantId}` |
| **Soul (AGENT)** | `${root}/{tenantId}/agents/{agentId}/SOUL.md` | PK `(tenant_id, agent_id)` | `agentmind:soul:{tenantId}:{agentId}` |
| **Tendencies (TENANT)** | `${root}/{tenantId}/TENANT-TENDENCIES.md` (SINGLE: `${root}/TENANT-TENDENCIES.md`) | PK `(tenant_id)` | `agentmind:tend:tenant:{tenantId}` |
| **Tendencies (USER)** | `${root}/{tenantId}/users/{userKey}/TENDENCIES.md` | PK `(tenant_id, user_key)`; index `(tenant_id, last_observed DESC)` | `agentmind:tend:{tenantId}:{userKey}` |

**Schema correction — per-agent Soul path.** The original schema placed
per-agent Soul at `${root}/{tenantId}/SOUL.md`, conflating that root path
with what would have been the tenant Soul. With tenant-scope added,
per-agent Soul moves under `agents/{agentId}/` (mirroring the AGENT Memory
layout). PK and Redis keys are unchanged. **Pre-Phase 1; no data migration
needed** — nothing has shipped yet.

SINGLE-mode collapse: `tenantId` resolves to `TenantProperties.defaultTenantId()`
("default") and `TenantGuard.resolveTenantPrefix()` returns `""` so on-disk
paths skip the tenant subdirectory — matching the `JsonFileTaskStore`
precedent.

Tendencies is the only concept with concurrent-write risk (dialectic pass +
in-flight user chat); Redis CAS uses `WATCH agentmind:tend:{tenantId}:{userKey}`
+ `MULTI`/`EXEC` with the `version` field, identical to `RedisTaskStore`.
Memory entries are append-via-replace, single-writer per `(tenantId,
userKey, scope)`. Soul is single-writer per `(tenantId, agentId)`.

### §5.4 Camel `"anonymous"` peerId gotcha

`extensions/jaiclaw-camel/src/main/java/io/jaiclaw/camel/CamelMessageConverter.java`
defaults `peerId` to the literal string `"anonymous"` when the Camel header is
missing. Under Option A this means all `"anonymous"` Camel messages within a
tenant resolve to the **same** canonical user — surprising but consistent.
The plan's Phase 1 risks log calls this out; the demo's e2e fixture exercises
a non-anonymous identity to keep the happy path clean.

### §5.5 Wiring prerequisite — Session/Message events

`SessionStartedEvent`, `SessionEndedEvent`, and `MessageReceivedEvent` are
declared in `core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/` but
are **not yet fired** from `SessionManager` or any channel adapter in 0.8.0
(they're marked "Aspirational"). The agentmind port depends on all three.

The plan's **Phase 1, task 1.1** is the wiring step — fire the events from
`SessionManager` (session lifecycle) and from each channel adapter
(message receive). This is foundational work that any future module (kanban
audit-on-message, plugin hooks, voice channels) will want, so it earns its
keep regardless of the agentmind outcome.

### §5.6 Graceful degradation without `jaiclaw-identity`

- **Soul**: unaffected (Soul keys on `(tenantId, agentId)`, never on user).
- **Memory (AGENT)**: unaffected.
- **Memory (PEER)**: degrades to per-channel deterministic-hash keying —
  same channel + same peerId still yields continuity within that channel;
  cross-channel does not. A `WARN` log at startup names the degraded features.
- **Tendencies**: same degradation as Memory (PEER).

### §5.7 Migration path to Option B

`AgentMindUserKeyResolver` is the only internal interface that holds the
user-resolution call. When `UserContext` lands, replace the resolver's
implementation with one that delegates to `UserContextHolder.require()`. The
sidecar `sessionKey → userKey` map becomes redundant and can be deleted.
**Zero data migration** — keys on disk / in DB / in Redis are already
`(tenantId, userKey)` shaped.

### §5.8 Multi-tenancy conformance checklist

Per CLAUDE.md's template. Applied to each of Soul, Memory, Tendencies:

| Check | Soul | Memory | Tendencies |
|---|---|---|---|
| Path/PK/key includes `tenantId` | yes (all scopes) | yes (all scopes) | yes (all scopes) |
| Path/PK/key includes `userKey` | n/a — TENANT and AGENT scopes only | yes for PEER; n/a for TENANT/AGENT | yes for USER; n/a for TENANT |
| Async hops wrapped via `TenantContextPropagator` | n/a (Soul writes are synchronous via the tool/REST) | n/a (Memory writes are synchronous via the tool/REST) | yes — dialectic + rollup pipelines, all executor submissions |
| `TenantGuard` injection (not `TenantContextHolder` directly) | yes | yes | yes |
| SINGLE-mode behavior | tenantId="default"; **TENANT-scope records become global — flagged §9 risk 9** | tenantId="default"; **TENANT-scope records become global** | tenantId="default"; **TENANT-scope rollup runs over global per-user records** |
| MCP tools forward `TenantContext` | yes | yes | yes |

A Spock spec `AgentMindStoreIsolationSpec` (mirroring the kanban
`TenantIsolationGuardSpec`) verifies cross-tenant + cross-user reads return
empty. A second spec `AgentMindMemoryThreeScopeIsolationSpec` verifies the same
across all three Memory scopes (TENANT, AGENT, PEER) — Phase 2 gates.

---

## §6 — Opt-in surface and SPI inventory

**Locked-in rule.** Every agentmind capability defaults OFF. No autoconfig fires
without explicit operator consent via a `jaiclaw.agentmind.{soul,memory,tendencies}
.enabled` property. **Every pluggable point ships an SPI + a working default
implementation + an activation property.** The framework ships a runnable
default for every SPI; advanced users swap. This is the same shape kanban
followed for storage providers.

| SPI | Default impl | Activation |
|---|---|---|
| `SoulProvider` | `FileSoulProvider` (JSON-on-disk markdown) | `jaiclaw.agentmind.soul.enabled=true` |
| `AgentMindStoreProvider` (returns sub-stores: `SoulStore`, `MemoryStore`, `TendenciesStore`) | `JsonAgentMindStoreProvider` | one of `jaiclaw.agentmind.{soul,memory,tendencies}.enabled=true` + `jaiclaw.agentmind.storage.type=json\|h2\|postgres\|redis` (default `json`) |
| `AgentMindMemoryProvider` (extends `MemoryProvider`) | `BoundedBlobAgentMindMemoryProvider` | `jaiclaw.agentmind.memory.enabled=true` |
| `AgentMindUserKeyResolver` | `IdentityLinkUserKeyResolver` (delegates to `IdentityResolver`) | always when any agentmind concept is enabled; falls back to deterministic hash if `IdentityResolver` absent |
| `TendenciesLearningProvider` | `LocalLlmTendenciesProvider` (uses configured `ChatModel`) | `jaiclaw.agentmind.tendencies.enabled=true` |
| Alt: `DeterministicTendenciesLearningProvider` | bundled, opt-in | `jaiclaw.agentmind.tendencies.provider=deterministic` |
| Alt: `HonchoRemoteTendenciesProvider` | separate sub-module `extensions/jaiclaw-tendencies-honcho/`, optional Maven dep | `jaiclaw.agentmind.tendencies.provider=honcho` + `…honcho.endpoint=…` |
| `TendenciesCadenceGate` | `TimeAndTurnCadenceGate` (min-interval + min-turns) | always when Tendencies enabled |
| `MemoryOverflowPolicy` | `FailFastOverflowPolicy` (raises `MemoryOverflowException`) | always when Memory enabled |
| `TenantTendenciesRollupProvider` | `MajorityTraitsRollupProvider` (≥50% active-users threshold, deterministic, no LLM) | `jaiclaw.agentmind.tendencies.tenant.enabled=true` |
| Alt: `LlmSummarizingRollupProvider` | bundled, opt-in | `jaiclaw.agentmind.tendencies.tenant.rollup-provider=llm` |
| Alt: `ConsensusRollupProvider` | bundled, opt-in (≥75% threshold) | `jaiclaw.agentmind.tendencies.tenant.rollup-provider=consensus` |
| `TenantTendenciesCadenceGate` | `ScheduledRollupCadenceGate` (cron-driven) | always when tenant Tendencies enabled; cadence configurable via `…rollup-cadence` (default `PT24H`) |

**TENANT scope is dispatched within the existing SPI**, not via parallel
SPIs. `SoulProvider` / `AgentMindMemoryProvider` / `TendenciesStore`
implementations MUST handle all enum values of their respective scope
discriminator (`SoulScope`, `MemoryScope`, `TendenciesScope`) or
explicitly document the unsupported set. This avoids SPI explosion (no
`TenantSoulProvider` etc.) and keeps the per-concept code path single.

Default implementations are bundled with the module that owns the SPI.
Alternative implementations either ship in the same module behind a property
switch (e.g., `DeterministicTendenciesLearningProvider`,
`LlmSummarizingRollupProvider`) or in a sub-module behind
`@ConditionalOnClass` + property (e.g., `HonchoRemoteTendenciesProvider`).

A Spock spec asserts **zero beans created** for each concept when its toggle
is `false`. **Tenant-scope opt-ins are independent of pillar opt-ins** —
operators must flip both `jaiclaw.agentmind.{soul,memory,tendencies}.enabled`
*and* `jaiclaw.agentmind.{soul,memory,tendencies}.tenant.enabled` to activate
tenant-scope behavior. The `*AutoConfigDisabledSpec`s verify both gates.

---

## §7 — Module layout

| Module | Concept | Activation |
|---|---|---|
| `extensions/jaiclaw-agentmind-soul/` | Soul concept + `SoulProvider` SPI default + `soul` agent tool + `BeforePromptBuildEvent` subscriber + MCP provider + REST debug controller + autoconfig | `jaiclaw.agentmind.soul.enabled=true` |
| `core/jaiclaw-memory/` (existing module, additions only) | `AgentMindMemoryProvider` + `MemoryDocument` record + `memory` agent tool + `BoundedBlobMemoryStore` + `MemoryOverflowPolicy` SPI | `jaiclaw.agentmind.memory.enabled=true` |
| `extensions/jaiclaw-agentmind-tendencies/` | Tendencies concept + `TendenciesStoreProvider` SPI (JSON/JDBC/Redis) + striped dialectic executor + `LocalLlmTendenciesProvider` + `DeterministicTendenciesProvider` + `MessageReceivedEvent` injector + `SessionEndedEvent` cadence trigger + REST + MCP + Actuator | `jaiclaw.agentmind.tendencies.enabled=true` |
| `extensions/jaiclaw-tendencies-honcho/` | Optional remote Honcho-compatible provider | `@ConditionalOnClass(HonchoClient.class)` + `jaiclaw.agentmind.tendencies.provider=honcho` + endpoint |
| `jaiclaw-starter-agentmind-soul/` | Starter pulling soul module | — |
| `jaiclaw-starter-agentmind-tendencies/` | Starter pulling tendencies module | — |
| `jaiclaw-starter-agentmind/` | Umbrella starter pulling all three | — |
| `jaiclaw-examples/agentmind-demo/` | Runnable demo exercising all three pillars with `DeterministicTendenciesLearningProvider` (no LLM key required) | — |
| `.claude/skills/agentmind-e2e/` | Out-of-process e2e skill mirroring `kanban-e2e` | — |

**Memory ships inside `core/jaiclaw-memory/`, not a parallel module.** AgentMind'
`MEMORY.md`/`USER.md` blobs *are* JaiClaw memory done a particular way;
layering another module on top would duplicate surface and force the user to
choose which "memory" they mean.

**No new modules for tenant scope.** All tenant-scope variants — tenant Soul,
tenant Memory, tenant Tendencies, the rollup pipeline — live inside the same
modules that own their per-agent/per-user counterparts. The
`TenantTendenciesRollupProvider` SPI, `ScheduledRollupCadenceGate`, and
alternative rollup providers all ship inside
`extensions/jaiclaw-agentmind-tendencies/`. This keeps `mvn dependency:tree`
readable and the lineage from agentmind obvious.

---

## §8 — Per-pillar deep-dives

### §8.1 AgentMind-isms — preserve vs adapt

| AgentMind choice | Verdict | Rationale |
|---|---|---|
| Markdown-as-prompt for Soul/Memory | **Preserve** | Normalizing loses the design; the prompt is the consumer, not Java code |
| Markdown + structured map for Tendencies | **Adapt** | JaiClaw has dashboards/REST clients that hermes doesn't; structured map costs one extra structured-output call per pass |
| No `read` API on memory | **Preserve** as agent tool; add REST read for ops only | Cache stability is the whole point |
| User-message injection for Tendencies | **Preserve** | System-prompt cache stability |
| Memory overflow as error | **Preserve** | `MemoryOverflowException` surfaced as tool-error result; forces in-turn compaction |
| Single-worker executor for writes | **Preserve** as striped per `(tenantId, userKey)` | Turn order is a correctness property |
| 14 persona overlays as files | **Defer** to Phase 4 as opt-in `jaiclaw.agentmind.soul.personas.dir`; shipping the 14 verbatim is itself an open question | Personas are a layer on top of Soul, not core |
| Honcho remote billing per-pass | **Adapt** | Local providers default; remote opt-in module; emit `jaiclaw.tendencies.passes.cost.estimated` Micrometer gauge from Phase 1 |
| Free-form section markers (`# Identity` etc.) | **Preserve** | The `soul` tool addresses sections by markdown heading |
| Single-tenant single-user assumption | **Extend** | AgentMind is desktop-personal; JaiClaw is multi-tenant. TENANT-scope variants of each concept are a JaiClaw extension justified by the multi-tenancy boundary |

### §8.2 The dialectic pipeline

- **Trigger.** Default: `SessionEndedEvent` (with `AgentEndedEvent` as a
  secondary signal for long-running agentic tasks). Both drain into the
  `TendenciesCadenceGate` keyed by `(tenantId, canonicalUserId)`. A pass only
  fires if `min-interval` AND `min-turns` thresholds are both met.
- **Inference budget.** Striped single-thread executor over virtual threads:
  `ConcurrentHashMap<UserKey, ExecutorService>`. Bounded queue per stripe
  (default 4), drop-oldest on overflow. Metered with Micrometer
  (`jaiclaw.tendencies.queue.depth`, `jaiclaw.tendencies.queue.drops`).
  **Never** plain `@Async` — Spring's default executor reorders writes
  against the same user.
- **Cost guard.** Per-tenant daily token cap configurable via
  `jaiclaw.agentmind.tendencies.cost.daily-token-cap` (default 100k). When the
  cap is hit, the cadence gate trips circuit-breaker open for the rest of the
  day. Actuator endpoint `/actuator/agentmind/tendencies` reports passes-per-day,
  estimated tokens, gate hits/misses, circuit state.
- **Provider chain.** `LocalLlmTendenciesProvider` (single-pass against
  configured `ChatModel`) is the default; `DeterministicTendenciesProvider`
  (rule/regex extraction; zero LLM cost) is a property switch;
  `HonchoRemoteTendenciesProvider` lives in the optional sub-module.

#### §8.2.1 Tenant rollup pipeline

The per-user dialectic pipeline above produces `Tendencies(scope=USER, …)`
records. A second pipeline produces the `Tendencies(scope=TENANT, …)`
record by aggregating across them.

- **Trigger.** Scheduled, **not** event-driven. `ScheduledRollupCadenceGate`
  fires on a cron expression (default `PT24H`). Rollup runs once per
  `(tenantId)` per cadence window, regardless of how many user-level
  Tendencies updates happened in that window. Rationale: tenant Tendencies
  is a daily-resolution org-level signal, not a per-message response —
  scheduling decouples cost from chat volume.
- **Sampling.** `jaiclaw.agentmind.tendencies.tenant.rollup-min-active-users=3`
  is the floor; below it the rollup is skipped (logged as INFO).
  `jaiclaw.agentmind.tendencies.tenant.active-window=PT168H` (default 7d)
  bounds the set of per-user records the rollup reads — only users with
  `updatedAt > now - active-window` count.
- **Provider.** `TenantTendenciesRollupProvider` SPI; default
  `MajorityTraitsRollupProvider` (deterministic, no LLM). Alternative
  `LlmSummarizingRollupProvider` (one LLM call per rollup, opt-in) and
  `ConsensusRollupProvider` (75% threshold, opt-in).
- **Executor.** A single global virtual-thread executor for tenant rollups
  — no striping (one job per tenant per cadence, no risk of same-tenant
  concurrent submissions). Wrapped via `TenantContextPropagator` per repo
  rule.
- **Persistence.** Same `TendenciesStore` SPI as per-user records;
  dispatches on scope for key derivation per §5.3.
- **Output cost gauge.** Reuses the existing
  `jaiclaw.agentmind.tendencies.passes.cost.estimated` Micrometer gauge with
  a `scope=tenant` tag for observability.

#### §8.2.2 Prompt composition order

The full assembled prompt across all three concepts and three scope
layers:

```
System prompt:
  [identity line: "You are {name}, {description}."]
  [TENANT Soul markdown]           — omitted if absent
  [AGENT Soul markdown]            — omitted if absent
  [TENANT Memory block]            — omitted if absent
  [AGENT Memory block]             — omitted if absent
  [PEER Memory block]              — omitted if absent
  [skills full content]
  [additionalInstructions]

User message:
  [<tenant-tendencies>…</tenant-tendencies>]    — omitted if absent
  [<tendencies-context>…</tendencies-context>]  — omitted if absent
  [original user content]
```

Three invariants:

1. **Most-general → most-specific.** Tenant before agent before peer/user.
   The LLM weights later instructions higher so the most-specific layer
   implicitly refines without structurally suppressing the more-general
   layers.
2. **Empty sections are omitted entirely** — no placeholder headers, no
   zero-length fences. A missing record produces zero output. Preserves
   prefix-cache stability when operators introduce overlays
   mid-deployment.
3. **Distinct fences for tenant vs user Tendencies.** `<tenant-tendencies>`
   and `<tendencies-context>` are different XML-tag fences so the LLM
   disambiguates "this is about the org" vs "this is about this user."

A Phase 1 spec `AgentMindPromptCompositionOrderSpec` asserts the ordering;
`AgentMindScopeFallThroughSpec` covers the three populated-set cases
(tenant-only / agent-only / both).

### §8.3 Render caching

A rendered `<tendencies-context>` block is cached per `(tenantId, userKey)`
inside the new module and invalidated when the underlying `Tendencies`
record's `updatedAt` advances. The cache lives on a `Caffeine` cache (LRU,
size-bounded) so a multi-tenant deployment with many users doesn't accumulate
unbounded render output.

The rendered `<tenant-tendencies>` block (tenant scope) lives on a
**separate** Caffeine cache keyed only by `tenantId` — no userKey dimension.
Separation lets the tenant cache stay small and long-lived (one entry per
tenant, daily-rollup invalidation cadence) while the per-user cache absorbs
the per-message-volume churn. A tenant Soul or tenant Memory edit also
invalidates this cache (any tenant-scope content change is a prefix-cache
invalidation event — see §9 risk 11).

---

## §9 — Risks (decision-log seed)

1. **Dialectic LLM token blowup.** Tendencies' learning can silently triple a
   tenant's token spend. Mitigation from Phase 1: per-tenant daily cap +
   circuit breaker + Actuator cost gauge.
2. **Cache invalidation on Soul/Memory mid-session edits.** A mid-session
   write to SOUL.md or MEMORY.md invalidates the prefix cache for the rest of
   the session. Mitigation: document the trade-off; emit
   `jaiclaw.agentmind.cache.invalidations` counter; recommend session-end
   consolidation as the canonical write path.
3. **`MessageReceivedEvent` not wired from channel adapters in 0.8.0.**
   Tendencies' user-message injection is blocked on this dispatch landing.
   Mitigation: Phase 1's first task group is the wiring step itself; it's a
   precondition, not a side quest.
4. **`UserContext` SPI churn deferred.** Option B is the right long-term
   shape but isn't shipping in this plan. Mitigation: the wrapping
   `AgentMindUserKeyResolver` insulates call-sites; swapping in a Spring
   `UserContextHolder` is a one-class change with zero data migration.
5. **Provider × backend × concept matrix.** Three concepts × N backends × M
   learning providers can drift. Mitigation: shared Spock contract specs
   (`AgentMindStoreContractSpec`, `TendenciesStoreContractSpec`,
   `TendenciesLearningProviderContractSpec`) modeled on
   `TaskStoreContractSpec`.
6. **Soul section-merge correctness.** Parallel `soul replace` calls from two
   agents can race. Mitigation: advisory file lock + `version` field;
   stale-version writes are rejected with a typed error.
7. **Institutional poisoning (tenant Memory).** A bad write to tenant
   Memory contaminates every agent and user in the tenant simultaneously.
   Mitigation: agent-write OFF by default; operator role guard on REST +
   MCP write paths; advisory file lock + `version` field reject stale
   writes; structured audit log on every tenant-scope write; tagged
   Micrometer counter `jaiclaw.agentmind.memory.tenant.writes` with `actor`
   and `agentId` tags for observability.
8. **Tenant Tendencies rollup cost.** Aggregating across all users is
   O(N) per cadence. At 10k users per tenant, even a deterministic
   majority-rules pass touches 10k records daily. Mitigation: scheduled
   (not event-driven) trigger; `rollup-min-active-users` floor avoids
   no-op runs; bounded `active-window` limits the input set; per-tenant
   opt-out via `tendencies.tenant.enabled=false`. The LLM-summarizing
   alternative provider is opt-in, never default.
9. **Scope leakage in SINGLE-mode.** SINGLE-mode collapses `tenantId` to
   `"default"`, so tenant-scope records become globally shared. A
   SINGLE-mode deployment that later migrates to MULTI inherits global
   tenant-scope content into the "default" tenant. Mitigation: explicit
   startup `WARN` log when SINGLE-mode + any tenant-scope toggle are
   enabled together; OPERATIONS.md caveat in the SINGLE-mode section;
   MULTI-conversion migration runbook in the dev guide noting
   tenant-scope records must be reviewed before tenant separation.
10. **Cross-agent voice conflict.** Tenant Soul says "concise"; per-agent
    Soul says "verbose". Additive layering means both render; the LLM
    weights the later (agent) higher, so agent effectively wins for that
    agent, but the tenant intent stays in-prompt. Mitigation: log
    conflicts at write-time when an agent Soul section heading collides
    with a tenant Soul section heading (advisory only, not rejection);
    `jaiclaw.agentmind.soul.conflicts` counter; document the layering
    semantics in OPERATIONS.md so operators set expectations.
11. **Prefix-cache invalidation amplified for tenant edits.** A single
    tenant Soul or tenant Memory edit invalidates the system-prompt
    prefix cache for **every session in every agent in the tenant**
    simultaneously. Mitigation: tag the
    `jaiclaw.agentmind.cache.invalidations` counter with a `scope` label so
    blast radius is observable; recommend tenant-scope edits during
    low-traffic windows; document the trade-off explicitly alongside
    risk 2 (mid-session edit cost).

---

## §10 — Open questions

Each marked **Open** or **Resolved: <decision> (date)**. The companion plan
file's `§11` mirrors this list and resolves them inline as phases land.

- **Memory char-budget defaults.** Match agentmind (2,200 / 1,375) or rethink
  for Java context windows? **Open** — resolve in Phase 2.
- **Tendencies deterministic vocabulary.** Should
  `DeterministicTendenciesProvider` ship a default trait vocabulary (e.g.,
  `prefers_concise`, `tech_leaning`) or be schema-free? **Open** — resolve in
  Phase 3.
- **Persona overlays.** Ship hermes' 14 personas verbatim, or just the SPI?
  **Open** — resolve in Phase 4.
- **Cross-agent Soul sharing.** One tenant, multiple agents: does each agent
  get its own Soul, or does the tenant share one? Current schema is
  per-agent; per-tenant could be a `jaiclaw.agentmind.soul.scope` property.
  **Resolved 2026-06-13: both, via `SoulScope { TENANT, AGENT }` with
  additive layering.** Tenant scope is operator-authored and lands first
  in the prompt; per-agent scope refines it (see §4.1 and §8.2.2).
- **Tenant Tendencies rollup vocabulary.** Should `MajorityTraitsRollupProvider`
  ship a default trait taxonomy (e.g., `working_hours`, `comm_style`,
  `tech_stack`) that constrains what shows up in the tenant trait map, or
  should the rollup be schema-free and produce whatever keys the per-user
  records carry? **Open** — resolve in Phase 5.
- **Tenant Soul role-guard defaults across deployment styles.** Is
  `ADMIN,OPERATOR` the right default everywhere, or should single-tenant
  deployments default to allowing the gateway-owner role? **Resolved
  2026-06-14:** ship `[ADMIN, OPERATOR]` as the universal default;
  single-tenant deployments override via
  `jaiclaw.agentmind.soul.tenant.write.roles`. The check is performed
  against the standard servlet `HttpServletRequest.isUserInRole` so any
  auth integration lights up the guard automatically.
- **Tenant Memory char-budget default.** 4,096 chars (per the plan's
  proposal) is double per-user Memory. Is that the right ratio? Should the
  default scale by deployment size, or stay fixed? **Open** — resolve in
  Phase 2.

---

## Appendix — Cross-references

- Companion execution plan: [`AGENTMIND-MEMORY-SOUL-PLAN.md`](./AGENTMIND-MEMORY-SOUL-PLAN.md)
- Kanban precedent (paired-doc shape): [`KANBAN-TASK-PROCESSING-ANALYSIS.md`](./KANBAN-TASK-PROCESSING-ANALYSIS.md) + [`KANBAN-IMPLEMENTATION-PLAN.md`](./KANBAN-IMPLEMENTATION-PLAN.md)
- AgentMind-agent reference: [`github.com/NousResearch/hermes-agent`](https://github.com/NousResearch/hermes-agent)
- Multi-tenancy architecture: [`multi-tenancy-architecture.md`](./multi-tenancy-architecture.md)
