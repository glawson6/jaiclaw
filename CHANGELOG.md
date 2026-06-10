# Changelog

All notable changes to JaiClaw are documented in this file. The format
loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
with the caveat that we are pre-1.0 — public APIs may change between minor
versions until 1.0.

For deep release notes (per-module changes, breaking-change migration
hints, full lists of new examples), see `releases/release-X.Y.Z.md`.

## [Unreleased]

In progress in the `0.7.1-SNAPSHOT` line (heading to **0.8.0** —
hard-break release; see `docs/MIGRATION-0.8.md`).

### Breaking changes (0.8.0)

- **Typed hook events.** The plugin SDK's hook API migrated from
  `HookName` enum + `HookHandler<E, C>` to a sealed `HookEvent`
  hierarchy (16 events under `io.jaiclaw.core.hook.event`) +
  `HookHandler<E extends HookEvent>`. `PluginApi.on(...)` is now keyed
  by event class. The `HookName` enum is removed; every in-repo plugin
  (`ObservabilityPlugin`, `TelegramDocStorePlugin`,
  `TelegramSubscriptionPlugin`, `ResearchAssistantPlugin`,
  `CodeScaffolderPlugin`, `DataPipelinePlugin`,
  `IncidentResponderPlugin`) migrated. The 18-line
  `extractString`/`extractBoolean` casting helpers the audit flagged
  are gone — typed accessors replace them. See
  `docs/MIGRATION-0.8.md` § P3.1 for the full diff.
- **`JaiClawAutoConfiguration` monolith removed.** The 782-LOC
  `io.jaiclaw.autoconfigure.JaiClawAutoConfiguration` is replaced by seven
  domain-scoped auto-configs (`Http`, `Tenant`, `Tools`, `Plugin`,
  `Memory`, `Skills`, `Agent`). Adopters referencing the monolith via
  `@AutoConfigureAfter`, `spring.autoconfigure.exclude`, or `@Import` must
  switch to `JaiClawAgentAutoConfiguration` (the last entry in the new
  DAG). Bean names and types are unchanged — most apps are unaffected.
  See `docs/MIGRATION-0.8.md` § P3.4 for the full diff.

### Added

- Multi-tenant isolation hardening: 6 in-memory stores keyed by
  `TenantGuard.resolveStorageKey(...)`; 3 async paths
  (`SubscriptionExpiryScheduler`, `JsonlCallStore`, `CallManager`)
  wrap with `TenantContextPropagator`; opaque-default WARN on
  `jaiclaw.tenant.default-tenant-id="default"`; optional strict-mode
  validation flag; `TenantIsolationGuardSpec` regression guard scans
  the audited modules.
- `JaiClawSecurityNoneModeHeadersSpec` — proves `mode=none` chain emits
  HSTS / X-Frame-Options / Referrer-Policy / X-Content-Type-Options.
- Governance files: `CONTRIBUTING.md`, `SECURITY.md`,
  `CODE_OF_CONDUCT.md`, GitHub issue/PR templates, `CHANGELOG.md`.

### Changed

- `jaiclaw-bom` Spring AI version synced to **1.1.7** (was 1.1.4 —
  consumers of the BOM previously got a different version than the
  framework was built against).
- `mode=none` security chain now applies the same response headers as
  `api-key` and `jwt` modes (`JaiClawSecurityAutoConfiguration.NoneSecurityConfiguration`).
- `ShellExecTool` command logging dropped from INFO to DEBUG — prevents
  secrets in shell args from landing in default-level production logs.
- `extensions/jaiclaw-rules`: XStream pinned to **1.4.21** (Drools
  transitive was 1.4.20); **mxparser excluded** outright across all
  Drools artifacts. Closes the CVE chain attributed to mxparser 1.2.2
  (CVSS up to 9.9) without forcing a Drools major-version bump.

### Removed

- Tracked `feature-parity-report.md` files at the repo root and under
  `docs/backlog/` (regenerated artifacts; now in `.gitignore`).

## [0.7.1] - 2026-06-10

### Added

- **Pipeline UX overhaul (Phase A–F)**: startup validator with
  "did-you-mean?" suggestions, `{{input}}` and `{{pipeline.*}}`
  template variables, `.then()` DSL alias, `PipelineExecutionTracker`
  exposed via `/actuator/pipelines`, `PipelineGateway` +
  `POST /api/pipelines/{id}/trigger`, per-file YAML loader at
  `jaiclaw.pipeline.locations.patterns[]`.
- **Real CRON expressions** for `TriggerType.CRON` via
  `quartz://jaiclaw-pipelines/<id>?cron=<URL-encoded>`. Requires
  `camel-quartz-starter`.
