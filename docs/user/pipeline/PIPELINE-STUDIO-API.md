# Pipeline Studio Authoring API

The Pipeline Studio's authoring plane exposes the REST surface any UI
(or CLI, or MCP tool) needs to author, validate, import, and export
`PipelineDefinition` drafts. Every endpoint lives under
`/api/pipeline-studio/*` and is served by the new
`jaiclaw-pipeline-authoring` extension.

Phase 1 of the [Pipeline Studio buildout](../../dev/pipeline/PIPELINE-STUDIO-ANALYSIS.md).
Backend-only — the React Flow SPA that consumes this API ships in
Phase 2, and hot deploy + test-run + role-based authz land in Phase 3.

For agent-driven authoring against these endpoints, see the
`pipeline-author` blueprint (fetch via `blueprints_get` on
`/mcp/blueprints`) and the matching Claude Code skill at
`.claude/skills/pipeline-author/SKILL.md`.

---

## Install

Add the dependency alongside `jaiclaw-pipeline`:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-pipeline-authoring</artifactId>
</dependency>
```

Not pulled by `jaiclaw-spring-boot-starter`. Opt-in via explicit dep +
enable via property:

```yaml
jaiclaw:
  pipeline:
    authoring:
      enabled: true                          # opt-in — default is false
      storage-path: ${user.home}/.jaiclaw/pipeline-drafts   # optional
```

The `enabled` gate is deliberately opt-in — not `matchIfMissing`.
Authoring endpoints are unauthenticated in Phase 1; adopters must
front them with their own Spring Security chain until Phase 3 lands
role-based method annotations.

---

## Endpoints

Every path is prefixed with `/api/pipeline-studio`.

### Draft CRUD

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/drafts` | List drafts visible to the current tenant. |
| `GET` | `/drafts/{id}` | Fetch a single draft. `404` when missing. |
| `POST` | `/drafts` | Create a fresh draft. Body: JSON `PipelineDefinition`. `409` if id already exists. Returns `201 Created` with `ETag: "1"` and the persisted draft. |
| `PUT` | `/drafts/{id}` | Update a draft. Requires `If-Match: "{revision}"` header for optimistic locking. `409 Conflict` on revision mismatch (response body includes `expected` + `actual` for merge). Returns updated draft with new ETag. |
| `DELETE` | `/drafts/{id}` | Delete a draft. `204 No Content`. |

Drafts may be invalid — the store accepts everything with a
non-blank `id`. Use the validate endpoints to check correctness.

### Validation

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/drafts/{id}/validate` | Validate the stored draft. `404` if missing. |
| `POST` | `/validate` | Validate an anonymous JSON body — for the UI's live-edit-as-you-type. |

Response shape:

```json
{
  "hasErrors": true,
  "formatted": "Pipeline 'renewal-reminder' has 2 errors: ...",
  "errors": [
    { "pipelineId": "renewal-reminder",
      "location": "stage 'find-account'",
      "code": "UNKNOWN_BEAN",
      "message": "PROCESSOR bean 'findAccount' not found in ApplicationContext",
      "suggestion": "findAccountService" }
  ]
}
```

Validation checks (via `PipelineValidator`):

- Every `{{stages.X.output}}` / `{{stages.X.metadata.k}}` placeholder
  references a stage defined in the same pipeline (with "did you
  mean 'X'?" Levenshtein suggestions).
- Every PROCESSOR stage's `bean` name exists in the Spring context and
  implements either `Function<String,String>` or
  `ConfigurableStageProcessor` (**new in 1.0.0**).
- CHANNEL output `channelId` resolves in `ChannelRegistry`.
- `errorStrategy=DEAD_LETTER` requires a `deadLetterUri`.
- EMBABEL AGENT stages require an `AgentOrchestrationPort` bean +
  a valid workflow id.

### Catalog

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/catalog` | Palette + inspector metadata for every node the UI can drop on the canvas. |

Response shape:

