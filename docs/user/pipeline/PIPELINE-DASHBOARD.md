# Pipeline Dashboard

The **JaiClaw Pipeline Dashboard** is a lightweight read-only web page
served by any gateway that has `jaiclaw-pipeline` on the classpath.
It shows every registered pipeline, per-pipeline recent execution
history, a live-updating flow diagram (via SSE), and a manual trigger
form for alias-configured pipelines.

Phase 0 of the [Pipeline Studio buildout](../../dev/pipeline/PIPELINE-STUDIO-ANALYSIS.md).
No React, no build toolchain — vanilla HTML/CSS/JS served as classpath
resources.

---

## Install

Add the dependency alongside `jaiclaw-pipeline` in the consuming app:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-pipeline-dashboard</artifactId>
</dependency>
```

The dashboard is not pulled by `jaiclaw-spring-boot-starter`. It's
opt-in — every consuming app declares the dependency explicitly.

---

## Enable / disable

Enabled by default when the module is on the classpath:

```yaml
jaiclaw:
  pipeline:
    dashboard:
      enabled: true       # default; set to false to opt out
```

The autoconfig gates on:

- `@ConditionalOnClass(DispatcherServlet.class)` — WebMVC required.
  (A WebFlux port lands in a later phase.)
- `@ConditionalOnProperty(jaiclaw.pipeline.dashboard.enabled, matchIfMissing=true)`.

---

## What you get

Point a browser at `http://<host>:<port>/pipelines/dashboard`.

Left sidebar lists every pipeline the actuator reports. Selecting one
shows:

- **Flow diagram** — the same SVG that `GET /api/pipelines/{id}/render.html?view=flow&format=svg`
  produces server-side, embedded in the page.
- **Trigger form** — POST an input payload to an operator-configured
  alias (`jaiclaw.pipeline.http-trigger.allowed`).
- **Recent executions** — the last N executions from
  `PipelineExecutionTracker` (via `/actuator/pipelines/{id}`) with
  status, start time, and duration.
- **Live events log** — a scrolling feed of every SSE event from
  `/api/pipelines/{id}/events` (`snapshot`, `execution-started`,
  `stage-started/completed/failed`, `execution-completed/failed`).
- **Flow overlay** — every event tagged with a `stageName` adds
  `active` / `completed` / `failed` CSS classes to the SVG stage box
  (identified via the `data-stage="X"` attributes
  `PipelineHtmlRenderer` already emits). Stages light up as they run.

Multi-tenant deployments show a tenant indicator in the top-right
sourced from `TenantContextHolder` — the dashboard doesn't allow
switching tenants (that's an operator action), but it makes the
current scope visible.

---

## Endpoints served

| Path | Response | Notes |
|---|---|---|
| `GET /pipelines/dashboard` | HTML shell | Static resource; no server-side rendering |
| `GET /pipelines/dashboard/*.css`, `*.js` | Static resources | Served from `classpath:/jaiclaw-pipeline-dashboard/` |
| `GET /pipelines/dashboard/whoami` | JSON `{tenantId, tenantName, multiTenant}` | Used by the SPA to decide whether to show the tenant indicator |

Everything else — pipeline list, executions, SSE, trigger — is served
by the existing `jaiclaw-pipeline` module's controllers.

---

## Security

The dashboard is a plain WebMVC controller — it inherits whatever
Spring Security configuration the consuming app applies to the
`/pipelines/dashboard/**` path. **This module ships no auth of its
own.** If the app has Spring Security wired, add a matcher for
`/pipelines/dashboard/**` that requires the appropriate role.

The trigger form calls the same `POST /api/pipelines/trigger`
endpoint any other client would — subject to its authn/z chain +
the `jaiclaw.pipeline.http-trigger.allowed` alias map.

### Coexisting with an OIDC / browser-login surface

Apps that use `jaiclaw-security` in `mode=api-key` and also want a
browser OIDC flow (Spring Security `oauth2Login()`) for the
dashboard need to opt the dashboard's browser-navigation paths
out of the `ApiKeyAuthenticationFilter` — otherwise the API-key
filter runs first and returns `401 {"error":"invalid_api_key"}`
before the OIDC chain can redirect. Since 1.0.0, the
skip-list is configurable:

```yaml
jaiclaw:
  security:
    api-key-filter:
      skip-paths:
        - /api/health              # default — health probes
        - /webhook/**              # default — channel webhooks
        - /pipelines/dashboard/**  # add: browser dashboard
        - /oauth2/authorization/** # add: Spring Security's OIDC entry
        - /login/oauth2/code/**    # add: Spring Security's OIDC callback
```

`/api/**` and `/mcp/**` still require the API key. Only the paths
listed above bypass the filter, letting a separate
`SecurityFilterChain` (scoped to `/pipelines/dashboard/**` with
`.oauth2Login()`) handle browser sessions.

The dashboard is **read-only** aside from the trigger form; there is
no draft store, no deploy, no delete. Those land in Phases 1–3.

---

## Verification

Boot any consuming app with `jaiclaw-pipeline-dashboard` on the
classpath and at least one registered pipeline. In this repo:

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-pipeline-e2e -am -DskipTests -o
java -jar jaiclaw-examples/pipeline-e2e/target/jaiclaw-example-pipeline-e2e-*.jar
```

Then open `http://localhost:8100/pipelines/dashboard`. Trigger
`processor-pipe` from the form (configure an alias for it via
`jaiclaw.pipeline.http-trigger.allowed`) and watch the SVG light up.

---

## Roadmap

The dashboard is Phase 0 of the Pipeline Studio. Later phases:

- **Phase 1** — authoring plane (draft CRUD, catalog, JSON schema, YAML round-trip)
- **Phase 2** — Studio SPA (React Flow canvas + inspector + validation panel)
- **Phase 3** — hot deploy + test-run + role-based authz
- **Phase 4** — baseline processor pack

See `docs/dev/pipeline/PIPELINE-STUDIO-ANALYSIS.md` for the full
analysis and phased roadmap.
