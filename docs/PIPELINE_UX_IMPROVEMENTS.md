# Pipeline UX Improvements

Backlog of user-experience improvements for the `jaiclaw-pipeline` module, prioritized for implementation.

---

## 1. Startup Validation with Helpful Messages

**Problem:** Template references to non-existent stages (`{{stages.typo.output}}`), missing `agentId`, unknown `channelId`, and missing Spring beans for `PROCESSOR` stages all fail at runtime — often mid-execution — with cryptic errors.

**Implementation:**
- Create `PipelineValidator` service invoked from `PipelineAutoConfiguration` after all pipelines are registered
- Checks to perform:
  - All `{{stages.X.output}}` and `{{stages.X.metadata.key}}` placeholders reference a stage name defined in the same pipeline
  - `agentId` exists in `AgentRegistry` (if bean present)
  - `channelId` exists in `ChannelRegistry` (if output type is `CHANNEL`)
  - `bean` name exists in Spring `ApplicationContext` (for `PROCESSOR` stage type)
  - If `errorStrategy == DEAD_LETTER` and `deadLetterUri` is null → fail with clear message (see item 6)
- Emit a single consolidated error listing all violations rather than failing on the first
- Include a "did you mean X?" suggestion for near-miss stage name references (Levenshtein distance ≤ 2)

**Example error:**
```
Pipeline 'content-pipeline' failed validation:
  - stage 'write': systemPrompt references unknown stage 'resarch' — did you mean 'research'?
  - output: channelId 'slakk' not found in ChannelRegistry — registered channels: [slack, teams]
```

**Files to create/modify:**
- New: `PipelineValidator.java`
- Modify: `PipelineAutoConfiguration.java` — invoke validator after registration

---

## 2. Pipeline Execution Tracking + Actuator Endpoint

**Problem:** No visibility into what pipelines are running, what ran recently, or what happened during a specific execution. Especially painful for CRON and FILE triggers where there is no synchronous caller.

**Implementation:**
- Create `PipelineExecutionTracker` — in-memory store of recent executions
  - Bounded per pipeline: last 50 executions (configurable via `jaiclaw.pipeline.tracker.max-per-pipeline`)
  - Per execution: `executionId`, `pipelineId`, `tenantId`, `startedAt`, `completedAt`, `status` (RUNNING/SUCCESS/FAILED), per-stage duration map, failure reason
  - Thread-safe (`ConcurrentHashMap` + bounded `ArrayDeque`)
- Wire `PipelineRouteBuilder` to publish lifecycle events to `PipelineExecutionTracker`
- Create `PipelineActuatorEndpoint` (`@Endpoint(id = "pipelines")`)
  - `GET /actuator/pipelines` — list all registered pipelines with trigger type and enabled status
  - `GET /actuator/pipelines/{id}` — definition + last N executions
  - `GET /actuator/pipelines/{id}/executions/{executionId}` — full execution detail with per-stage breakdown

**Files to create/modify:**
- New: `PipelineExecutionTracker.java`
- New: `PipelineActuatorEndpoint.java`
- New: `PipelineExecutionSummary.java` (record)
- Modify: `PipelineRouteBuilder.java` — publish to tracker at stage start/complete/fail
- Modify: `PipelineAutoConfiguration.java` — register tracker and endpoint beans
- Modify: `PipelineProperties.java` — add `tracker.maxPerPipeline` config

---

## 3. `PipelineGateway` Bean + HTTP Trigger Endpoint

**Problem:** Triggering a `MANUAL` pipeline requires knowing the internal Camel URI (`direct:pipeline-{id}`) and calling `ProducerTemplate` directly. This is low-level, undiscoverable, and leaks implementation details.

**Implementation:**
- Create `PipelineGateway` interface and `DefaultPipelineGateway` implementation:
  ```java
  public interface PipelineGateway {
      PipelineExecutionHandle trigger(String pipelineId, String input);
      PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId);
      PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId, String correlationId);
  }
  ```
- `PipelineExecutionHandle` record: `executionId`, `pipelineId`, `startedAt`
- `DefaultPipelineGateway` wraps `ProducerTemplate`, looks up pipeline in `PipelineRegistry`, validates it exists and is enabled before sending
- Expose HTTP endpoint at `POST /api/pipelines/{id}/trigger` (request body = plain text input, returns `PipelineExecutionHandle` as JSON)
  - Only registered if `jaiclaw.pipeline.http-trigger.enabled=true` (default: true)
  - Respects security guard (authentication + tenant isolation) before dispatching

**Files to create/modify:**
- New: `PipelineGateway.java` (interface)
- New: `DefaultPipelineGateway.java`
- New: `PipelineExecutionHandle.java` (record)
- New: `PipelineTriggerController.java` (Spring MVC `@RestController`)
- Modify: `PipelineAutoConfiguration.java` — register gateway and conditionally register controller
- Modify: `PipelineProperties.java` — add `http-trigger.enabled` flag

---

## 4. Template Variable Discoverability + Warn on Missing

**Problem:** Available template variables are undocumented and undiscoverable. Typos in placeholder names silently produce empty strings. `{{input}}` availability is unclear.

**Implementation:**
- Formalize `{{input}}` as a first-class variable in `TemplateResolver` representing the original trigger payload
- Add standard pipeline-level variables: `{{pipeline.id}}`, `{{pipeline.executionId}}`, `{{pipeline.tenantId}}`
- Change `TemplateResolver` behavior on unresolved placeholder: log `WARN` with list of available variables at that point in execution
  ```
  WARN  TemplateResolver - Unresolved placeholder '{{stages.resarch.output}}' in pipeline 'content-pipeline' stage 'write'.
        Available stage outputs: [research, summarize]
        Available pipeline vars: [pipeline.id, pipeline.executionId, pipeline.tenantId, input]
  ```
