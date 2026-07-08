# JaiClaw 1.0.0 Release Plan

> **Status**: 0.9.3 compliance substrate cut in progress (includes API cleanup + compliance). 30-day pilot window opens once 0.9.3 lands on Central.
> **Sequence**: 0.9.2 (secrets) → 0.9.3 (API cleanup + compliance substrate) → (30-day pilot window) → 1.0.0 cut.
> **Companion plans**: [RELEASE-PLAN-0.9.2.md](./RELEASE-PLAN-0.9.2.md), [COMPLIANCE-IMPLEMENTATION-PLAN.md](./COMPLIANCE-IMPLEMENTATION-PLAN.md).
> **Supersedes**: [../ROAD-TO-1.0.md](../ROAD-TO-1.0.md) (kept as historical context for the 0.8 → 0.9 transition).

## TL;DR

1.0 is a **stability commitment**, not a feature release. The `@Stable` annotation regime introduced in 0.8.0 (see the historical `ROAD-TO-1.0.md`) makes the commitment precise: from 1.0 onward, breaking changes to `@Stable` types require a major version bump (2.0). `@Experimental` and `@Internal` types continue to evolve under their own contract.

To get to 1.0 from today (0.9.3-SNAPSHOT, release in flight):

1. ✅ **0.9.2** — secrets baseline (see the companion doc). Shipped.
2. **0.9.3** — API surface polish + example cleanup **plus** the compliance substrate (GDPR + HIPAA — Tier 1 + Tier 2 + Tier 3 from [COMPLIANCE-IMPLEMENTATION-PLAN.md](./COMPLIANCE-IMPLEMENTATION-PLAN.md)). Bundled into one release because both were in-flight when the cut was scheduled. Positions JaiClaw as a "compliance-capable" framework for enterprise adopters and closes the article-to-capability gap identified in the internal audit. **Cut in progress.**
3. **30-day pilot window** with two non-internal production deployments. Empirical evidence that the API freeze is real. Opens once 0.9.3 lands on Central.
4. **1.0.0 cut** — review `@Experimental` surfaces, promote a few to `@Stable`, write the consolidated migration guide, tag.

**Hard ceiling**: Spring Boot 3.5.x. Embabel — JaiClaw's binding Tier 1 dependency — has not released a Spring Boot 4-compatible version. Their `main` branch (`0.4.0-SNAPSHOT`) still targets Spring Boot 3.5.x. **Spring Boot 4 / Spring Framework 7 / Apache Camel 4.19+ are post-1.0 work**, slated for JaiClaw 2.0 when Embabel ships a 4.x GA. This is intentional, not an oversight.

---

## What 1.0 promises (inherited from ROAD-TO-1.0.md)

- **`@Stable` API surface is frozen.** From 1.0, breaking changes to `@Stable` types require a major version bump. Adding new methods to interfaces is permitted only when those methods carry default implementations.
- **The core SPIs an adopter typically depends on are stable**: `ToolCallback`, `ToolDefinition`, `ChannelAdapter`, `ChannelMessage`, `JaiClawPlugin`, `PluginApi`, the sealed `HookEvent` hierarchy, `AgentRuntime`, `SessionManager`, `TenantContext`, `AuditLogger`, `TranscriptStore`, `McpToolProvider`, `McpResourceProvider`, `SkillLoader`.
- **Core configuration records** (`JaiClawProperties` and its immediate `@ConfigurationProperties` records) are stable in shape; new optional fields may be added in minors.
- **CLI + start.sh surface** is stable. Subcommands may gain new flags; existing flags retain their meaning.

## What 1.0 does NOT promise

- **No promise on `@Experimental` types** — they evolve at their own pace per release notes.
- **No promise on `@Internal` types** — `io.jaiclaw.*.internal` and `*.impl.*` packages change without notice.
- **No promise on configuration property values being stable** — new fields may be added; defaults may shift. Breaking changes to property *names* go through deprecation.
- **No promise on tooling/CLI implementation details** — invocation syntax is stable; the underlying JKube profiles, helper scripts, and internals are not.

---

## Where we are vs the 1.0 gates

| Gate (per ROAD-TO-1.0 § "1.0.0 — Stability commitment") | Status today | Delta to close |
|---|---|---|
| `@Stable` API surface frozen | Largely true. 13 sealed interfaces + ~16 `@Stable` core SPIs documented. | Decide on ~7 `@Experimental` surfaces at 1.0 cut (see § 1.0.0 below). |
| Two consecutive 0.9.x patch releases without breaking changes | Not yet. 0.9.0 / 0.9.1 just shipped. | Ship 0.9.2 and 0.9.3 cleanly. |
| ≥2 non-internal production deployments running 0.9.x for ≥30 days | Unknown / probably not yet. | Identify pilots; instrument feedback channel. |
| Maven Central published continuously | Partial. Recent releases went Nexus-only. | Confirm 0.9.x is on Central, or backfill. |
| Migration guide 0.x → 1.0 | Doesn't exist yet (`MIGRATION-0.8.md` exists but not 0.9 → 1.0). | Write during 0.9.x cycle. |

