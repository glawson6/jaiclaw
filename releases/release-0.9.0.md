# JaiClaw 0.9.0 Release Notes

**Release Date**: TBD (Phase 4 wrap-up in flight)

> 0.9.0 is the **"AgentMind"** release — a port of
> [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent)'s
> Soul / Memory / Tendencies model into the JaiClaw runtime. All four new
> extensions are **off by default**; no existing application changes
> behaviour after the upgrade unless an operator opts in via
> `application.yml`.

---

## Highlights

- **AgentMind family** — three independent pillars + an opt-in remote
  provider sub-module, all gated behind their own
  `jaiclaw.agentmind.{soul,memory,tendencies}.enabled=true` toggles.

- **Persona overlays** — `jaiclaw-agentmind-soul` ships 5 curated
  persona markdown files (`concise`, `technical`, `mentor`, `socratic`,
  `pirate`) plus a `personality` agent tool that swaps the active
  overlay per session.

- **First-class HookEvent permits** — Soul/Memory/Tendencies mutations
  are typed events on the sealed `HookEvent` hierarchy, alongside
  kanban's `TaskStateChangedEvent`. Permits land in 0.9.0 so plugin
  authors can register handlers; emission at the SPI write boundary
  ships in a follow-up release.

- **Runnable end-to-end demo** — `jaiclaw-examples/agentmind-demo`
  wires all four surfaces against a localhost Anthropic chat endpoint.
  Boot-context smoke spec asserts the wiring; in-module prompt
  composition spec byte-compares the assembled system prompt against
  checked-in goldens.

---

## New Modules

| Module | Purpose | Opt-in flag |
|---|---|---|
| `jaiclaw-agentmind-soul` | Per-agent + per-tenant Soul markdown overlay; persona overlays; `SoulPromptInjector` hook. | `jaiclaw.agentmind.soul.enabled=true` |
| `jaiclaw-agentmind-memory` | Per-user/agent/tenant blob Memory with char-budgeted compaction. | `jaiclaw.agentmind.memory.enabled=true` |
| `jaiclaw-agentmind-tendencies` | Per-user observed style; `DeterministicTendenciesProvider` default learner. | `jaiclaw.agentmind.tendencies.enabled=true` |
| `jaiclaw-tendencies-honcho` | Remote `TendenciesLearningProvider` against a Honcho-compatible API. | `jaiclaw.agentmind.tendencies.provider=honcho` + `HonchoClient` bean |
| `jaiclaw-example-agentmind-demo` | Runnable end-to-end demo. | n/a — example app |

Three starters added under `jaiclaw-starters/`:

- `jaiclaw-starter-agentmind-soul`
- `jaiclaw-starter-agentmind-memory`
- `jaiclaw-starter-agentmind-tendencies`

Module count: `extensions/` grows from 35 → 39; `jaiclaw-starters/`
from 28 → 31; `jaiclaw-examples/` from 40 → 41.

---

## New `HookEvent` Permits

Three permits added to the sealed `HookEvent` hierarchy (Plan task 4.7):

- `SoulUpdatedEvent(scope, tenantId, agentId, version, actor)`
- `MemoryUpdatedEvent(scope, tenantId, agentId, canonicalUserId, version, actor)`
- `TendenciesUpdatedEvent(tenantId, canonicalUserId, agentId, sessionKey, version, provider)`

Plugins register against the types via
`api.on(SoulUpdatedEvent.class, handler)` etc. Emission at the SPI
write boundary (InstrumentedSoulProvider decorator,
BoundedBlobMemoryStore, TendenciesLearningProvider) is a follow-up
shipped with the AgentMind dashboard work.

`HookEventTypesSpec` locks the permit count at 20 (was 17).

---

## Breaking Changes

**None expected.** All four new extensions are additive and gated by
opt-in properties. The `HookEvent` permit additions are
backwards-compatible — existing plugins that pattern-match on the prior
17 subtypes continue to work; only exhaustive `switch` statements over
the sealed hierarchy need updating, and the codebase audit found none.

---

## Dependency Updates

No baseline framework updates in 0.9.0 — Spring Boot stays at 3.5.14,
Spring AI at 1.1.7, Embabel at 0.3.5, Camel at 4.18.2. AgentMind
extensions reuse the existing dependency stack.

The `jaiclaw-tendencies-honcho` sub-module intentionally does NOT
depend on a particular HTTP client; consumers wire their preferred
WebClient / OkHttp implementation against the minimal `HonchoClient`
SPI.

---

## Configuration Surface (Summary)

```yaml
jaiclaw:
  agentmind:
    soul:
      enabled: false
      personas:
        enabled: false
        dir: ${HOME}/.jaiclaw/agentmind/personas
      tenant:
        enabled: false
        write-roles: [ADMIN, OPERATOR]
    memory:
      enabled: false
      tenant:
        enabled: false
        agent-write-enabled: false
    tendencies:
      enabled: false
      provider: deterministic   # deterministic | local-llm | honcho
```

See [`docs/user/OPERATIONS.md` § AgentMind Configuration](../docs/user/OPERATIONS.md#agentmind-configuration)
for the full reference.

---

## Bug Fixes

- None observable to users. Internal refinements:
  - `AgentMindSoulProperties` compact constructor defaults `personas`
    to `Personas(false, null)` so existing 4-arg callers from prior
    snapshots are not broken at runtime — but the canonical constructor
    is now 5-arg.

---

## Security Fixes

- None in scope for 0.9.0. Tenant-scope writes for Soul and Memory are
  role-guarded by `jaiclaw.agentmind.{soul,memory}.tenant.write-roles`
  (default `[ADMIN, OPERATOR]`); the checks delegate to the standard
  servlet `HttpServletRequest.isUserInRole` so any auth integration
  automatically engages the guard.

---

## Demo + Skills

- `jaiclaw-examples/agentmind-demo` — runnable Spring Boot app
  demonstrating all four surfaces end-to-end.
- `.claude/skills/agentmind-e2e/` — operator skill that drives the
  demo out-of-process (local-only; `.claude/` is gitignored).
- `jaiclaw-examples/agentmind-demo/src/test/resources/goldens/` —
  checked-in golden files for the prompt composition byte-compare
  spec.

---

## Migration Notes

No required migration. Optional uptake:

1. Add the three starters you want:
   ```xml
   <dependency>
       <groupId>io.jaiclaw</groupId>
       <artifactId>jaiclaw-starter-agentmind-soul</artifactId>
   </dependency>
   ```
2. Flip the pillar-level enable flag in `application.yml`.
3. (Optional) Author per-agent or per-tenant Soul markdown via the
   agent tool or the operator REST endpoint.

See `jaiclaw-examples/agentmind-demo/README.md` for a walkthrough.

---

## Acknowledgements

The AgentMind extensions are a Java/Spring port of the conceptual
model in [`NousResearch/hermes-agent`](https://github.com/NousResearch/hermes-agent).
Implementation specifics — multi-tenancy, prompt-cache layering,
HookEvent integration, and opt-in module gating — are JaiClaw's own.