```json
{
  "triggerTypes": ["MANUAL", "HTTP", "FILE", "CRON", "CAMEL_URI"],
  "stageTypes":   ["AGENT", "PROCESSOR", "CAMEL"],
  "outputTypes":  ["CHANNEL", "CAMEL_URI", "LOG", "NONE"],
  "errorStrategies": ["STOP", "RETRY_THEN_FAIL", "DEAD_LETTER"],
  "processors": [
    { "beanName": "regexExtractProcessor",
      "name": "Regex Extract",
      "category": "Transform",
      "description": "Extract a group from the input via a regex",
      "icon": "regex",
      "configSchema": "{...json schema...}" }
  ],
  "customBeans": ["upperCase", "addExclaim"],
  "channels": ["telegram", "slack"],
  "cameltemplates": []
}
```

- **`processors`** — every Spring bean carrying
  `@PipelineProcessor` AND implementing `ConfigurableStageProcessor`.
  Each entry gets the full metadata + config schema.
- **`customBeans`** — bare `Function<String,String>` bean names.
  Palette shows these as "custom bean" nodes with name only (no
  inspector form).
- **`channels`** — ids from `ChannelRegistry`, when the bean is
  present.
- **`cameltemplates`** — populated in Phase 4 (empty array today).

### JSON Schema

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/schema` | Hand-maintained JSON Schema for `PipelineDefinition`. Drives the inspector's schema-driven forms. |

The schema (a Draft-07 document at `META-INF/jaiclaw-pipeline-schema.json`
inside the authoring jar) is served verbatim. Keep it in sync with
the engine's record shape when adding new fields.

### YAML round-trip

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/drafts/{id}/yaml` | Export a draft as per-file YAML (`application/x-yaml`). |
| `POST` | `/import` | Accept a YAML body (`application/x-yaml`, `text/yaml`, or `text/plain`) and create a new draft. Optional `?id=my-draft` query param when the YAML has no `id:`. |

Round-trip is a wrapper over Jackson's `YAMLMapper` — the format
matches per-file YAML loaded by `PipelineFileLoader`, so a draft
exported here can be dropped straight into a
`jaiclaw.pipeline.locations.patterns[]` classpath location and
loaded at boot.

