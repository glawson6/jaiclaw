# Road to JaiClaw 1.0

> **⚠️ Superseded.** This document captures the 0.8.0 → 0.9.0 framing of
> the API stability program. It is kept for historical context (the
> `@Stable` / `@Experimental` / `@Internal` regime defined here is still
> the contract).
>
> For current release plans, see:
> - [dev/RELEASE-PLAN-0.9.2.md](./dev/RELEASE-PLAN-0.9.2.md) — the next release (secrets baseline).
> - [dev/RELEASE-PLAN-1.0.0.md](./dev/RELEASE-PLAN-1.0.0.md) — the umbrella plan through 1.0 GA.

> **Status at 0.8.0:** The framework is production-deployable today. The
> API stability program ([P3.5](CODEBASE-ANALYSIS-2026-06-10.md)) ships
> in 0.8.0 with `@Stable` / `@Experimental` / `@Internal` markers on
> public types. This document is the contract between now and 1.0:
> what's stable, what may still change, and what gates the 1.0 cut.

The version sequence is **0.8.0 → 0.9.0 → 1.0.0**, with `0.8.x` patch
releases in between. 0.9.0 is the "API freeze" milestone where
`@Experimental` markers are either promoted to `@Stable` or removed.
1.0 is the "stability commitment" milestone where breaking changes to
`@Stable` types are no longer permitted between minor releases.

---

## What's stable today (0.8.0)

The following types and methods carry `@Stable` and will not break
incompatibly in 0.8.x or 0.9.x; the commitment becomes permanent at 1.0.

### Core SPIs an adopter typically depends on

| Type | Package | What it is |
|---|---|---|
| `ToolCallback`              | `io.jaiclaw.core.tool`        | The single tool-calling SPI; bridged to Spring AI + Embabel + MCP |
| `ToolDefinition`            | `io.jaiclaw.core.tool`        | Metadata for a tool, used by every tool registration path |
| `ChannelAdapter`            | `io.jaiclaw.channel`          | The per-channel SPI for inbound + outbound messaging |
| `ChannelMessage`            | `io.jaiclaw.channel`          | The canonical channel-agnostic message record |
| `JaiClawPlugin`             | `io.jaiclaw.plugin`           | The plugin SPI (tools + hooks + state) |
| `PluginApi`                 | `io.jaiclaw.plugin`           | Plugin-side handle for registering tools/hooks |
| `HookEvent` (sealed)        | `io.jaiclaw.core.hook.event`  | Typed hook event hierarchy (0.8.0 P3.1) |
| `AgentRuntime`              | `io.jaiclaw.agent`            | Entry point for invoking agents |
| `SessionManager`            | `io.jaiclaw.agent.session`    | Per-session state lookup |
| `TenantContext`             | `io.jaiclaw.core.tenant`      | Current-tenant resolution |
| `AuditLogger`               | `io.jaiclaw.audit`            | Audit-trail SPI |
| `TranscriptStore`           | `io.jaiclaw.audit`            | Session-transcript SPI |
| `McpToolProvider`           | `io.jaiclaw.mcp`              | MCP server tool surface |
| `McpResourceProvider`       | `io.jaiclaw.mcp`              | MCP server resource surface |
| `SkillLoader`               | `io.jaiclaw.skills`           | Skill discovery + filtering |

### Core configuration records

`JaiClawProperties` and its immediate `@ConfigurationProperties` records
(agent, tools, skills, channels, session, memory, tenant, models, voice,
video) are stable in shape; new optional fields may be added in minors.

### CLI + start.sh surface

`bin/jaiclaw`, `start.sh`, `quickstart.sh`, and `setup.sh` invocation
syntax is stable. Subcommands may gain new flags; existing flags retain
their meaning.

---

## What's still `@Experimental`

The following surfaces may still evolve between 0.8 and 0.9. Adopters
may use them; migration steps will be documented per-release.

| Area | Why it's experimental |
|---|---|
| `@ToolParameter` annotation binding | Validated the developer ergonomics in 0.8.0 P3.2; will likely promote in 0.9.0 with minor naming polish |
| `TypedToolCallback` + `SchemaBuilder` | Same — companion to `@ToolParameter` |
| `jaiclaw-pipeline` DSL          | Three sources (inline YAML / per-file YAML / Java) shipped 0.7.x; templating + actuator surface stabilizing |
| `jaiclaw-camel` integration    | Camel route ↔ JaiClaw bridge; usage signal still small |
| `jaiclaw-embabel-delegate`      | The shape of the delegate API is settled; renaming considered |
| `JaiClawObservations` metric naming | We may rename one or two metrics to align with Spring AI's observation taxonomy as it firms up |
| `jaiclaw-canvas` A2UI artifact rendering | API surface usable but evolving with the broader A2UI standard |

The full list lives in source. Find experimental types with:

```bash
grep -rln "@Experimental" core extensions channels tools
```

