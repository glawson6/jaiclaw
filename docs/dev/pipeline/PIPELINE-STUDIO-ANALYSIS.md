# JaiClaw Visual Pipeline Builder ("Pipeline Studio") — Analysis

> **Scope:** What it takes to assemble JaiClaw pipelines visually in a UI: what already exists in the codebase to build on, what's missing, the components needed (backend and frontend, with both UI-stack options analyzed), and a baseline processor catalog to make the builder useful on day one.
> **Grounding:** All class references verified against `extensions/jaiclaw-pipeline/` at HEAD (2026-07-21), plus `docs/dev/PIPELINE-STRATEGY.md` and `docs/dev/PIPELINE_UX_IMPROVEMENTS.md`.

---

## 1. Executive summary

JaiClaw is unusually well-positioned for a visual pipeline builder. The hard parts of most "visual workflow" products — a clean declarative model, a real execution engine, live event streaming, server-side validation with good error messages, and multi-tenant isolation — already exist in `jaiclaw-pipeline`. The definition model is a flat, serializable record graph (`PipelineDefinition` → `TriggerDefinition` + ordered `StageDefinition[]` + `OutputDefinition`) that maps one-to-one onto canvas nodes, and the per-file YAML format is already the natural save format for anything a UI produces.

What's missing is the **authoring plane**. Everything today is read-and-run: definitions load once at boot (inline YAML, per-file YAML, or Java DSL code beans), routes are built once by an `ApplicationRunner`, and `PipelineRegistry` has no update or delete. There is no API to create a pipeline at runtime, no way to deploy one without a restart, no catalog endpoint a palette could be populated from, and the `PROCESSOR` stage SPI (`Function<String, String>` beans) carries no metadata or per-stage configuration — so a UI could show a bean name and nothing else.

The recommended shape: three new modules — `jaiclaw-pipeline-authoring` (draft store, catalog, validation, hot deploy), `jaiclaw-pipeline-processors` (the baseline processor pack), and `jaiclaw-pipeline-studio` (the UI, recommended as a React Flow SPA packaged as static resources and served by the gateway). The existing SSE, tracker, render, and trigger surfaces are reused as-is for the "run and watch" half of the UI. A linear v1 canvas is honest about what the engine executes today (stages are a sequence; `switch`/`parallel` are still Phase-2 items in PIPELINE-STRATEGY.md), while React Flow leaves the door open for true DAG editing when branching lands.

---

## 2. What exists today (the foundation)

### 2.1 The definition model is already a node graph

`PipelineDefinition` (13 fields) is an immutable record: `id`, `name`, `description`, `tenantIds`, `enabled`, `trigger`, `errorStrategy`, `maxRetries`, `deadLetterUri`, `stages`, `output`, `security`, `resultTemplate`. Visually this is exactly:

```
[Trigger node] → [Stage node] → [Stage node] → … → [Output node]
                      (pipeline-level settings = canvas-level properties panel)
```

- **Trigger node** — `TriggerDefinition(type, uri, expression, path)`; types `FILE | CRON | HTTP | CAMEL_URI | MANUAL` (`TriggerType`). CRON runs through Quartz (`quartz://jaiclaw-pipelines/<id>?cron=…`).
- **Stage nodes** — `StageDefinition(name, type, bean, agentId, systemPrompt, channelId, uri, timeout, transport, runtime, embabelWorkflow)`; types `AGENT | PROCESSOR | CAMEL` (`StageType`), with `runtime = NATIVE | EMBABEL` selecting Embabel's GOAP planner per stage, and an optional per-stage `TransportConfig` (any Camel URI instead of default SEDA, with HMAC-SHA256 / bearer transport auth).
- **Output node** — `OutputDefinition(type, channelId, uri, template)`; types `CHANNEL | CAMEL_URI | LOG | NONE`.
- **Edges carry data implicitly** — every stage's output lands in `PipelineContext.stageOutputs` keyed by stage name, and any downstream prompt/template can reference `{{stages.X.output}}`, `{{stages.X.metadata.k}}`, `{{input}}`, `{{pipeline.id|executionId|tenantId}}` (`TemplateResolver`). `PipelineContext.availableVariables()` already computes the variable set at any execution point — which is precisely what a template-autocomplete widget needs.

