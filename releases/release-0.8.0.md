# JaiClaw 0.8.0 Release Notes

**Release Date**: 2026-06-10

> 0.8.0 is the **"make extending it delightful"** release — Phase 3 of
> the [2026-06-10 codebase audit](../docs/CODEBASE-ANALYSIS-2026-06-10.md)
> remediation. It is a **hard-break** release: a small number of public
> API moves (the `JaiClawAutoConfiguration` split, the `HookName` →
> sealed `HookEvent` switch, the legacy `jclaw_ak_` API-key prefix being
> dropped) require code changes in custom plugins and channel adapters.
> All breaks are documented in [`docs/MIGRATION-0.8.md`](../docs/MIGRATION-0.8.md).

---

## Highlights

- **API stability program (P3.5)** — every public top-level type now
  carries `@Stable`, `@Experimental`, or `@Internal`. Headline SPIs
  (`ToolCallback`, `ChannelAdapter`, `JaiClawPlugin`, `AgentRuntime`,
  `SessionManager`, `TenantGuard`, `AuditLogger`, MCP providers) are
  stable from this release on. A new `ApiStabilityMarkerSpec` locks the
  policy at build time. JSpecify `@NullMarked` on six core packages.

- **Typed tool parameters (P3.2)** — record-based tool parameter binding
  via `@ToolParameter` + `SchemaBuilder` + `TypedToolCallback<P>`. The
  hand-written JSON-Schema-block + `requireParam(...)` pair the audit
  flagged is gone for migrated tools. `WebFetchTool` ships as the
  canonical example. The legacy `ToolCallback` SPI continues to work
  unchanged.

- **Typed hook events (P3.1)** — sealed `HookEvent` hierarchy + generic
  `PluginApi.on(Class<E>, HookHandler<E>)`. The `HookName` enum and the
  `extractString`/`extractBoolean` casting helpers the audit flagged are
  gone. All seven in-repo plugins migrated.

- **`AbstractChannelAdapter` + 11-channel migration (P3.3)** — one
  common base class final-implements lifecycle, identity, and outbound
  chunking. New `WebhookSignatureUtil` consolidates HMAC verification.
  Custom channel adapters do a short migration; in exchange, adding a
  new channel is now a ~200-line job.

- **Auto-configuration split (P3.4)** — the 782-LOC
  `JaiClawAutoConfiguration` monolith becomes seven domain-scoped
  auto-configs (`Http`, `Tenant`, `Tools`, `Plugin`, `Memory`,
  `Skills`, `Agent`) with explicit `@AutoConfigureAfter` ordering.

- **Test coverage gate (P3.6)** — JaCoCo 0.8.12 in root
  `pluginManagement`; five audit-flagged modules enforce ≥ 40% line
  coverage at `mvn verify`. All five currently pass.

- **Production deployment guide (P3.7)** — new
  `docs/user/PRODUCTION-DEPLOYMENT.md` with K8s manifests, Helm values,
  observability via the existing `JaiClawObservations` metrics, security
  hardening, cloud-provider notes, and a runbook.

- **Strategy + roadmap docs (P3.8)** — new `docs/POSITIONING.md` and
  `docs/ROAD-TO-1.0.md` for technical decision-makers and adopters
  weighing 0.8 vs waiting for 1.0.

- **Multi-tenant isolation hardening** — six in-memory stores keyed by
  `TenantGuard.resolveStorageKey(...)`; three async paths wrap with
  `TenantContextPropagator`; opaque-default WARN; optional strict-mode
  validation; regression guard spec. Closes the multi-tenancy gaps the
  audit flagged.

- **Spring AI BOM realigned to 1.1.7** (was 1.1.4 — consumers of the BOM
  previously got a different Spring AI version than the framework was
  built against).

---

## Breaking changes

See [docs/MIGRATION-0.8.md](../docs/MIGRATION-0.8.md) for the full diff
per area; this is the executive summary.

### P3.1 — Hook event model

`HookName` enum **removed**. `PluginApi.on(HookName, HookHandler)`
**removed**. Subclasses of `HookHandler` with the old
`(EventContext, T)` signature must migrate to `HookHandler<E extends
HookEvent>` with the typed `handle(E event)` signature.

```diff
- pluginApi.on(HookName.MESSAGE_INBOUND, (ctx, msg) -> {
-   String text = extractString(msg, "text");
-   handle(text);
- });
+ pluginApi.on(MessageInboundEvent.class, event -> {
+   handle(event.text());
+ });
```

### P3.3 — Channel adapter base class

Channel adapters extend `AbstractChannelAdapter` and implement three
hooks instead of seven concrete methods. Inbound message dispatch goes
through `dispatchInbound(...)`; the lifecycle `start`/`stop`/
`isRunning` methods are now `final`. Custom channel adapters need a
short migration.

### P3.4 — Auto-configuration class moves

`JaiClawAutoConfiguration` is gone. Adopters who referenced it directly
(via `@AutoConfigureAfter(JaiClawAutoConfiguration.class)`,
`spring.autoconfigure.exclude=…JaiClawAutoConfiguration`, or
`@Import(JaiClawAutoConfiguration.class)`) must switch to
`JaiClawAgentAutoConfiguration` (the last entry in the DAG). Bean
names and types are unchanged — most apps don't notice.

### P3.5 — Naming cleanup