---

## Milestone — 0.9.2 (secrets baseline)

**See [RELEASE-PLAN-0.9.2.md](./RELEASE-PLAN-0.9.2.md) for full detail.** Summary scope:

- Generic `SecretsProvider` SPI in `jaiclaw-core` with three reference implementations (env, file/.env, 1Password).
- GitHub Actions secrets migrated to 1Password (13 secrets across 3 workflows).
- Kubernetes deployment integration via 1Password Connect Operator.
- Docker Compose / standalone runtime integration via `op run --`.
- Security hardening flags flipped to default-on.
- SEV-006 + SEV-010 tenant-context fixes.
- CVE quick wins (pdfbox, jsoup, Spring Cloud BOM, false-positive suppressions).

This is the largest of the three milestones by code volume and the highest-leverage for 1.0's stability commitment.

---

## Milestone — 0.9.3 (API surface polish + example cleanup)

**Fixed scope.** Nothing outside the list below ships in 0.9.3.

### Deprecated removals

The 0.8.0 → 0.9.0 transition introduced new constructor signatures while keeping legacy overloads marked `@Deprecated`. By 1.0 these need to go — if they survive into 1.0 they become a `@Stable` commitment we'd have to keep until 2.0.

| File | What to remove | Status |
|---|---|---|
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` | 2 deprecated constructors (11-arg no-tenant form + 4-arg backward-compat form). Callers migrate to `AgentRuntime.builder()`. | ✅ done (0.9.3) |
| `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java` | 5 deprecated constructors (parallel `SessionManager` / `ChannelRegistry` overloads). Callers migrate to `GatewayService.builder()`. | ✅ done (0.9.3) |
| `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/SystemPromptBuilder.java` | `tools()` no-op method (Spring AI now sends tool schemas structurally, not via the prompt). | ✅ done (0.9.3) |

Process: verify zero in-reactor callers via `./mvnw compile` before removal. If the compile passes, no external caller in the repo depends on them. External adopters get one clean error message at 0.9.2 → 0.9.3 upgrade.

### `TemplateResolver` removal date ✅ done (0.9.3)

The 2-arg `TemplateResolver.resolve(String, Map<String, StageOutput>)` overload was `@Deprecated(forRemoval = false)` — indefinite. Removed per recommendation (a). Both production callers (`AgentStageProcessor`, `PipelineRouteBuilder`) already used the `PipelineContext` overload; only test code exercised the deprecated form and was migrated to the `ctxWith(...)` helper.

### Missing `allow-bundled` config in 2 examples ✅ done (0.9.3)

Per CLAUDE.md § Skills & Prompt Size, every example MUST explicitly configure `jaiclaw.skills.allow-bundled` to prevent the 60× token-cost inflation from loading all bundled skills.

| File | Action | Status |
|---|---|---|
| `jaiclaw-examples/security-handshake-server/src/main/resources/application.yml` | Added `jaiclaw.skills.allow-bundled: []` | ✅ |
| `jaiclaw-examples/security-handshake/src/main/resources/application.yml` | Same | ✅ |

Both example POMs already had the `jaiclaw-maven-plugin analyze` execution — the CI gate was in place but ran with the `["*"]` default because the config was missing. Post-fix, `jaiclaw:analyze` reports 2,208 tokens (security-handshake) / 2,125 tokens (security-handshake-server) instead of the ~28K they would have hit.

### Broken `travel-planner` example ✅ done (0.9.3)

`AmadeusApiTravelDataProvider.java` (226 lines, 4 methods throwing `UnsupportedOperationException`) was tech-debt-in-public. Removed per recommendation (a). `StubTravelDataProvider` is now the only bundled data source; `TravelPlannerConfiguration` simplified to one `@Bean` method (no `@Profile("!live-api")`/`@Profile("live-api")` split). README + application.yml + 3 javadoc comments updated to point adopters at the `TravelDataProvider` SPI as the extension point.

### JaCoCo coverage closure ≥50% on 10 core modules

Per the historical ROAD-TO-1.0 lines 103-107: at 1.0 every core module must be at ≥50% line coverage. The 10 modules are:

`jaiclaw-core`, `jaiclaw-config`, `jaiclaw-tools`, `jaiclaw-agent`, `jaiclaw-skills`, `jaiclaw-plugin-sdk`, `jaiclaw-memory`, `jaiclaw-security`, `jaiclaw-gateway`, `jaiclaw-channel-api`.

Process:
1. Run the existing coverage gate (`./mvnw verify` with the JaCoCo profile active).
2. Identify the current floor per module.
3. Write specs **only for the classes pulling each module's average down**. Don't chase coverage for its own sake — target uncovered branches in important code paths.

### Pre-cut gates for 0.9.3

- All 0.9.2 gates still hold (no regressions).
- JaCoCo report in the release notes showing ≥50% per module.
- `releases/release-0.9.3.md` written, with a clear note about the deprecated-constructor removals being the only API-shape change.

---

## The 30-day pilot window (between 0.9.3 and 1.0.0)

This is the gate that makes 1.0 a real commitment rather than a marketing event.

- **No new patch releases unless a CVE drops.** 0.9.3 is the freeze. If something breaks in the wild, we patch — but a patch release means the API freeze didn't hold, and the 30-day clock resets.
- **Two non-internal pilot deployments running 0.9.3 in production for ≥30 days.** "Non-internal" means not run by JaiClaw maintainers. Identify candidate pilots now during the 0.9.2 cycle so they can plan an upgrade.
- **Feedback channel must be documented** before the pilot starts. Slack / GitHub Discussions / email — your call, but pilots need to know where to file issues and expect a response SLA.

If the API freeze breaks during this window (i.e., we have to ship a breaking 0.9.x to fix something), we cut 0.9.4 and restart the 30-day clock. This is by design.

---

## Milestone — 1.0.0 cut

**Fixed scope.** Nothing new ships in 1.0.

### `@Experimental → @Stable` review

Each `@Experimental` surface gets a yes / no / rename decision. Per ROAD-TO-1.0 line 65: "promoted types lock their signatures for 1.0."

| Surface | Recommendation | Why |
|---|---|---|
| `@ToolParameter` + `TypedToolCallback` + `SchemaBuilder` | **Promote to `@Stable`** | ROAD-TO-1.0 line 63 explicitly identified these as the canonical promotion candidates. Used widely. |
| Hook event leaves (`TaskStateChangedEvent`, `SoulUpdatedEvent`, `MemoryUpdatedEvent`) | **Promote to `@Stable`** | The sealed `HookEvent` hierarchy is already `@Stable`. The leaf events are the obvious next step. |
| `JaiClawObservations` metric names | **Promote to `@Stable` with any needed renames committed now** | Operators depend on these for dashboards. Pick names now; freeze them. |
| `jaiclaw-pipeline` DSL | **Keep `@Experimental` through 1.0** | Three-source DSL is recent; templating and actuator surface still firming up. Honest call. |
| `jaiclaw-camel` integration | **Keep `@Experimental` through 1.0** | "Usage signal still small" per the historical ROAD-TO-1.0. |
| `jaiclaw-embabel-delegate` | **Keep `@Experimental` through 1.0** | Tightly coupled to Embabel's own evolution (which is pre-1.0). |
| `jaiclaw-canvas` A2UI artifact rendering | **Keep `@Experimental` through 1.0** | A2UI standard itself still evolving. |

Promoting only 2-3 of 7 surfaces is fine and honest. ROAD-TO-1.0 explicitly allows `@Experimental` to persist past 1.0 (line 134); only `@Stable` carries the no-breaks commitment.

### Consolidated 0.x → 1.0 migration guide

A new `docs/dev/MIGRATION-1.0.md` that walks an adopter from 0.8 / 0.9 / 0.9.2 / 0.9.3 to 1.0. Cross-links to:
- `MIGRATION-0.8.md` (already exists; covers 0.7 → 0.8).
- The 0.9.2 release notes (security defaults flip).
- The 0.9.3 release notes (deprecated removals).
- This file for the 1.0 promotions.

### Maven Central verification

Confirm 0.9.2 and 0.9.3 published cleanly to Maven Central. If they didn't (e.g., went Nexus-only by mistake), backfill or commit publicly to "Nexus-only until 1.0" — but the historical ROAD-TO-1.0 says "Maven Central published continuously" is a 1.0 gate, so the right answer is backfill.

### The cut itself

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
./mvnw versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false -pl :jaiclaw-bom
# Release commit
git add -A && git commit -m "release: 1.0.0"
# Tag
git tag v1.0.0
# Deploy
maven-central-deploy/release.sh 1.0.0 1.0.1-SNAPSHOT
# Push
git push && git push origin v1.0.0
```