### 2.2 Three authoring paths, one precedence chain

Definitions load from inline YAML (`jaiclaw.pipelines[]`), per-file YAML (`jaiclaw.pipeline.locations.patterns[]` → `PipelineFileLoader`/`PipelineYamlParser`, one file = one definition, filename stem = fallback id), and Java DSL code beans (`JaiClawPipeline.define()` with the fluent `.trigger().cron(…).then("x").agent(…).output().log()` chain). Precedence on id conflict: **code beans > per-file YAML > inline YAML**. The per-file YAML format is the ideal round-trip target for the UI: it's the whole definition, human-diffable, and git-able.

### 2.3 The runtime the UI drives

`PipelineRouteBuilder` compiles each definition into a Camel route (`trigger → seda:stage-0 → … → output`), with SEDA backpressure, per-pipeline error strategy (`STOP | RETRY_THEN_FAIL | DEAD_LETTER`), tenant context propagation, `MessageChunker`-independent output typing, audit (`PipelineAuditor`), hooks (`PipelineHookFirer` firing the six sealed `Pipeline*Event`s), and Micrometer metrics (`PipelineMetrics`). **Routes are added exactly once**, in `PipelineAutoConfiguration.pipelineRouteInitializer` (an `ApplicationRunner` calling `camelContext.addRoutes(...)` at startup). The module is opt-in (`jaiclaw.pipeline.enabled`, default `false`) and fails fast when enabled with no pipeline source.

### 2.4 Read/trigger surfaces the UI gets for free

These already exist and cover most of the *monitoring* half of a builder UI:

| Surface | Location | What the UI uses it for |
|---|---|---|
| SSE event streams | `PipelineEventController` — `GET /api/pipelines/events` (global) and `/{id}/events`; emits `snapshot` then `execution-started`, `stage-started/completed/failed`, `execution-completed/failed`; tenant-scoped, per-tenant connection caps (429) | Live status overlay on the canvas — nodes light up as stages run, zero polling |
| Execution history | `PipelineExecutionTracker` (bounded in-memory, last N per pipeline) + `PipelineActuatorEndpoint` (`/actuator/pipelines`, `/{id}`, `/{id}/executions/{executionId}` with per-stage durations) | Run-history panel, per-stage timing display |
| Trigger API | `PipelineTriggerController` — `POST /api/pipelines/trigger` with **alias-based** body (operator-managed alias→id map in `jaiclaw.pipeline.http-trigger.allowed`; path-based trigger deliberately removed); `GET /status/{executionId}` returns a consumer-safe `StatusBody` | "Run now" button + test runs |
| Programmatic gateway | `PipelineGateway.trigger(pipelineId, input[, tenantId, correlationId])` → `PipelineExecutionHandle`; `PipelineSyncCoordinator` for trigger-and-wait | Backend for the UI's own (authenticated, non-alias) run endpoint |
| Server-side rendering | `PipelineRenderService` + `PipelineRenderController` — ASCII (compact/table/flow × 9 width profiles) and HTML (`div` or `svg`, flow view) | Embeddable read-only views; the SVG flow render is a fallback/preview even outside the SPA |
| MCP tools | `PipelineMcpToolProvider` — `pipeline_list`, `pipeline_trigger`, trigger-and-wait, status, render | Agent-driven parity with everything the UI does |
| Validation | `PipelineValidator` → `ValidationReport`/`ValidationError` — placeholder references, agentId/channelId/bean existence, dead-letter config; consolidated errors with Levenshtein "did you mean 'research'?" suggestions | The exact error engine a "Validate" button wants — it just isn't reachable per-draft over HTTP yet |
| Security | `PipelineSecurityGuard` (auth, tenant isolation, input validation, 1MB output cap, 32KB input cap in `PipelineContext.MAX_INPUT_BYTES`); controllers delegate authn/z to the Spring Security chain | Enforcement layer the authoring API must extend, not bypass |

