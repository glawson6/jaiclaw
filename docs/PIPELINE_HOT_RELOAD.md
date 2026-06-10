# Pipeline Hot Reload (future feature)

Design sketch for filesystem-watch–driven hot reload of per-file pipeline
definitions. Currently deferred — captured here so it doesn't drift out of
scope, and so we know exactly what API gaps to close before committing.

## Motivation

The current per-file loader (Phase F, see
`io.jaiclaw.pipeline.loader.PipelineFileLoader`) reads YAML files once at
startup. Operators have asked for the same "drop a file on the server, no
restart required" workflow they get from Camel route reloading or
Spring Cloud Config refresh. The bar is lower than full Cloud Config: we
just need to react to file changes under the configured
`jaiclaw.pipeline.locations` filesystem patterns.

## Scope when implemented

- **Filesystem only.** Classpath patterns are immutable at runtime; hot
  reload is meaningless for them.
- **Per-file granularity.** A change to `foo.yml` rebuilds the routes for
  pipeline `foo` only — other pipelines keep running.
- **Add / modify / delete.** All three filesystem events trigger the same
  reconcile loop.

## Sketch implementation

### Watcher

One `java.nio.file.WatchService` per **parent directory** referenced by any
`file:` glob in `jaiclaw.pipeline.locations.patterns`. Each watcher fires
events through a single `BlockingQueue<PipelineFileEvent>` for serial
processing. Run on a virtual thread.

```java
class PipelineFileWatcher implements SmartLifecycle {
    void start() {
        for (Path dir : distinctFilesystemDirsFromPatterns()) {
            dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        }
        Thread.ofVirtual().start(this::pump);
    }
    private void pump() { /* take events, reconcile via PipelineReconciler */ }
}
```

### Reconciler

```java
class PipelineReconciler {
    void onAddOrModify(Path file) {
        // 1. Parse with PipelineYamlParser
        // 2. Single-pipeline validate
        // 3. Replace in PipelineRegistry
        // 4. Stop + rebuild this pipeline's Camel routes
    }
    void onDelete(Path file) {
        // 1. Resolve pipelineId from filename/cache
        // 2. Stop + remove this pipeline's Camel routes
        // 3. Unregister from PipelineRegistry
    }
}
```

## API gaps to close first

The current public surface assumes pipelines exist for the full lifetime of
the JVM. Hot reload needs three new public methods:

1. **`PipelineValidator.validatePipeline(PipelineDefinition def, PipelineRegistry registry)`**
   — currently private (`validatePipeline` at
   `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/validation/PipelineValidator.java:90`).
   Need a single-pipeline call so the watcher can validate without re-running
   every check across the whole registry.

2. **`PipelineRegistry.unregister(String id)`** — does not exist today.
   `PipelineRegistry` only exposes `register`/`get`/`contains`/`getAll`/`size`.

3. **Route management helper.** `PipelineRouteBuilder` builds routes only
   at startup (called once from `PipelineAutoConfiguration#pipelineRouteInitializer`
   at line 224). Hot reload needs:
   ```java
   class PipelineRouteController {
       void addOrReplace(PipelineDefinition def);  // CamelContext.addRoutes(...)
       void remove(String pipelineId);              // CamelContext.removeRoute(...)
   }
   ```
   Removal has to stop the route, drain in-flight SEDA messages, then call
   `CamelContext.removeRoute(routeId)` for the trigger route, all stage
   routes, the output route, and (for non-MANUAL triggers) the gateway
   convergence route.

## Tricky bits to design around

- **In-flight executions.** A reload during an active execution would
  otherwise drop the run. Strategy: drain the SEDA queues with a
  configurable timeout before tearing down. The `PipelineExecutionTracker`
  already records `RUNNING` status; the reconciler can check it.
- **CRON / quartz triggers.** Replacing a CRON pipeline means unregistering
  its quartz job (`org.quartz.Scheduler.unscheduleJob(...)`). Camel's quartz
  component does this automatically when the route is removed; verify.
- **Validation failures on reload.** If a hot-loaded file fails validation,
  log WARN and keep the **previous** definition active. Don't take the
  pipeline offline because somebody saved a half-written YAML.
- **Identity:** files override id via either the `id:` field or the
  filename stem. If a file's `id:` changes mid-flight, the reconciler must
  treat it as `delete(old_id) + add(new_id)` rather than `modify`.
- **Permissions.** WatchService on Linux/macOS uses inotify/FSEvents — works
  fine. Containerized environments with read-only mounts must use a
  rsync/initContainer pattern to push files into a writable volume that the
  app can watch.

## Configuration shape (proposed)

```yaml
jaiclaw:
  pipeline:
    locations:
      patterns:
        - "file:/etc/jaiclaw/pipelines/*.yml"
      watch:
        enabled: true            # default: false
        debounceMs: 250          # coalesce rapid editor saves
        drainTimeoutMs: 5000     # wait this long for in-flight executions
```

## Testing strategy

- **Spock spec** that writes a YAML to a temp dir, asserts the reconciler
  picks it up via the live `WatchService` (use `await/until` with a short
  timeout).
- **Integration spec** that triggers a pipeline, modifies its system prompt
  mid-execution, and asserts the next trigger uses the new definition while
  the in-flight one finishes with the old.
- **Negative spec:** a malformed YAML mid-reload doesn't take the prior
  pipeline offline.

## Out of scope even for the future implementation

- Hot-reloading **inline** YAML pipelines defined under
  `jaiclaw.pipeline.pipelines[]` (Spring Boot's config-refresh ecosystem can
  do this — defer).
- Hot-reloading code-defined `JaiClawPipeline` beans (impossible without
  bytecode reload).
- Tenant-aware per-file hot reload — needs separate design work around
  multi-tenant registry partitioning.

## When to actually implement

When operators ask for it. Don't pre-build — the startup load already covers
the "ship the file with the deploy" pattern, which is what most teams want
first. Hot reload starts paying off once an ops team is doing same-day
pipeline changes from a runbook.
