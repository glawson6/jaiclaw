# Pipeline Studio

A visual builder for JaiClaw pipelines: drag-drop palette + linear
canvas (React Flow) + schema-driven inspector + validation panel +
two-way YAML view. Ships as a jar of static resources served by any
Boot app on `/studio`.

Phase 2 of the [Pipeline Studio buildout](../../dev/pipeline/PIPELINE-STUDIO-ANALYSIS.md).
Consumes the [Phase 1 authoring API](PIPELINE-STUDIO-API.md).

---

## Install

Two deps (Studio SPA + authoring backend):

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-pipeline-studio</artifactId>
</dependency>
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-pipeline-authoring</artifactId>
</dependency>
```

Both are opt-in — not pulled by `jaiclaw-spring-boot-starter`.

Enable the authoring API:

```yaml
jaiclaw:
  pipeline:
    authoring:
      enabled: true
```

The SPA autoconfig activates automatically when the jar is on the
classpath (opt out with `jaiclaw.pipeline.studio.spa.enabled=false`).

Open `http://<host>:<port>/studio` in a browser.

---

## Layout

Three panes:

```
┌──────────┬──────────────────────────────┬──────────┐
│ Palette  │  Canvas                      │ Inspector│
│          │                              │          │
│ - Agent  │      ┌─────────┐             │ (stage-  │
│ - Camel  │      │ Trigger │             │  or      │
│ - Custom │      └────┬────┘             │  pipe-   │
│          │           ▼                  │  line-   │
│ - Regex  │      ┌─────────┐             │  level   │
│   Extract│      │ stage-1 │             │  form)   │
│ - JSON   │      └────┬────┘             │          │
│   Path   │           ▼                  │          │
│          │      ┌─────────┐             │          │
│          │      │ Output  │             │          │
│          │      └─────────┘             │          │
└──────────┴──────────────────────────────┴──────────┘
```

- **Palette** — drag-drop nodes (Phase 2 ships click-to-append instead
  of drag; drag lands in a follow-up). Grouped by category, search
  filter. Built-in generic Agent / Camel / Custom-Bean entries always
  present; `@PipelineProcessor`-annotated beans from the catalog fill
  in the rest.
- **Canvas** — linear vertical flow via React Flow. Trigger pinned
  top, output pinned bottom. Click any stage to inspect it. When
  engine branching (`switch`/`parallel`) lands, this becomes a true
  DAG canvas — the frontend switch is a schema upgrade, not a
  rewrite.
- **Inspector** — schema-driven forms via `@rjsf`. When a
  `PROCESSOR` stage's bean carries a `configSchema()`, the inspector
  renders the config form from that schema. Pipeline-level pane shows
  when no stage is selected.

Top-right toolbar: **YAML** (toggle to two-way editor) · **Validate**
(POST to `/api/pipeline-studio/validate`) · **Save**.

---

## Editing model

- Every mutation flows through a client-side `useDraftStore` hook —
  no round-trips on typing. **Save** posts the whole definition to
  the authoring backend.
- Save uses optimistic locking. The store remembers the last-known
  revision; a `PUT` with a stale `If-Match` returns `409` and the UI
  surfaces the merge conflict (Phase 2 shows the raw error; a
  three-way merge lands in a follow-up).
- **Dirty state** is tracked and shown in the header (`unsaved
  changes` vs `clean`). Refresh discards unsaved edits.

---

## YAML round-trip

Toggle **YAML** in the toolbar to see the current definition as YAML
in an editable textarea. Two side buttons:

- **Apply** — parses the YAML client-side (via `js-yaml`) and
  replaces the draft. Invalid YAML shows a parse error inline.
- **Download YAML** — downloads a `{pipeline-id}.yml` file matching
  the per-file YAML format loaded by `PipelineFileLoader`. Drop it
  into a `jaiclaw.pipeline.locations.patterns[]` classpath location
  and the same pipeline loads at boot.

The backend also serves a server-side YAML export at
`GET /api/pipeline-studio/drafts/{id}/yaml` — used when you want the
server's canonical rendering rather than the client's.

---

## Validation

Click **Validate** in the toolbar to run
`POST /api/pipeline-studio/validate` against the current draft. The
returned `ValidationReport` renders under the canvas:

- Error rows with `stage 'X'` locations get a click-to-focus button
  that selects the offending stage on the canvas.
- Levenshtein suggestions ("did you mean 'X'?") from the validator
  appear inline.

The validator covers everything documented in
[PIPELINE-STUDIO-API.md](PIPELINE-STUDIO-API.md#validation) —
placeholder references, PROCESSOR beans (both `Function` and
`ConfigurableStageProcessor`), CHANNEL ids, dead-letter URIs,
EMBABEL workflows.

---

## Development

Local iteration without booting a Java app:

```bash
cd apps/jaiclaw-pipeline-studio/frontend
npm install
npm run dev
```

Vite serves on `http://localhost:5173/studio/`. The dev proxy in
`vite.config.ts` forwards `/api/**`, `/actuator/**`, and
`/pipelines/dashboard/**` to `http://localhost:8080` — so pointing
the Studio at a locally-running gateway is one-line.

Running the frontend build + Vitest from Maven:

```bash
./mvnw test -pl :jaiclaw-pipeline-studio -Pfrontend
./mvnw package -pl :jaiclaw-pipeline-studio -Pfrontend -DskipTests
```

Without `-Pfrontend`, only the small Java-side autoconfig compiles —
useful when a developer without Node installed builds the reactor.
The jar in that case ships zero static resources; the fallback
autoconfig still activates, `index.html` just 404s until someone runs
with `-Pfrontend`.

---

## Phase 3 additions

Below the canvas (and any validation panel) the SPA now renders a
**Deploy toolbar**:

- **Deploy** — POSTs to `/drafts/{id}/deploy`. Backend runs
  validation + URI-scheme allowlist first; failures surface in the
  status line under the buttons.
- **Undeploy** — drains + removes routes for the current pipeline id.
- **Redeploy** — sends the current in-memory definition as the
  redeploy body (matches "save + redeploy" without an intermediate
  Save press).
- **Test-run** — inline input textbox + button; hits
  `/drafts/{id}/test-run` and renders the returned status +
  per-stage output in a panel below.

All four buttons hit the Phase 3 endpoints; whether they succeed
depends on the app's Spring Security setup + configured role
authorities. The SPA does not gate buttons client-side by role —
a 403 back from the server shows up in the status line.

The full role setup for the API surface is in
[`PIPELINE-STUDIO-API.md`](PIPELINE-STUDIO-API.md#authz-role-based).

## Deferred to Phase 4

- Real palette entries beyond the built-in generic templates — the
  processor pack (Tier 1 + Tier 3 + AI presets) is Phase 4.

## Non-goals for the SPA

- **True drag-and-drop from palette to canvas** — Phase 2 ships
  click-to-append. Drag would require React Flow's drag-and-drop
  integration, which works fine but adds ~100 lines of state
  choreography that doesn't change what pipelines can be built.
- **DAG editing** — the engine only runs linear stages; when
  `switch`/`parallel` land, the canvas grows edge-picking + branch
  merge points. React Flow supports both natively.