**Bottom line:** the run/observe loop is done. The create/edit/deploy loop doesn't exist.

---

## 3. Gap analysis — what's missing for visual assembly

**G1 — No authoring API.** `PipelineRegistry` supports `register/get/getAll/getForTenant/contains/size` — no update, no delete, no persistence. Nothing authored at runtime survives a restart, and nothing can be authored at runtime at all.

**G2 — No hot deployment.** Routes are built once at startup. A UI whose "Deploy" button requires a JVM restart is dead on arrival. Camel itself supports dynamic `addRoutes`/`removeRoute`; what's needed is a `PipelineLifecycleManager` that adds, replaces, and removes a pipeline's route set safely (drain in-flight SEDA exchanges, handle Quartz trigger re-registration, fire audit events). Note: `PIPELINE_UX_IMPROVEMENTS.md` references a `PIPELINE_HOT_RELOAD.md` design doc — **that file does not exist in `docs/dev/`** — so this design work is genuinely unstarted, not just unimplemented.

**G3 — No catalog/discovery API.** The palette needs to know what can be dropped on the canvas: available processor beans, agents, channels, trigger/output types, Camel components, Embabel workflows. Today none of that is queryable: `PROCESSOR` beans are anonymous `Function<String, String>` beans found by name only; agents and channels have registries (`AgentRegistry`, `ChannelRegistry`) but no pipeline-facing REST projection.

**G4 — The processor SPI is too thin for visual configuration.** `BeanStageProcessor` invokes a `Function<String, String>` — no per-stage parameters, no description, no declared input/output contract. Every behavioral variation requires writing a new Java bean. A visual builder lives on *parameterized* nodes ("HTTP Fetch" with a URL field; "Regex Extract" with a pattern field). This is the single most important enabler for the baseline processor catalog in §6: a `ConfigurableStageProcessor` SPI (config map per stage + JSON Schema for the inspector form) and a `@PipelineProcessor(name, category, description, configSchema, icon)` metadata annotation.

**G5 — The engine is linear-only.** `stages` is an ordered list; `switch`/`parallel` stage types are specced in PIPELINE-STRATEGY.md §3.4 but remain Phase-2, unimplemented. Decision required: ship a v1 canvas that is honestly linear (a vertical flow — still visual, still valuable), or implement branching first. Recommendation: linear v1. It matches the engine, all three declarative pipeline examples in the repo (pipeline-e2e, sales-enrichment, support-triage) are linear, and React Flow makes the later DAG upgrade a frontend-schema change rather than a rewrite.

**G6 — No JSON Schema for `PipelineDefinition`, no per-draft validation endpoint.** Inspector forms, client-side checks, and import validation all want a published schema. And `PipelineValidator.validate()` runs registry-wide at boot; the UI needs `validate(PipelineDefinition draft)` exposed over REST, returning the existing `ValidationReport` (which is already UI-grade, suggestions included).