### Template variables

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/drafts/{id}/variables?stage=X` | List every placeholder resolvable at stage `X`'s execution point. |

Response:

```json
{
  "stage": "second",
  "variables": {
    "pipeline.id":            "renewal-reminder",
    "pipeline.executionId":   "…",
    "pipeline.tenantId":      "",
    "pipeline.correlationId": "",
    "input":                  "",
    "stages.first.output":    ""
  }
}
```

Values are empty (Studio only needs the set of resolvable placeholder
names, not their runtime values). This drives the `{{`-autocomplete
in the UI's prompt/template editor.

---

## Draft lifecycle

Every draft is a `PipelineDraft` record with these fields:

| Field | Type | Notes |
|---|---|---|
| `id` | string | Matches `PipelineDefinition.id`. |
| `revision` | long | Starts at 1, bumped on every save. Used for optimistic locking. |
| `definition` | PipelineDefinition | The pipeline being edited. |
| `tenantId` | string | Set on save from `TenantContextHolder`. Null in SINGLE mode. |
| `status` | enum | `DRAFT | VALIDATED | DEPLOYED | DISABLED`. |
| `origin` | enum | `STUDIO | YAML_IMPORT | CODE_BEAN`. Phase 3's URI-scheme allowlist gates only UI-origin drafts. |
| `lastModifiedAt` | Instant | Updated on every save. |

Drafts are persisted by the `PipelineDraftStore` SPI. The default
`JsonFilePipelineDraftStore` writes one JSON file per draft at
`{storage-path}/{tenantId}/{id}.json` (tenant segment empty in
SINGLE mode). Writes use the `.tmp`-and-atomic-move pattern so a
partial write never appears on disk.

Adopters can plug in their own store — Redis, JDBC, cloud blob — by
providing a `@Bean PipelineDraftStore` bean. The autoconfig respects
`@ConditionalOnMissingBean`.

---

## Phase 3 — hot deploy + test-run + authz

Shipped in 1.0.0. See
[`docs/dev/pipeline/PIPELINE_HOT_RELOAD.md`](../../dev/pipeline/PIPELINE_HOT_RELOAD.md)
for the runtime semantics.

### Deploy / undeploy / redeploy

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/drafts/{id}/deploy` | Deploy the stored draft to the live Camel context. Validation runs first — 400 with a `ValidationReport` body on failure. 409 when the id is already deployed (use redeploy). 200 on success. |
| `POST` | `/drafts/{id}/undeploy` | Drain in-flight executions, remove `pipeline-{id}-*` routes, unregister. 404 when not deployed. |
| `POST` | `/drafts/{id}/redeploy` | Undeploy + deploy. Body optional — omitted body redeploys the stored draft as-is; a JSON body deploys that definition instead (id must match). |

Success response:

```json
{ "pipelineId": "renewal-reminder", "status": "DEPLOYED", "stageCount": 3 }
```

Every state change fires `PipelineDeployedEvent` /
`PipelineUndeployedEvent` on the Spring event bus AND
through `HookRunner.fireVoid(...)` — plug into either to react.

### Test-run

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/drafts/{id}/test-run` | Deploy the draft to a sandboxed id (`__draft__{id}__{uuid8}`), submit the caller's input as a MANUAL trigger, wait for completion, undeploy. Trigger + output forced to `MANUAL` / `LOG` regardless of the draft's real config. |

Request:

```json
{ "input": "hello", "timeoutSeconds": 30 }
```

Response:

```json
{
  "executionId": "…",
  "status": "SUCCESS",
  "totalDurationMs": 42,
  "stageOutputs": { "s1": "…", "s2": "…" },
  "failureReason": "",
  "startedAt": "2026-07-21T…"
}
```

### Authz (role-based)

Every Phase 3 endpoint carries a `@PreAuthorize` annotation referencing
the following role names (configurable):

```yaml
jaiclaw:
  pipeline:
    authoring:
      roles:
        viewer:   ROLE_PIPELINE_VIEWER
        author:   ROLE_PIPELINE_AUTHOR
        deployer: ROLE_PIPELINE_DEPLOYER
        runner:   ROLE_PIPELINE_RUNNER
```

| Endpoint | Required authority |
|---|---|
| `POST /drafts/{id}/deploy` | `deployer` |
| `POST /drafts/{id}/undeploy` | `deployer` |
| `POST /drafts/{id}/redeploy` | `deployer` |
| `POST /drafts/{id}/test-run` | `author` |

Adopters without Spring Security on the classpath: the annotations
are inert — every endpoint reaches the handler. Adopters with
Spring Security but no role config: any authenticated principal may
invoke every endpoint (the check short-circuits when the mapped
authority string is blank).

### URI-scheme allowlist

Enforced only on UI-origin drafts (`origin=STUDIO`). YAML-imported
drafts and code beans bypass the check — an operator hand-editing
YAML is already trusted with arbitrary Camel URIs.

```yaml
jaiclaw:
  pipeline:
    authoring:
      security:
        allowed-uri-schemes: [direct, seda, log, vm, timer, quartz]
```

Default: `direct, seda, log, vm, timer, quartz` — safe (no network,
no shell, no filesystem). Add schemes for your deployment as needed
(e.g. `smtp, kafka, jdbc` in an internal-only tenant). An empty
list disables the check.

Validation errors carry `code=URI_SCHEME_DENIED` with a message
listing the current allowlist.

### MCP tools

Two MCP tools ship at `/mcp/pipeline-authoring`:

- **`pipeline_validate`** — always enabled. Validates an inline
  `PipelineDefinition` JSON. Safe — never mutates registry or draft
  store.
- **`pipeline_deploy`** — opt-in via
  `jaiclaw.pipeline.authoring.mcp.deploy-enabled=true` (default
  `false`). Deploys a stored draft. Privilege-escalation surface —
  enable only in trusted deployments.

## Deferred to Phase 4

- `cameltemplates` catalog population (curated Camel-URI templates).
- Baseline processor pack — the 15+ `@PipelineProcessor` beans the
  palette needs to be useful on day one.