- `ApiKeyProvider` no longer accepts the legacy `jclaw_ak_…` prefix.
  Operators with stored `jclaw_ak_…` keys regenerate (any random hex
  string works; constant-time comparison is default-on in 0.8).
- `ProjectScanner` (jaiclaw-prompt-analyzer) no longer falls back to
  `jclaw:` as a YAML root key. Use `jaiclaw:`.
- Three orphan `.iml` files removed from the working tree.

---

## New modules

None. 0.8.0 is consolidation and API-shape work. New modules continue
in `0.8.x` patch releases.

---

## Dependency updates

- **Spring AI**: 1.1.4 → **1.1.7** in `jaiclaw-bom` (now matches root).
- **JaCoCo**: introduced at **0.8.12** in root pluginManagement.
- **XStream**: pinned to **1.4.21** in `jaiclaw-rules` (was 1.4.20 via
  Drools transitive); **mxparser excluded** outright across all Drools
  artifacts (CVE chain attributed to mxparser 1.2.2, CVSS up to 9.9).

No other dependency moves in 0.8.0.

---

## Security fixes

- **CVE chain via mxparser 1.2.2** — excluded outright across
  `jaiclaw-rules`. CVSS up to 9.9.
- **`ShellExecTool` command logging** dropped from INFO to DEBUG —
  prevents secrets in shell args from landing in default-level
  production logs.
- **Multi-tenant in-memory store isolation** — six previously-shared
  in-memory stores now key by tenant; three previously-context-
  dropping async paths now propagate the tenant context. The
  remediation is documented in
  [`docs/MIGRATION-0.8.md`](../docs/MIGRATION-0.8.md) §
  Multi-Tenant Isolation Hardening.
- **Opaque-default WARN** — `TenantGuard` emits a one-time WARN at
  startup if `jaiclaw.tenant.default-tenant-id` is still the literal
  string `default`. Production deployments must override; optional
  strict-mode validation flag rejects low-entropy values at startup.
- **`mode=none` security headers** — the `mode=none` chain now applies
  the same response headers as `api-key` and `jwt` modes (HSTS,
  X-Frame-Options, Referrer-Policy, X-Content-Type-Options).

---

## Bug fixes

- **Spring AI BOM version drift** — the BOM was pinned at 1.1.4 while
  the root `pom.xml` was at 1.1.7. Consumers of the BOM transitively
  resolved a different Spring AI version than the framework was tested
  against. Fixed in 0.8.0.
- **`jaiclaw-pipeline` startup failure modes** — `PipelineValidator`
  now consolidates all per-pipeline errors into a single
  `IllegalStateException` (was throwing on the first one) and adds
  Levenshtein-≤ 2 "did you mean?" hints for typo'd refs.

---

## Documentation

- New: [`docs/POSITIONING.md`](../docs/POSITIONING.md)
- New: [`docs/ROAD-TO-1.0.md`](../docs/ROAD-TO-1.0.md)
- New: [`docs/MIGRATION-0.8.md`](../docs/MIGRATION-0.8.md)
- New: [`docs/user/PRODUCTION-DEPLOYMENT.md`](../docs/user/PRODUCTION-DEPLOYMENT.md)
- Updated: [`docs/INDEX.md`](../docs/INDEX.md) — Strategy & roadmap
  section.
- Updated: [`CLAUDE.md`](../CLAUDE.md) — multi-tenancy conformance
  check, tenant-default-id security note.

---

## What's next (0.8.x → 0.9.0)

0.8.x patch releases will be bug-fix / security-backport only. The
0.9.0 milestone is the **API freeze** — see
[`docs/ROAD-TO-1.0.md`](../docs/ROAD-TO-1.0.md) for the gates:

1. Every `@Experimental` type gets a yes/no promotion decision.
2. Public-surface audit confirms every non-`internal` public type
   carries a stability marker.
3. JaCoCo line coverage ≥ 50% on every core module.
4. Migration guide 0.8 → 0.9 with concrete pre-1.0 deprecation steps.

---

## Upgrade checklist

1. **Read [`docs/MIGRATION-0.8.md`](../docs/MIGRATION-0.8.md)** — it's
   the authoritative diff per area.
2. **Custom plugins**: migrate `HookName` → typed `HookEvent`
   subclasses; update `extractString`/`extractBoolean` calls to typed
   accessors on the event record.
3. **Custom channel adapters**: extend `AbstractChannelAdapter`;
   implement `doStart()` / `doStop()` / `doSend(ChannelMessage)`; route
   inbound through `dispatchInbound(...)`.
4. **App POMs referencing `JaiClawAutoConfiguration`**: switch to
   `JaiClawAgentAutoConfiguration`.
5. **API keys**: if you stored `jclaw_ak_…` keys, regenerate to plain
   random hex.
6. **`jaiclaw.tenant.default-tenant-id`**: set to a high-entropy value
   in production. Consider enabling
   `jaiclaw.tenant.strict-default-tenant-id: true`.
7. **`jaiclaw.skills.allow-bundled`**: confirm you've set it to `[]`
   or a tight whitelist; the `["*"]` default loads ~26k tokens of
   bundled skills per request.
8. **`mvn verify`**: confirm JaCoCo `check` passes on
   `jaiclaw-core`, `jaiclaw-config`, `jaiclaw-subscription`,
   `jaiclaw-tasks`, `jaiclaw-docstore`. Other modules are still
   un-gated in 0.8.0.