- Do NOT throw — pipelines should continue with empty string substitution as today
- Add `PipelineContext.availableVariables()` helper that returns the full variable map for a given execution state (useful for debugging and the warning above)

**Files to modify:**
- `TemplateResolver.java` — add `{{input}}`, `{{pipeline.*}}` vars; add warn-on-miss logging
- `PipelineContext.java` — add `availableVariables()` method
- `PipelineRouteBuilder.java` — pass original input payload into context so `{{input}}` resolves

---

## 5. DSL `.then()` Stage Chaining

**Problem:** Multi-stage pipelines using the Java DSL require `.back()` calls to navigate back to the pipeline level between stages, which is verbose and confusing.

**Current:**
```java
pipeline("my-pipe")
    .trigger().http("/api/run").back()
    .stage("research").agent("researcher").systemPrompt("...").back()
    .stage("write").agent("writer").systemPrompt("...").back()
    .output().channel("slack");
```

**Target:**
```java
pipeline("my-pipe")
    .trigger().http("/api/run")
    .then("research").agent("researcher").systemPrompt("...")
    .then("write").agent("writer").systemPrompt("{{stages.research.output}}")
    .output().channel("slack");
```

**Implementation:**
- Add `.then(String stageName)` method to `StageBuilder` that finalizes the current stage and starts the next — equivalent to `.back().stage(stageName)`
- Add `.then(String stageName)` to `TriggerBuilder` as well so the first stage can be chained directly from the trigger
- Keep `.back()` and `.stage()` working unchanged for backwards compatibility
- Add `.output()` directly on `StageBuilder` (equivalent to `.back().output()`) to complete the chain cleanly

**Files to modify:**
- `StageBuilder.java` — add `then()`, `output()` shorthand methods
- `TriggerBuilder.java` — add `then()` shorthand method

---

## 6. Dead-Letter URI Guard + Global Default

**Problem:** Setting `errorStrategy: DEAD_LETTER` without a `deadLetterUri` produces a confusing Camel error at route startup. No global default means every pipeline must repeat the URI.

**Implementation:**
- In `PipelineValidator` (item 1): if any pipeline has `errorStrategy == DEAD_LETTER` and no `deadLetterUri`, and no `defaults.deadLetterUri` is set → fail startup with clear message
- Add `deadLetterUri` to `PipelineProperties.PipelineDefaults`
- In `PipelineRouteBuilder`: if pipeline's `deadLetterUri` is null, fall back to `defaults.deadLetterUri`
- Surface dead-letter queue stats in the Actuator endpoint (item 2): count of messages sent to dead-letter per pipeline

**Example config:**
```yaml
jaiclaw:
  pipeline:
    defaults:
      deadLetterUri: "log:pipeline-dead-letter?level=ERROR"
```

**Files to modify:**
- `PipelineValidator.java` (new, from item 1) — add dead-letter check
- `PipelineProperties.java` — add `defaults.deadLetterUri`
- `PipelineRouteBuilder.java` — fall back to default dead-letter URI

---

## Implementation Order

| Priority | Item | Effort | Why First |
|----------|------|--------|-----------|
| 1 | Startup Validation (#1) | Low | Prevents silent failures; foundation for item 6 |
| 2 | Dead-Letter Guard + Default (#6) | Low | Quick win, part of validation pass |
| 3 | `PipelineGateway` + HTTP trigger (#3) | Medium | Biggest friction point for new users |
| 4 | Template discoverability (#4) | Low | Easy win, improves debuggability |
| 5 | DSL `.then()` chaining (#5) | Low | Polish; backwards-compatible |
| 6 | Execution tracking + Actuator (#2) | Medium | Operational visibility; depends on nothing above |

---

## Implementation Status

All six items above are implemented and unit-tested. Additional UX surfaces
landed in follow-up work and are tracked here so this document stays the
canonical UX index:

- **CRON via quartz.** `PipelineRouteBuilder.resolveTriggerUri()` emits
  `quartz://jaiclaw-pipelines/<id>?cron=<encoded>` for `TriggerType.CRON`.
  Optional Camel `camel-quartz-starter` dependency. Real cron expressions
  (e.g. `0 0 7 ? * MON-FRI`) are honored.
- **Hyphenated stage names.** `TemplateResolver` and
  `validation.PlaceholderScanner` regexes accept `[\w-]+` so stages like
  `classify-and-sentiment` resolve in `{{stages.X.output}}` templates.
- **Module gate.** New `jaiclaw.pipeline.enabled` flag (default `false`)
  makes the module opt-in. When `enabled=true`, startup fails fast if no
  pipeline source is configured (`pipelines[]`, `locations.patterns[]`, or
  a `JaiClawPipeline` code bean).
- **Per-file YAML loader.** New `jaiclaw.pipeline.locations.patterns[]`
  configures Spring resource-pattern globs (`classpath*:...`, `file:...`).
  Each matching file is one `PipelineDefinition`; the filename stem
  supplies a fallback `id`. Code beans still override per-file YAML which
  in turn overrides inline YAML on id conflict. Hot reload is **not** in
  scope — see [`PIPELINE_HOT_RELOAD.md`](./PIPELINE_HOT_RELOAD.md) for the
  future-feature design.
