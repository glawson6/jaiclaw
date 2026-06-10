# JaiClaw 0.7.1 Release Notes

**Release Date**: 2026-06-10

## Highlights

- **Pipeline UX overhaul (Phase A–F)** — six new pipeline features make the `jaiclaw-pipeline` module pleasant to author and operate: startup validator with "did you mean?" suggestions, `{{input}}` / `{{pipeline.*}}` template variables, `.then()` DSL alias, execution tracker + `/actuator/pipelines` endpoint, `PipelineGateway` + `POST /api/pipelines/{id}/trigger`, and a per-file YAML loader (`jaiclaw.pipeline.locations.patterns[]`).
- **Real CRON expressions** — `TriggerType.CRON` now generates `quartz://jaiclaw-pipelines/<id>?cron=<URL-encoded>` URIs and accepts expressions like `0 0 7 ? * MON-FRI`. Requires `camel-quartz-starter`.
- **Six new pipeline example apps** under `jaiclaw-examples/` — support-triage, invoice-processor, aiops-incident-responder, competitive-intel-briefing, sales-enrichment-pipeline, contract-reviewer — plus the `pipeline-e2e` fixture that exercises every UX surface and is hit by the e2e runner.
- **Pipeline module is now opt-in** — `jaiclaw.pipeline.enabled` defaults to `false`. Set it to `true` to activate; if no source is configured (`pipelines[]`, `locations.patterns[]`, or `JaiClawPipeline` code beans) the app fails fast at startup with a clear message.
- **e2e harness scenario 6** — `E2E_SCENARIOS=6 ./e2e/run-e2e-tests.sh` boots the pipeline-e2e example, runs the validator-failure profile, and curls the trigger/actuator surfaces to assert all four phases land green. Documented in `.claude/skills/e2e-test/SKILL.md`.

## New Features

### Pipeline UX (Phase A–F)

**Phase A — Startup validation + dead-letter default**
- New `io.jaiclaw.pipeline.validation` package: `PipelineValidator`, `ValidationReport`, `ValidationError`, `Levenshtein`, `PlaceholderScanner`.
- Validator runs as `@Order(HIGHEST_PRECEDENCE) ApplicationRunner`; consolidates all per-pipeline errors into one `IllegalStateException` with a `did you mean 'research'?`-style hint when a stage reference is misspelled.
- Checks: typo'd `{{stages.X.output}}` refs, unknown PROCESSOR bean names, wrong bean types (`Function<String,String>` required), unknown CHANNEL output `channelId` (when `ChannelRegistry` is on classpath), missing `deadLetterUri` for `DEAD_LETTER` strategy.
- New `PipelineDefaults.deadLetterUri` global fallback property at `jaiclaw.pipeline.defaults.dead-letter-uri`.

**Phase B — Template discoverability + `{{input}}`**
- `TemplateResolver.resolve(template, PipelineContext)` overload adds `{{input}}` (original trigger payload, 32 KB cap), `{{pipeline.id}}`, `{{pipeline.executionId}}`, `{{pipeline.tenantId}}`, `{{pipeline.correlationId}}`.
- WARN-on-miss: unresolved placeholders log the list of available variables at the point of resolution so authors catch typos.
- Hyphenated stage names now resolve in placeholders (regex changed from `\w+` to `[\w-]+`). Lets you write `{{stages.classify-and-sentiment.output}}` without escaping.
- New `PipelineContext.availableVariables()` for diagnostic use.

**Phase C — DSL `.then()` chaining**
- `.then(stageName)` added to `PipelineBuilder` and `StageBuilder` as a readability alias for `.stage(name)`. Backwards-compatible: existing chains keep working.

**Phase D — Execution tracking + Actuator**
- New `io.jaiclaw.pipeline.tracking` package: `PipelineExecutionTracker`, `PipelineExecutionSummary`, `ExecutionStatus`.
- Thread-safe bounded per-pipeline history (`maxPerPipeline` default 50), plus `byId(executionId)` direct lookup. Truncates `failureReason` to 4 KB.
- New `PipelineActuatorEndpoint` (`@Endpoint(id="pipelines")`) registered by a separate `PipelineActuatorConfiguration` `@AutoConfiguration` gated on `spring-boot-actuator` classpath. Three read operations: list, byId, executionById.

**Phase E — `PipelineGateway` + HTTP trigger**
- New `io.jaiclaw.pipeline.gateway` package: `PipelineGateway` interface, `DefaultPipelineGateway`, `PipelineExecutionHandle`.
- New `PipelineTriggerController` (`@RestController` at `${jaiclaw.pipeline.http-trigger.base-path:/api/pipelines}/{id}/trigger`) returns `202 Accepted` + handle JSON. 404 + error body for unknown pipelines. Reads `X-Tenant-Id` / `X-Correlation-Id` headers.
- Registered by `PipelineWebConfiguration` `@AutoConfiguration` gated on `RestController` classpath + `jaiclaw.pipeline.http-trigger.enabled` (default true).
- `PipelineRouteBuilder` adds a Phase E convergence route at `direct:pipeline-<id>` for every pipeline regardless of primary trigger type, so the gateway and controller work uniformly (MANUAL, HTTP, FILE, CRON, CAMEL_URI).
- Gateway-supplied `executionId`/`tenantId`/`correlationId` headers are honored end-to-end so the handle returned by `trigger(...)` resolves via the actuator.