This is a **non-event** if the gates have been honored. The work was done in 0.9.2, 0.9.3, and the pilot window. 1.0.0 is the punctuation.

---

## Post-1.0 roadmap (explicit deferral)

These were in 0.9.x scope discussions but are deferred past 1.0. The ordering here is rough priority, not commitment.

### OpenClaw parity (large multi-release roadmap)

`/Users/tap/dev/docs/jaiclaw/internal/OPENCLAW-PARITY-PLAN-V2.md` enumerates 109 tasks across 5 phases:

- **Tier 1** — group chat routing, multi-agent routing per gateway, 17+ missing messaging channels (WhatsApp, Matrix, iMessage, LINE, Google Chat, Mattermost, IRC, Twitch, Nostr, Feishu, Zalo, WeChat, BlueBubbles, Nextcloud Talk, Synology Chat, Tlon/Landscape, Signal → Teams).
- **Tier 2** — DM sender pairing workflows, execution approval workflows, session spawning + sub-agents.
- **Tier 3** — 16+ additional LLM providers (Mistral, Groq, DeepSeek, xAI/Grok, Perplexity, Together, Venice, OpenRouter, LiteLLM, BytePlus, Qianfan, Kimi, SGLang, vLLM, HuggingFace, gateways); image generation; full media pipeline; web search provider diversity.
- **Tier 4** — voice wake-word activation, Talk Mode, native companion apps, device control nodes.
- **Tier 5** — config hot-reload, 28+ additional CLI commands, extended thinking, richer onboarding wizard.
- **Tier 6** — plugin isolation, plugin SDK documentation, plugin marketplace.

