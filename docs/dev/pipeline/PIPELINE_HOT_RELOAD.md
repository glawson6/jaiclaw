# Pipeline Hot Reload — Design

> **Status:** Draft, landed alongside Phase 3 of the Pipeline Studio
> buildout (see `PIPELINE-STUDIO-ANALYSIS.md` § 7). Referenced from
> `PIPELINE_UX_IMPROVEMENTS.md` but never written until now.

Every pipeline definition today loads at boot: `PipelineAutoConfiguration.pipelineRouteInitializer`
(an `ApplicationRunner`) iterates `registry.getAll()` and calls
`camelContext.addRoutes(new PipelineRouteBuilder(...))` for each one.
The Pipeline Studio's authoring plane needs to add / remove / replace
that route set at runtime without a JVM restart.

This document specifies the semantics `PipelineLifecycleManager`
implements. Scope is deliberately narrow: **single-JVM, single-replica
hot deploy**. Multi-replica coordination is called out as a
future SPI hook but not built here.

---

## 1. Requirements

A hot deploy operation is atomic-ish from the caller's view — either
the new definition is running end-to-end, or the old state is
preserved (no half-deployed routes). The runtime must:

1. Refuse to deploy an invalid definition. Studio already validates
   drafts through the Phase 1 API; the lifecycle manager
   revalidates as a belt-and-braces guard before mutating any state.
2. Refuse to overwrite a currently-deployed pipeline unless the
   caller explicitly invokes `redeploy(...)`.
3. Drain in-flight executions when undeploying so callers holding a
   `PipelineExecutionHandle` see either SUCCESS or FAILED, never a
   RUNNING-with-no-routes ghost.
4. Fire lifecycle audit + hook events on every state change:
   `PipelineDeployedEvent`, `PipelineUndeployedEvent`. Redeploy fires
   both.
5. Preserve `PipelineExecutionTracker` history across a redeploy —
   the tracker is keyed on pipelineId, so history stays visible.

Non-requirements:

- **Multi-replica coordination.** Two JVMs deploying to the same
  Redis-backed draft store is out of scope. Adopters with a
  multi-replica gateway either pin to a single replica for
  authoring or provide a `PipelineDeploymentCoordinator` SPI
  implementation that fronts the lifecycle manager (deferred).
- **Deploy rollback across restart.** If the JVM dies between
  registry update and route add, the next restart's
  `pipelineRouteInitializer` re-adds routes from the registry — so
  a deployed pipeline survives a crash. Draft-store durability is
  a separate concern (see [PIPELINE-STUDIO-API.md](../../user/pipeline/PIPELINE-STUDIO-API.md)
  § draft lifecycle).
- **Live-diff of route changes.** Every redeploy is a full
  stop-old + start-new. No attempt to reconcile individual route
  changes — the Camel context handles thousands of routes without
  strain, and simpler semantics are worth more than optimising for
  a rare case.

---

## 2. Route naming

`PipelineRouteBuilder` already assigns predictable, per-pipeline
route ids. The lifecycle manager uses these names to identify what
to stop / remove during undeploy:

| Route id | Purpose |
|---|---|
| `pipeline-{id}-gateway` | Direct-URI convergence route (`direct:pipeline-{id}`) for sync gateway calls |
| `pipeline-{id}-trigger` | The trigger route (HTTP / FILE / CRON / CAMEL_URI / MANUAL) |
| `pipeline-{id}-stage-{name}` | Per-stage worker route (one per stage) |
| `pipeline-{id}-output` | Terminal output-routing route |

All route ids start with `pipeline-{id}-`. Undeploy iterates
`camelContext.getRoutes()`, filters by that prefix, and stops +
removes each.

---

## 3. Deploy flow