**Phase F — Per-file YAML loader**
- New `io.jaiclaw.pipeline.loader` package: `PipelineFileLoader`, `PipelineYamlParser`, `PipelineLoadException`.
- New `jaiclaw.pipeline.locations.patterns[]` Spring `ResourcePatternResolver` patterns (`classpath*:...`, `file:...`). Empty by default — opt-in.
- One file = one `PipelineDefinition`. Filename stem fallback for missing `id:`; `enabled: true` defaulted when omitted (because the record's `boolean enabled` field has no null-default).
- Source precedence on id conflict: code beans > per-file YAML > inline YAML.
- One bad file logs a WARN and is skipped; the rest of the registry still loads.

### CRON triggers (Camel quartz)

- `PipelineRouteBuilder.resolveTriggerUri()` now emits `quartz://jaiclaw-pipelines/<id>?cron=<URL-encoded-cron>` for `TriggerType.CRON`. Camel quartz handles the schedule.
- Throws a clear `IllegalStateException` at route build time when the cron expression is null/blank.
- `camel-quartz-starter` added as `<optional>` dep on jaiclaw-pipeline. CRON-using apps must include it explicitly (or pull it transitively via e.g. `competitive-intel-briefing`'s pom).

### Pipeline module is opt-in

- New top-level `jaiclaw.pipeline.enabled` flag (default `false`). When false, the registry stays empty and the route initializer + validation runner don't fire.
- When `enabled=true` but no source is configured (no inline `pipelines[]`, no `locations.patterns[]`, no `JaiClawPipeline` code bean), the auto-config throws `IllegalStateException` at startup with guidance on which property to set.

### Six new commercial pipeline examples

| Module | Trigger | Stages | Showcases |
|---|---|---|---|
| `jaiclaw-example-support-triage-pipeline` | MANUAL | 6 (3 AGENT + 3 PROCESSOR) | `.then()` DSL, `{{pipeline.executionId}}`, confidence-based escalation |
| `jaiclaw-example-invoice-processor` | FILE | 5 (2 AGENT + 3 PROCESSOR) | `errorStrategy: DEAD_LETTER` + `deadLetterUri`, JSONL audit |
| `jaiclaw-example-aiops-incident-responder` | CAMEL_URI | 6 (4 AGENT + 2 PROCESSOR) | `direct:incident-alert` trigger + Phase E convergence route, embedded runbook library |
| `jaiclaw-example-competitive-intel-briefing` | CRON | 5 (3 AGENT + 2 PROCESSOR) | First quartz cron consumer, `@ConfigurationProperties` competitor list, filesystem signal-cache diff |
| `jaiclaw-example-sales-enrichment-pipeline` | CRON | 5 (3 AGENT + 2 PROCESSOR) | Per-tick batch loop pattern (cron pops one lead from queue per fire) |
| `jaiclaw-example-contract-reviewer` | FILE | 6 (5 AGENT + 1 PROCESSOR) | `errorStrategy: RETRY_THEN_FAIL` + `maxRetries: 2`, inline 5-rule playbook |

Each example drops into an interactive Spring Shell with a tiny consistent vocabulary (`executions`, `last-result`, plus example-specific verbs).

### e2e harness — scenario 6

- New `run_scenario_6` in `e2e/run-e2e-tests.sh` validates the pipeline-e2e example end-to-end with four sub-results: `6a-Validator` (broken profile must exit non-zero with consolidated error + "did you mean"), `6b-HTTP-trigger` (POST returns 202; unknown id returns 404), `6c-Actuator` (list + byId surface the execution), `6d-Template` (status=SUCCESS + `{{input}}` flowed through).
- Default scenario list: `1,2,3,4,5` → `1,2,3,4,5,6`.
- New env vars: `E2E_PIPELINE_PORT` (default 8100), `JAICLAW_E2E_WITH_AGENT` (optional AGENT-stage sub-test).
- Documented in `e2e/README.md`, `.claude/skills/e2e-test/SKILL.md`, `.claude/agents/e2e-tester.md`.

### Documentation

- `docs/dev/PIPELINE_UX_IMPROVEMENTS.md` — closing footer captures the six implemented phases and links to follow-up docs.
- `PIPELINE_EXAMPLES_PLAN.md` — six-example design rationale. *(Moved out of the public repo in the 2026-06-10 docs reorg; preserved in the private hub at `/Users/tap/dev/docs/jaiclaw/internal/PIPELINE_EXAMPLES_PLAN.md`.)*
- `PIPELINE_HOT_RELOAD.md` — future-feature design sketch for filesystem-watcher-driven hot reload (deferred; documents the three API gaps to close first). *(Also moved to the private hub.)*

## New Modules

- `extensions/jaiclaw-pipeline` gains new packages: `io.jaiclaw.pipeline.validation`, `io.jaiclaw.pipeline.tracking`, `io.jaiclaw.pipeline.gateway`, `io.jaiclaw.pipeline.web`, `io.jaiclaw.pipeline.actuator`, `io.jaiclaw.pipeline.loader`.
- `jaiclaw-examples/pipeline-e2e` — e2e fixture for scenario 6 (already shipped earlier in the cycle; called out here because the new modules in this release depend on it being the reference template).
- `jaiclaw-examples/support-triage-pipeline`
- `jaiclaw-examples/invoice-processor`
- `jaiclaw-examples/aiops-incident-responder`
- `jaiclaw-examples/competitive-intel-briefing`
- `jaiclaw-examples/sales-enrichment-pipeline`
- `jaiclaw-examples/contract-reviewer`

Example count: 32 → 39.

## Breaking Changes

- **`jaiclaw.pipeline.enabled` is now required to activate the pipeline module.** Existing apps that rely on the pipeline auto-config must explicitly set `jaiclaw.pipeline.enabled: true` in `application.yml`. Apps that don't use the pipeline module are unaffected.
- **`PipelineProperties` canonical constructor signature changed.** New fields: `enabled`, `tracker`, `httpTrigger`, `locations`. Legacy 3-arg / 5-arg constructors were removed (they conflicted with Spring's `@ConfigurationProperties` record binding). Tests and downstream code that constructed `PipelineProperties` directly must use the full 7-arg form: `(enabled, pipelines, defaults, security, tracker, httpTrigger, locations)`.
- **`PipelineDefaults` canonical constructor signature changed.** New field: `deadLetterUri` (nullable, default null). The legacy 3-arg `PipelineDefaults(int, int, boolean)` constructor was removed.
- **CRON triggers no longer accept "milliseconds as cron".** Previously `TriggerType.CRON` mis-routed the expression into `timer:?period=`. CRON-using pipelines now require a real cron expression and `camel-quartz-starter` on the classpath.

## Dependency Updates

| Dependency | Previous | New | Notes |
|---|---|---|---|
| `jackson-dataformat-yaml` | — | added | New required dep on `jaiclaw-pipeline` for the per-file YAML loader |
| `camel-quartz-starter` | — | optional | Required at runtime only when `TriggerType.CRON` is used |
| `jaiclaw-channel-api` | — | optional dep | Enables `channelId` validation in `PipelineValidator` when present |
| `spring-boot-actuator` | — | optional dep | Enables `/actuator/pipelines` endpoint |
| `spring-web` | — | optional dep | Enables `PipelineTriggerController` |

## Bug Fixes

- **Auto-config nested-config trap.** The original Phase D/E work put the actuator endpoint + HTTP trigger controller in nested `@Configuration` classes inside `PipelineAutoConfiguration`. Their `@ConditionalOnBean` conditions evaluated in the same pass as the parent, so neither bean ever registered — `/actuator/pipelines` and `POST /api/pipelines/{id}/trigger` returned 404 even when the module was on the classpath. Fixed by extracting `PipelineActuatorConfiguration` and `PipelineWebConfiguration` to top-level `@AutoConfiguration` classes with `@AutoConfigureAfter(PipelineAutoConfiguration)`.
- **Hyphenated stage names in placeholders.** Templates like `{{stages.classify-and-sentiment.output}}` previously failed startup validation because `TemplateResolver` and `PlaceholderScanner` regexes used `\w+`. Both updated to `[\w-]+` with a new spec lock.
- **Gateway / tracker handle correlation.** Before this release, `PipelineGateway.trigger(...)` generated an executionId that didn't match the one surfaced by the tracker (the route generated a fresh UUID). `PipelineRouteBuilder` now honors gateway-supplied headers (`JaiClawPipelineGatewayExecutionId`, `JaiClawPipelineTenantId`, `JaiClawPipelineCorrelationId`) so the returned handle resolves via the actuator.

## Migration Guide

Existing apps that use the pipeline module:

1. **Enable the module** in `application.yml`:
   ```yaml
   jaiclaw:
     pipeline:
       enabled: true
   ```

2. **For CRON pipelines**, add `camel-quartz-starter` to the app's `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.apache.camel.springboot</groupId>
       <artifactId>camel-quartz-starter</artifactId>
       <version>${camel.version}</version>
   </dependency>
   ```
   And update any cron expression that was previously a millisecond duration to a real cron expression (e.g. `86400000` → `0 0 0 * * ?`).

3. **For programmatic construction of `PipelineProperties` or `PipelineDefaults`** (typically test code), update to the new canonical constructors. Use the named `DEFAULT` constants where possible.

4. **No action needed for** existing inline-YAML pipelines or `JaiClawPipeline` code beans — both source paths are unchanged.

## Security Fixes

None this release.

## Infrastructure

- Pipeline test count: 127 → 159 (+32 new specs across validation, tracking, gateway, loader, autoconfig, route-builder CRON, web integration).
- E2E harness scenario count: 5 → 6.
- The e2e example app (pipeline-e2e) now exercises every Phase A–F surface; the scenario 6 sub-results provide pinpoint diagnostics on regressions.