This is the **1.x roadmap**, not 1.0. 1.0 commits to the *framework*; parity grows under 1.x minors.

### Pipeline hot reload

`/Users/tap/dev/docs/jaiclaw/internal/PIPELINE_HOT_RELOAD.md` is a deferred design sketch. Three named SPI gaps need filling: `validatePipeline()` public SPI version, `PipelineRegistry.unregister()`, `PipelineRouteController`. Defer to a 1.x minor.

### AgentMind backlog

Per `docs/dev/AGENTMIND-MEMORY-SOUL-PLAN.md`, several items were deferred to Phase 3b+:

- `RedisTendenciesStoreProvider` (WATCH/MULTI CAS pattern).
- `LocalLlmTendenciesProvider` (tendencies learning without external service dependency).
- Soul dashboard UI.

### Telegram docstore auto-indexing

Blocked on `MessageReceivedEvent` broadcast from channel adapters (a P3.3 follow-up). The `TelegramDocStorePlugin.java:75` TODO is explicit about waiting for this. Defer.

### `AgentRuntime` refactor

The class is 829 lines; the responsibility split between session management and routing/prompt orchestration is real tech debt. The reason this is post-1.0: touching it now commits the new surface to `@Stable`. Better to refactor under `@Internal` later or behind a new SPI rather than break the 1.0 contract.

### Module-level READMEs

58 core / channel / extension modules currently lack `README.md`. The Developer Guide (`/Users/tap/dev/docs/jaiclaw/JAICLAW-DEVELOPER-GUIDE.md`) documents everything; per-module READMEs are polish, not a 1.0 gate.

---

## The 2.0 ceiling

The single biggest thing 1.0 leaves on the table is the Spring Boot 4 migration.

**Status as of January 2026:**
- JaiClaw is on Spring Boot 3.5.15 (current for the 3.5.x line).
- Spring Framework 6.2.19 (current for the 6.2.x line, tied to Spring Boot 3.5.x).
- Apache Camel 4.18.1 (current for the 4.18.x line; Camel 4.19+ requires Spring Boot 4).
- **Embabel Agent 0.3.5** is current. The local `0.4.0-SNAPSHOT` HEAD at `/Users/tap/dev/workspaces/openclaw/embabel-agent/` still targets Spring Boot 3.5.x. **There is no Embabel release supporting Spring Boot 4 yet.**

**Why we wait**: Embabel is the binding Tier 1 dependency for JaiClaw's agent runtime. Upgrading JaiClaw to Spring Boot 4 / Spring Framework 7 before Embabel does would either fork Embabel locally (operational nightmare) or strip out the Embabel integration entirely (architectural regression).

**Monitoring**: watch https://github.com/embabel/embabel-agent/releases for an official 0.4.0 GA or later with Spring Boot 4 support. When it ships, JaiClaw 2.0 work begins. Scope sketch:

- Spring Boot 3.5.x → 4.x.
- Spring Framework 6.2.x → 7.x.
- Spring AI 1.1.x → whatever is current.
- Apache Camel 4.18.x → 4.19+ (drops 3.x support entirely).
- Embabel Agent → the new GA.
- Any `@Stable` API breaks that the Spring 7 + Boot 4 migration forces.

This is **explicitly post-1.0**. 1.0 ships on Spring Boot 3.5.x by design, not by accident.

---

## Pointer

For the 0.9.2 deep-dive, see [RELEASE-PLAN-0.9.2.md](./RELEASE-PLAN-0.9.2.md).

For the historical 0.8 → 0.9 framing that this doc inherits, see [../ROAD-TO-1.0.md](../ROAD-TO-1.0.md).