**G7 — No draft/version lifecycle.** A UI implies: draft (invalid allowed) → validated → deployed → disabled/retired, with edit-of-deployed producing a new draft, and storage that is tenant-scoped. Per the repo's own multi-tenancy conformance checklist, a draft store must use tenant-prefixed paths/keys — a single global `drafts.json` would fail JaiClaw's own PR checklist. Default: per-tenant JSON files (matching `JsonFileSubscriptionRepository`'s `.tmp`-and-rename pattern) with an SPI for Redis/JDBC; same single-replica caveat as the tracker.

**G8 — Authoring is a privilege escalation surface.** A `CAMEL` stage or `CAMEL_URI` trigger/output is an arbitrary Camel URI — `exec:`, `file:`, `http:` to internal networks. Hand-editing YAML on the server implies trust; typing URIs into a web UI does not. Needed: role separation (viewer / author / deployer / runner), a **URI-scheme allowlist** for UI-authored definitions in `PipelineSecurityProperties`, and server-side enforcement that UI-created pipelines can't grant themselves broader `security` overrides than the caller's role allows. Trigger note: the alias map (`http-trigger.allowed`) is operator-managed config — "deploy with HTTP trigger" from the UI needs a story for who updates the alias map (deployer-role API mutation, or UI-deployed pipelines get UI-triggered runs only until an operator adds an alias).

---

## 4. Target architecture

### 4.1 Module layout

```
extensions/
  jaiclaw-pipeline              (existing — engine, untouched surfaces reused)
  jaiclaw-pipeline-authoring    (NEW — draft store, catalog, validation API, lifecycle manager)
  jaiclaw-pipeline-processors   (NEW — baseline processor pack, §6)
apps/ or extensions/
  jaiclaw-pipeline-studio       (NEW — UI; static resources jar served by gateway at /studio)
jaiclaw-starters/
  jaiclaw-starter-pipeline-studio (NEW — auto-config: authoring API + processors + UI, one dependency)
```

`jaiclaw-pipeline-authoring` depends on `jaiclaw-pipeline`; the engine module never depends on authoring (same direction-of-dependency discipline the topics review flagged for `jaiclaw-messaging`).

### 4.2 Backend components (the build list)

| # | Component | Module | Notes |
|---|---|---|---|
| B1 | `PipelineDraftStore` SPI + JSON-file impl | authoring | Tenant-scoped per conformance checklist; versioned (monotonic revision per draft); Redis/JDBC impls later |
| B2 | `PipelineStudioController` — CRUD REST | authoring | `GET/POST/PUT/DELETE /api/pipeline-studio/drafts[/{id}]`; drafts may be invalid; optimistic-locking via revision |
| B3 | Validation endpoint | authoring | `POST /api/pipeline-studio/drafts/{id}/validate` (and `/validate` for anonymous payloads) → existing `ValidationReport` JSON; requires refactoring `PipelineValidator` to accept a single definition + a "context" (registry, ApplicationContext, ChannelRegistry) |
| B4 | `PipelineLifecycleManager` — hot deploy | authoring | `deploy(definition)` = register + build + `camelContext.addRoutes`; `undeploy(id)` = stop/remove routes + deregister; `redeploy` = drain + swap; fires audit + hook events; this is the riskiest component — needs the design work `PIPELINE_HOT_RELOAD.md` was supposed to hold (Quartz re-registration, SEDA drain semantics, in-flight executions) |
| B5 | Registry write support | pipeline (small change) | `PipelineRegistry.unregister(id)` / `replace(definition)`; keep engine module change minimal |
| B6 | `PipelineCatalogService` + endpoint | authoring | `GET /api/pipeline-studio/catalog` → trigger types, stage types, output types, error strategies, render profiles, **processors** (from B8 metadata), agents (`AgentRegistry`), channels (`ChannelRegistry`), Embabel workflows if present, curated Camel endpoint templates (§6 Tier 3) |
| B7 | JSON Schema publication | authoring | `GET /api/pipeline-studio/schema` — generated or hand-maintained schema for the per-file YAML/JSON definition format; drives inspector forms and import validation |
| B8 | `@PipelineProcessor` metadata + `ConfigurableStageProcessor` SPI | pipeline or processors | `process(input, config, context)` with `configSchema()`; `BeanStageProcessor` stays for bare `Function` beans (shown in palette as "custom bean" nodes with name only) |
| B9 | YAML round-trip | authoring | `GET /drafts/{id}/yaml` (export in per-file format) + `POST /import` (YAML → draft via `PipelineYamlParser`); makes UI-authored pipelines git-able and hand-authored pipelines UI-editable |
| B10 | Test-run endpoint | authoring | `POST /drafts/{id}/test-run` — deploy to a sandboxed id (`__draft__{id}`), MANUAL trigger, forced `LOG` output (or captured output returned in-band via `PipelineSyncCoordinator`), auto-undeploy after run; author-role only |
| B11 | AuthZ + URI allowlist | authoring + pipeline security | Roles viewer/author/deployer/runner mapped onto the Spring Security chain; `PipelineSecurityProperties` gains `allowedUriSchemes` enforced by validator for UI-origin definitions |
| B12 | Template-variable endpoint | authoring | `GET /drafts/{id}/variables?stage=X` — computed from stage order (mirrors `PipelineContext.availableVariables()`) for placeholder autocomplete |

### 4.3 What is explicitly reused, not rebuilt

SSE streams (live canvas overlay), `PipelineExecutionTracker`/actuator (history panel), `PipelineGateway` (runs), `PipelineRenderService` SVG (read-only embeds and a no-JS fallback), `PipelineValidator` (all validation logic), `TemplateResolver` semantics (autocomplete), per-file YAML (persistence format), MCP tools (agent parity — worth adding `pipeline_validate` and `pipeline_deploy` MCP tools so agents can assemble pipelines through the same authoring plane the UI uses; profile-gate the deploy tool).

---

## 5. Frontend analysis — both options

### 5.1 Option A: React SPA (React Flow)

A React + TypeScript app using React Flow (now "xyflow") for the canvas, built with Vite, packaged into `jaiclaw-pipeline-studio` as static resources via `frontend-maven-plugin`, served by the gateway at `/studio` (same-origin: no CORS, session/JWT auth reused).

*For:* React Flow is the de-facto standard for node editors (n8n, Langflow, Flowise all sit on it) — drag-drop, connection validation, minimap, auto-layout come nearly free. Schema-driven inspector forms via `@rjsf` (react-jsonschema-form) consume B7 directly. `EventSource` → live node status is trivial. Branching (G5) becomes a schema upgrade, not a rewrite. And it matches the existing skill set — the `taptech-react-ui` / `taptech-creators-ui` / `taptech-calendar-ui` repos mean this stack is already in-house.

*Against:* a Node toolchain enters the build (mitigated: `frontend-maven-plugin` pins Node locally; CI needs no global install); bundle adds a few MB to the gateway image; two codebases to version together (mitigated: the JSON Schema + catalog contract is the seam — UI and API can evolve independently against it).

### 5.2 Option B: Server-rendered (Thymeleaf/htmx)

Templates in the gateway app, htmx for partial updates, building on `PipelineHtmlRenderer`'s existing div/SVG output; editing via forms rather than a true canvas.

*For:* one artifact, zero JS toolchain, tiny footprint; the read-only dashboard half is nearly free today (render endpoints + SSE with an htmx SSE extension); fine for a linear pipeline presented as an editable vertical list ("form-first" rather than "canvas-first").

*Against:* true drag-and-drop graph editing, connection dragging, inline template autocomplete, and live per-node overlays are exactly the interactions server rendering is worst at — each becomes bespoke JS anyway, converging on a worse SPA. The moment `switch`/`parallel` lands, a list-based editor hits its ceiling and the UI gets rewritten. Server-side HTML also couples UI iteration to gateway releases.

### 5.3 Recommendation

**React Flow SPA (Option A), with Option B's cheapest piece taken anyway.** Ship the SPA for authoring; separately, a near-zero-cost read-only "pipeline dashboard" page (htmx or even static JS + the existing SSE/render endpoints) can land immediately — before any authoring work — and delivers visible progress while B1–B11 are built. The two don't compete: one is a monitor, the other is the builder.

### 5.4 Frontend component inventory (SPA)

| Component | Consumes | Notes |
|---|---|---|
| Canvas (React Flow) | draft JSON | v1: vertical linear flow, trigger node pinned top, output pinned bottom; stage insertion between edges; reorder by drag |
| Palette | B6 catalog | Grouped: Triggers / AI (agent presets) / Processors (by category) / Integrations (Camel templates) / Output; search; drag-to-insert |
| Inspector | B7 schema + B6 per-processor `configSchema` | Schema-driven forms; stage-type-specific panels (AGENT: agent picker + prompt editor; PROCESSOR: config form; CAMEL: URI builder with allowlisted schemes) |
| Prompt/template editor | B12 variables | `{{` autocomplete of upstream stage outputs, `input`, `pipeline.*`; unresolved-placeholder squiggles mirroring validator rules |
| Validation panel | B3 | `ValidationReport` rendered with click-to-focus-node; "did you mean" suggestions surfaced as one-click fixes |
| YAML view | B9 | Two-way toggle canvas ↔ YAML; export/download; import |
| Run panel | B10, `PipelineGateway` endpoint, SSE | Input box, test-run button, live stage status on canvas nodes, per-stage output inspection from tracker detail |
| History panel | actuator/tracker | Recent executions, durations per stage, failure reasons; click to replay overlay |
| Deploy toolbar | B4 | Validate → Deploy / Undeploy / Redeploy with role gating; deployed-vs-draft diff |
| Tenant switcher | `getForTenant` semantics | Only in MULTI mode; scopes drafts, catalog (agents/channels), and streams |

---

## 6. Baseline processor catalog

The catalog is what makes an empty canvas useful. Principle: **every palette node is one of four kinds** — a configurable processor (new SPI, B8), an agent preset (an `AGENT` stage with a canned prompt + config), a Camel template (a `CAMEL` stage with a parameterized, allowlisted URI), or a custom bean (existing `Function<String,String>` bean, name-only). Nothing here requires new stage *types* in the engine.

### Tier 1 — Core processors (`jaiclaw-pipeline-processors`, new code, ~15 nodes)

| Node | Category | Config | Impl notes |
|---|---|---|---|
| Template / Format | Transform | template string | Thin wrapper over `TemplateResolver` — makes templating a first-class node instead of prompt-only |
| Regex Extract | Transform | pattern, group, all-matches? | |
| Regex Replace | Transform | pattern, replacement | |
| JSON Path Extract | Transform | JSONPath expr, default | Jackson already on classpath |
| JSON Validate | Validate | JSON Schema | Fails stage (routing to error strategy) on mismatch — the guardrail node for LLM-JSON handoffs |
| JSON ↔ CSV | Transform | direction, delimiter, headers? | |
| XML → JSON | Transform | — | |
| HTML → Text | Transform | — | Reuse `jaiclaw-documents` parsing |
| Markdown → HTML | Transform | — | Reuse `HtmlEscaper`/render utilities where sensible |
| Trim / Case / Truncate | Transform | op, max-length | The upperCase/addExclaim demo beans, productized |
| Chunk / Split | Transform | max-size, overlap | Reuse `jaiclaw-documents` chunking pipeline |
| Filter Gate | Control | predicate (regex / JSONPath / contains), on-fail: stop-silently vs error | "Stop the pipeline unless X" — escalationGate from support-triage, generalized |
| Set Metadata | Control | key, value-template | Writes `PipelineContext` metadata for downstream `{{stages.X.metadata.k}}` |
| HTTP Fetch | Data | url-template, method, headers, timeout | Guarded by URI allowlist (G8); distinct from raw CAMEL for safety |
| File Read / File Write | Data | path-template | Tenant-prefixed paths in MULTI mode per conformance checklist |

### Tier 2 — AI presets (config only, no new code — canned `AGENT` stages)

Summarize (style/length config), Classify (label list config — the routing companion to Filter Gate), Extract-to-JSON (target-schema config, pairs with JSON Validate), Translate (target language), Sentiment, Redact PII, Draft Reply (tone config). Each preset = name + description + prompt template + config-to-prompt substitution + recommended timeout. Stored as catalog data (YAML resources in the processors module), not Java — trivially extensible by users, and they exercise the exact pattern the existing declarative examples use by hand (sales-enrichment's enrich/score/draft stages are all this shape).

### Tier 3 — Integration nodes (curated Camel templates + one new processor)

- **Camel templates** (parameterized `CAMEL` stages with allowlisted schemes): Send Email (SMTP), Kafka Publish, HTTP POST (webhook), JDBC Query, S3/File archive, Log. Each template = display name + URI pattern + config fields → URI interpolation. This makes Camel's 300+ components consumable without exposing raw URI typing to authors.
- **Tool Invoke** (`ToolStageProcessor`, small new processor in the engine or processors module): run any tool from `ToolRegistry` as a stage (config: tool name + JSON args template). This is high-leverage — it makes every existing JaiClaw tool (and every MCP tool surfaced through the registry) a pipeline node for free, with `ToolProfile` gating respected.
- **Memory Upsert / Memory Search** (`jaiclaw-memory` vector store) — the two nodes that turn "document pipeline" into "RAG ingestion pipeline."
- **Document Parse** (`jaiclaw-documents`: PDF/HTML/text → text) — the front door for file-triggered pipelines.

### Explicitly not in baseline

`switch`/`parallel` (G5 — engine first), `batch-chunk` (Spring Batch stage remains unimplemented; palette can grey-list it), federation transports (Phase 3), and channel *inbound* nodes (pipelines are triggered, not chat-driven; the gateway owns conversational routing).

---

## 7. Phased roadmap

| Phase | Contents | Effort (focused) |
|---|---|---|
| 0 — Read-only dashboard | Static page on existing SSE + render + actuator; no new backend | 2–3 days |
| 1 — Authoring plane | B1–B3, B5–B7, B9, B12 (draft CRUD, catalog, schema, validate, YAML round-trip); `@PipelineProcessor` + `ConfigurableStageProcessor` SPI (B8) | 1.5–2 wks |
| 2 — Studio SPA v1 | Canvas/palette/inspector/validation/YAML/history against Phase-1 API; deploy still = "export YAML + restart" (honest interim) | 2–3 wks |
| 3 — Hot deploy + runs | B4 lifecycle manager (write the missing `PIPELINE_HOT_RELOAD.md` first), B10 test-run, B11 authz + URI allowlist, deploy toolbar + run panel | 1.5–2 wks |
| 4 — Processor pack | Tier 1 + Tier 3 processors, Tier 2 preset library, palette metadata | 1–1.5 wks (parallelizable with 2–3) |
| Later | `switch`/`parallel` in engine → DAG canvas; draft-store Redis impl; versioned deploy history/rollback; MCP authoring tools | — |

Sequencing note: Phase 1's API contract (schema + catalog) is the seam — freeze it early and frontend/backend proceed in parallel. Phase 3 (hot deploy) is the highest-risk item and deliberately sits *after* the UI proves value with restart-deploys.

## 8. Risks and open questions

Hot-reload semantics are the main engineering risk (in-flight SEDA drains, Quartz trigger re-registration, replace-under-load) — the referenced design doc doesn't exist yet and should be written before Phase 3, not during. Security posture changes qualitatively when authoring moves from server-filesystem YAML to a web form: the URI-scheme allowlist and role split (B11) are not optional hardening, they're prerequisites for exposing deploy at all — the same lesson as S2/S3 in the topics review. The draft store and tracker are in-memory/single-file today, so the k8s multi-replica story needs the same loud "single-replica default, SPI for Redis" documentation the topics review demanded of subscriptions. And the alias-based trigger design (deliberately opaque, operator-managed) needs an explicit decision about whether UI deployment may mutate the alias map or UI-deployed pipelines stay UI/manual-triggered until an operator intervenes.

One product-level note: the visual builder completes the same sellable story the topics review identified — **cron triggers → pipeline computes → topic fans out** — and it is the piece that makes that story *demoable in a browser*, which matters for the jaiclaw.io tutorial/product funnel already in motion.