- **Six new pipeline example apps** under `jaiclaw-examples/`:
  `support-triage-pipeline`, `invoice-processor`,
  `aiops-incident-responder`, `competitive-intel-briefing`,
  `sales-enrichment-pipeline`, `contract-reviewer`, plus the
  `pipeline-e2e` fixture and **scenario 6** in the e2e runner.

### Changed

- **`jaiclaw-pipeline` is now opt-in.** `jaiclaw.pipeline.enabled`
  defaults to `false`. Set it to `true` and configure at least one
  source (`pipelines[]`, `locations.patterns[]`, or a
  `JaiClawPipeline` code bean) or the app fails fast at startup.
- Hyphenated stage names now work in placeholders
  (`{{stages.classify-and-sentiment.output}}`).

See [releases/release-0.7.1.md](releases/release-0.7.1.md) for the
full per-phase walkthrough and the `PipelineDefaults.deadLetterUri`
configuration reference.

## [0.7.0] - 2026-06-06

### Added

- **Composite tool profiles** — `CompositeToolProfile` in
  `jaiclaw-core`, `CompositeToolProfileRegistry` in `jaiclaw-config`.
  Define named compositions in YAML with `profiles: [...]` plus
  `include` / `exclude` overrides for per-agent tool access control.
- OWASP dependency-check plugin in `pluginManagement` for repeatable
  CVE scanning against the NVD database.

### Security

- Dependency CVE remediation: upgraded Apache Camel, Netty, Tomcat,
  Log4j, and Spring Cloud to patch 122 known CVEs identified via the
  OWASP scan.

See [releases/release-0.7.0.md](releases/release-0.7.0.md).

## [0.6.0] - 2026-06-02

### Added

- **4 new channel adapters**, bringing the total to 11 messaging
  platforms: WhatsApp Business API (`jaiclaw-channel-whatsapp`),
  Google Chat (`jaiclaw-channel-googlechat`), LINE
  (`jaiclaw-channel-line`), Matrix (`jaiclaw-channel-matrix`).
- **10 new extension modules**: observability (Micrometer/OTLP), web
  search, model catalog, memory wiki, task management, video
  generation, and more.

### Fixed

- `mode=none` NPE resolved.
- Embabel auto-configuration made fully opt-in (must add
  `jaiclaw-starter-embabel` to pull in Embabel beans).
- Various stale workarounds removed during the security-hardening
  sweep.

See [releases/release-0.6.0.md](releases/release-0.6.0.md).

## [0.5.0] - 2026-05-28

### Added

- **`jaiclaw-email` extension**: SMTP2Go-backed email sending,
  `EmailMcpToolProvider`, `jaiclaw-starter-email` starter.
- **`jaiclaw-rules` extension**: Drools 9.44 rule engine —
  `RuleExecutionService`, classpath/filesystem/URL rule loaders,
  `SECTION_RULES` tool-catalog entry, `jaiclaw-starter-rules` starter.
- New example apps: `procurement-approval`, `support-triage`,
  `tax-advisor`, `onboarding-intake`.

### Changed

- Spring AI upgraded to **1.1.7** (from 1.1.4). Resolves CVE-2026-41863
  and pulls in updated model support across all Spring AI providers.

See [releases/release-0.5.0.md](releases/release-0.5.0.md).

## [0.4.0] - 2026-05-18

Release notes not retained in-repo before 0.5.0; see git tag for the
diff. Highlights from `git log v0.3.0..v0.4.0`:

- Continued OpenClaw parity work — additional skill plumbing,
  expanded MCP support.

## [0.3.0] - 2026-05-15

Initial public-shaped release. Establishes the core module split
(`jaiclaw-core`, `jaiclaw-tools`, `jaiclaw-agent`, `jaiclaw-skills`),
the first 7 channel adapters, and the gateway / shell apps.

## [0.1.0] - 2026-04-22

Initial commit of the JaiClaw port of OpenClaw. Java 21 / Spring Boot
3.5 / Spring AI / Embabel.

---

[Unreleased]: https://github.com/glawson6/jaiclaw/compare/v0.7.1...HEAD
[0.7.1]: https://github.com/glawson6/jaiclaw/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/glawson6/jaiclaw/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/glawson6/jaiclaw/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/glawson6/jaiclaw/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/glawson6/jaiclaw/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/glawson6/jaiclaw/compare/v0.1.0...v0.3.0
[0.1.0]: https://github.com/glawson6/jaiclaw/releases/tag/v0.1.0