---

## Gates between 0.8 and 1.0

### 0.8.x patch releases (continuous)

- Bug fixes (no `@Stable` API changes)
- Security backports
- New optional `@ConfigurationProperties` fields
- Documentation
- New `@Experimental` types

### 0.9.0 — "API freeze"

This is the milestone where most `@Experimental` types either get
promoted to `@Stable` or removed entirely. The gates:

1. **`@Experimental → @Stable` review.** Every type carrying
   `@Experimental` today gets a yes/no decision; promoted types lock
   their signatures for 1.0.
2. **Public surface audit.** Run the API stability check (see
   `dev/api-stability.md`) and confirm every `public` type in non-
   `internal` packages carries a stability marker.
3. **Migration guide for 0.8 → 0.9.** Any renames, package moves, or
   parameter changes get a concrete pre-1.0 deprecation step + a
   forwarding shim where reasonable.
4. **JaCoCo coverage gate ≥ 50%** on every core module
   (`jaiclaw-core`, `jaiclaw-config`, `jaiclaw-tools`, `jaiclaw-agent`,
   `jaiclaw-skills`, `jaiclaw-plugin-sdk`, `jaiclaw-memory`,
   `jaiclaw-security`, `jaiclaw-gateway`, `jaiclaw-channel-api`). The
   audit-flagged five are at ≥ 40% in 0.8.0; the rest at 0.9.0.
5. **Documentation parity.** Every `@Stable` public type has a
   non-trivial javadoc; every CLI command has a `--help` block.

### 1.0.0 — "Stability commitment"

The final cut. The gates:

1. **`@Stable` API surface is frozen.** From 1.0 on, breaking changes to
   `@Stable` types require a major version bump (2.0.0). Adding new
   methods to interfaces is permitted only when those methods carry
   default implementations.
2. **Two consecutive 0.9.x releases pass without breaking changes.**
   Empirical evidence that the API freeze held.
3. **Production reference deployments confirmed.** At least two
   non-Embabel-internal production deployments running 0.9.x for ≥ 30
   days, sharing operational feedback.
4. **Maven Central published continuously.** The release tooling in
   `maven-central-deploy/` is exercised against every release; the
   automation in `.github/workflows/` handles the 0.9.x → 1.0 cut.
5. **A migration guide from 0.x → 1.0 exists** consolidating breaks
   from 0.8 → 0.9 → 1.0.

---

## What 1.0 does **not** promise

- **No promise on `@Experimental` types.** They evolve at their own
  pace; check release notes.
- **No promise on `@Internal` types.** Adopters who import from
  `io.jaiclaw.*.internal` or `*.impl.*` packages do so at their own
  risk; those types change without notice.
- **No promise on configuration property values being stable.** New
  `@ConfigurationProperties` fields may be added; defaults may shift.
  Breaking changes to property *names* go through deprecation.
- **No promise on tooling/CLI implementation details.** The
  invocation syntax is stable; the underlying JKube profiles, scripts,
  and helper utilities are not.

---

## Versioning at a glance

| Tier         | Breaks `@Stable`? | Breaks `@Experimental`? | When |
|--------------|-------------------|--------------------------|------|
| 0.8.x patch  | No                | No                       | Now (bug fixes only) |
| 0.9.0 minor  | Yes (one-shot)    | Yes                      | Once `@Experimental` review is done |
| 0.9.x patch  | No                | No                       | Bug fixes after 0.9.0 |
| 1.0.0        | No (one-shot)     | Yes                      | Once 0.9.x is empirically stable |
| 1.x minor    | **No**            | Yes                      | After 1.0 |
| 1.x patch    | No                | No                       | After 1.0 |
| 2.0 major    | Yes               | Yes                      | When justified |

This is essentially SemVer with "@Stable / @Experimental" as the
contract qualifier.

---

## How to track stability

- **In your IDE:** the `@Stable`, `@Experimental`, `@Internal`
  annotations are `@Documented` and propagate to generated javadoc.
- **In code review:** any PR that touches a `@Stable` type and changes
  a method signature should fail the "API stability" check.
- **In releases:** the release notes in `releases/release-X.Y.Z.md`
  list every `@Stable` change.

To find every stability marker in source:

```bash
grep -rln "@Stable\|@Experimental\|@Internal" core extensions channels tools apps
```

---

## See also

- [POSITIONING.md](./POSITIONING.md) — what JaiClaw is and isn't
- [CODEBASE-ANALYSIS-2026-06-10.md](./CODEBASE-ANALYSIS-2026-06-10.md) §3.5
  — the API stability program
- [MIGRATION-0.8.md](./MIGRATION-0.8.md) — 0.7 → 0.8 upgrade
- [user/PRODUCTION-DEPLOYMENT.md](user/PRODUCTION-DEPLOYMENT.md) —
  putting 0.8 into production