```
deploy(PipelineDefinition definition)
  1. Validate         — reject on error (never mutate registry)
  2. Idempotency check — deploy on an id that's already deployed → throw
                         AlreadyDeployedException; caller must call redeploy
  3. Register         — registry.replace(definition)
  4. Build routes     — new PipelineRouteBuilder(definition, ...)
  5. addRoutes        — camelContext.addRoutes(routeBuilder)
  6. Verify           — assert every expected route id exists + is Started
  7. Fire events      — auditor.pipelineDeployed(definition)
                        hookFirer.firePipelineDeployed(definition)
  8. Return           — the definition with any runtime-adjusted fields
```

**Failure modes:**

| Step | Failure | Behaviour |
|---|---|---|
| 1 | validation errors | throw `IllegalArgumentException` with the report; registry untouched |
| 3 | replace succeeds but 5 fails | Roll back: `registry.unregister(id)` (or restore previous definition if this was an overwrite). Then rethrow. |
| 5 | `addRoutes` partially succeeds (rare — Camel's addRoutes is atomic per RouteBuilder, but a `RouteAlreadyExistsException` from a name collision is possible) | Stop + remove any routes matching the pipeline's id prefix. `unregister` from registry. Rethrow. |
| 6 | some routes are Stopped after add | Stop + remove all pipeline routes. `unregister`. Throw. |

Step 6 is a paranoid check — Camel normally starts routes on add
when the context is already running.

---

## 4. Undeploy flow

```
undeploy(String id)
  1. Lookup           — registry.get(id); return null if absent
  2. Drain            — call stopRoute(...) on every pipeline-{id}-* route
                        with a bounded timeout (default 10s per route)
  3. Remove routes    — camelContext.getRouteController().removeRoute(routeId)
                        on each stopped route
  4. Deregister       — registry.unregister(id)
  5. Fire events      — auditor.pipelineUndeployed(id)
                        hookFirer.firePipelineUndeployed(id, definition)
  6. Return           — the removed PipelineDefinition
```

**In-flight executions during drain:**

`stopRoute` in Camel signals SEDA endpoints to complete their
current exchange and stop pulling from the queue. Executions that
started before undeploy finish normally (their end-of-execution
hook fires via `PipelineHookFirer.firePipelineEnd(ctx, result)` as
usual). Executions still queued in a SEDA when its consumer stops
are **dropped** — Camel's default behaviour. This matches the
"undeploy is fast, in-flight lossy" contract we want; adopters who
need queue drain semantics use `errorStrategy=DEAD_LETTER` + a
persistent transport override.

**Drain timeout:**

`jaiclaw.pipeline.authoring.hot-reload.drain-timeout` (default
`PT10S`). If a stop doesn't complete in that window, the manager
force-stops (`stopRoute(id, timeout, true, true)`) and logs a WARN.
Callers see the undeploy return successfully — the WARN is the only
signal that in-flight work was aborted.

**Quartz re-registration:**

CRON-triggered pipelines have a Quartz trigger registered via
`quartz://jaiclaw-pipelines/{id}?cron={expr}`. Camel's
`removeRoute` also de-registers the Quartz trigger — that's the
normal Camel behaviour, no special code needed. **Verified.**

---

## 5. Redeploy flow

```
redeploy(String id, PipelineDefinition next)
  1. Validate the next definition
  2. Undeploy the current (drain + remove + deregister + fire events)
  3. Deploy the next (register + build + add + verify + fire events)
```

Not truly atomic — there is a window (potentially the entire
drain-timeout) between step 2 and step 3 where the pipeline id is
neither registered nor deployed. During this window,
`POST /api/pipelines/trigger` returns 404 and any queued executions
that missed the drain are lost.

**Options considered:**

- **Blue-green with route-id suffixes** (e.g. `pipeline-{id}-v2-*`).
  Rejected: doubles the route count during redeploy and Camel's
  route-name uniqueness means every stage id needs a suffix,
  which propagates into log lines and `PipelineExecutionTracker`
  entries.
- **Take a global lock during redeploy.** Rejected: adds
  coordination overhead for no observable benefit at
  single-replica scale.

The current gap is acceptable at single-replica scale; multi-
replica coordination would need a leader election anyway.

---

## 6. Concurrency

The lifecycle manager is thread-safe via a per-pipelineId
`ReentrantLock`. Two concurrent `deploy("p1", ...)` calls serialise;
the second waits for the first to complete (success or failure)
before entering. This matches the semantics `PipelineRegistry` +
`camelContext.addRoutes` need — Camel itself is thread-safe but
partial-failure recovery in Step 5 above requires exclusive access
to the registry entry.

Deploys against different pipeline ids proceed in parallel.

---

## 7. Multi-replica scope (deferred)

Nothing in this design prevents two replicas from deploying the
same pipeline. The Camel context in each replica is independent —
each has its own routes, its own SEDA queues, its own trigger.
For an HTTP-triggered pipeline behind a load balancer, this is
usually fine (the LB routes each request to one replica). For a
CRON-triggered pipeline, two replicas each fire the Quartz trigger
independently, producing 2× the intended executions.

**Options** (all deferred):

1. `PipelineDeploymentCoordinator` SPI — an interface with
   `tryClaim(pipelineId): boolean` and `release(pipelineId)`.
   Default impl returns `true` always (single-replica). Redis
   / ZooKeeper impls do leader election.
2. Pin authoring to a single replica via a Kubernetes
   `StatefulSet` or a dedicated "authoring" deployment separate
   from the runner deployments.
3. Only deploy from an out-of-band control plane (a CLI, or a
   git-ops sync), never from a running gateway.

Adopters running multi-replica today should choose #2 or #3 and
document that authoring is single-replica.

---

## 8. Events

Two new sealed `HookEvent` subclasses in `io.jaiclaw.core.hook.event`:

```java
public record PipelineDeployedEvent(
        String agentId,        // = pipelineId (HookEvent convention)
        String sessionKey,     // = ""
        Instant timestamp,
        String pipelineId,
        String tenantId,
        int stageCount,
        String origin)         // STUDIO | YAML_IMPORT | CODE_BEAN
    implements HookEvent { ... }

public record PipelineUndeployedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String tenantId,
        String reason)          // "undeploy" | "redeploy" | "reset"
    implements HookEvent { ... }
```

Both are `@Experimental`. Broadcast via `HookRunner.fireVoid(...)`
alongside `AgentStartedEvent` / `AgentEndedEvent` for legacy
tools that treat pipelines as agents.

Audit events (`PipelineAuditor.pipelineDeployed(...)` /
`.pipelineUndeployed(...)`) are additive on the auditor SPI; the
existing implementations get default no-op methods so downstream
adopters don't have to update their `PipelineAuditor`
implementations.

---

## 9. Metrics

New Micrometer counters emitted on every state change:

- `jaiclaw.pipeline.deployed` — counter tagged `pipelineId`, `origin`,
  `tenantId`. Increments on deploy.
- `jaiclaw.pipeline.undeployed` — counter tagged `pipelineId`,
  `tenantId`, `reason`. Increments on undeploy.

Both wire through the existing `PipelineMetrics` bean when present;
absent → no-op.

---

## 10. Test surface

Unit tests live in `jaiclaw-pipeline-authoring`:

- `PipelineLifecycleManagerSpec` — mocks `PipelineRegistry`,
  `CamelContext`, `PipelineValidator`. Verifies happy path,
  already-deployed guard, validate-failure short-circuit,
  addRoutes-failure rollback.
- `PipelineLifecycleManagerIntegrationSpec` — boots a real
  Camel context via `DefaultCamelContext`, deploys a trivial
  pipeline (log-only), fires a manual trigger, undeploys, asserts
  `camelContext.getRoutes()` shrinks to zero, redeploys with a
  different definition, verifies the new one runs.

Deferred: multi-replica coordinator specs (no coordinator SPI to
test).
