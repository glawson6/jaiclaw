# JaiClaw 0.9.0 Release Notes

**Release Date**: 2026-06-15

> 0.9.0 is a combined **"AgentMind + Pipeline sync + security
> remediation"** release. AgentMind ports
> [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent)'s
> Soul / Memory / Tendencies model into the JaiClaw runtime; the
> Pipeline module gains a synchronous-result API so callers no longer
> need to poll for completion; and a dependency-CVE remediation pass
> upgrades Spring Boot, Netty, and Tomcat to close 8 of 10 CRITICAL
> findings from the 2026-06-15 OWASP scan.
>
> All four new AgentMind extensions remain **off by default**; the
> Pipeline module remains opt-in via `jaiclaw.pipeline.enabled=true`.
> No existing application changes behaviour after the upgrade unless
> an operator opts in via `application.yml`.

---

## Highlights

- **AgentMind family** — three independent pillars + an opt-in remote
  provider sub-module, all gated behind their own
  `jaiclaw.agentmind.{soul,memory,tendencies}.enabled=true` toggles.

- **Persona overlays** — `jaiclaw-agentmind-soul` ships 5 curated
  persona markdown files (`concise`, `technical`, `mentor`, `socratic`,
  `pirate`) plus a `personality` agent tool that swaps the active
  overlay per session.

- **First-class HookEvent permits** — Soul/Memory/Tendencies mutations
  are typed events on the sealed `HookEvent` hierarchy, alongside
  kanban's `TaskStateChangedEvent`. Permits land in 0.9.0 so plugin
  authors can register handlers; emission at the SPI write boundary
  ships in a follow-up release.

- **Pipeline synchronous-result API** — `PipelineGateway` gains
  `triggerAsync(...)` returning
  `CompletableFuture<PipelineExecutionResult>` and
  `triggerAndAwait(..., Duration timeout)` as a blocking convenience.
  Callers no longer need to poll the actuator endpoint for completion.
  Existing fire-and-forget `trigger(...)` methods are unchanged. Stage
  failures complete the future normally with `status=FAILED` and
  populated `stageOutputs`; only true timeouts and infrastructure
  faults (coordinator capacity, orphan reaping, unknown pipeline)
  complete exceptionally.

- **Dependency CVE remediation pass** — Spring Boot 3.5.14 → 3.5.15,
  explicit `netty-bom` import pinned to 4.1.135.Final (closes 5
  CRITICALs incl. DNS cache poisoning CVE-2026-45674/47691 at CVSS
  10.0 and HTTP request smuggling CVE-2026-42581 at CVSS 9.8),
  explicit `tomcat-embed-*` pin to 10.1.55 (closes 3 CRITICALs incl.
  auth bypass CVE-2026-43512 at CVSS 9.8). 52 CVE occurrences cleared
  across the four targeted dependencies; 8 of 10 CRITICALs from the
  2026-06-15 NVD scan closed. Net zero behavioural change.

- **OSS Index credentials wired into CI** — root `pom.xml` and
  `.github/workflows/security-deps.yml` now read OSS Index
  username/token from CI secrets with NVD-cache step. OSS Index
  analyzer is currently disabled by default pending upstream
  DependencyCheck #7971 fix (token auth works via curl/CLI but 401s
  through the Maven plugin); NVD alone provides authoritative coverage
  in the meantime.

- **Runnable end-to-end demo** — `jaiclaw-examples/agentmind-demo`
  wires all four surfaces against a localhost Anthropic chat endpoint.
  Boot-context smoke spec asserts the wiring; in-module prompt
  composition spec byte-compares the assembled system prompt against
  checked-in goldens.

---

## New Modules

| Module | Purpose | Opt-in flag |
|---|---|---|
| `jaiclaw-agentmind-soul` | Per-agent + per-tenant Soul markdown overlay; persona overlays; `SoulPromptInjector` hook. | `jaiclaw.agentmind.soul.enabled=true` |
| `jaiclaw-agentmind-memory` | Per-user/agent/tenant blob Memory with char-budgeted compaction. | `jaiclaw.agentmind.memory.enabled=true` |
| `jaiclaw-agentmind-tendencies` | Per-user observed style; `DeterministicTendenciesProvider` default learner. | `jaiclaw.agentmind.tendencies.enabled=true` |
| `jaiclaw-tendencies-honcho` | Remote `TendenciesLearningProvider` against a Honcho-compatible API. | `jaiclaw.agentmind.tendencies.provider=honcho` + `HonchoClient` bean |
| `jaiclaw-example-agentmind-demo` | Runnable end-to-end demo. | n/a — example app |

Three starters added under `jaiclaw-starters/`:

- `jaiclaw-starter-agentmind-soul`
- `jaiclaw-starter-agentmind-memory`
- `jaiclaw-starter-agentmind-tendencies`

Module count: `extensions/` grows from 35 → 39; `jaiclaw-starters/`
from 28 → 31; `jaiclaw-examples/` from 40 → 41.

---

## New `HookEvent` Permits

Three permits added to the sealed `HookEvent` hierarchy (Plan task 4.7):

- `SoulUpdatedEvent(scope, tenantId, agentId, version, actor)`
- `MemoryUpdatedEvent(scope, tenantId, agentId, canonicalUserId, version, actor)`
- `TendenciesUpdatedEvent(tenantId, canonicalUserId, agentId, sessionKey, version, provider)`

Plugins register against the types via
`api.on(SoulUpdatedEvent.class, handler)` etc. Emission at the SPI
write boundary (InstrumentedSoulProvider decorator,
BoundedBlobMemoryStore, TendenciesLearningProvider) is a follow-up
shipped with the AgentMind dashboard work.

`HookEventTypesSpec` locks the permit count at 20 (was 17).

---

## Pipeline Synchronous Result API

`PipelineGateway` gains two new method families alongside the existing
fire-and-forget `trigger(...)`:

```java
CompletableFuture<PipelineExecutionResult> triggerAsync(
        String pipelineId, String input);

PipelineExecutionResult triggerAndAwait(
        String pipelineId, String input, Duration timeout)
        throws InterruptedException, ExecutionException, TimeoutException;
```

Overloads carry optional `tenantId` and `correlationId` parameters,
mirroring the existing `trigger` signatures.

`PipelineExecutionResult` is a snapshot of the full execution state at
completion:

```java
record PipelineExecutionResult(
    String executionId,
    String pipelineId,
    String tenantId,
    String correlationId,
    int totalStages,
    int finalStageIndex,
    Map<String, String> stageOutputs,
    String input,
    ExecutionStatus status,           // SUCCESS or FAILED — never RUNNING
    Instant submittedAt,
    Instant completedAt,
    Duration totalDuration,
    String failureReason              // non-null when status=FAILED
) {}
```

Implementation notes (operator-facing):

- Internal `PipelineSyncCoordinator` bean (autowired) holds a
  bounded `ConcurrentHashMap<executionId, CompletableFuture>`.
  Capacity is configurable via `jaiclaw.pipeline.sync.max-pending`
  (default 1024); over-capacity submissions return an already-failed
  future carrying `PipelineCapacityException`.
- A TTL sweep (`jaiclaw.pipeline.sync.orphan-ttl`, default 10m) reaps
  pending futures whose route never completed (JVM crash, route bug),
  completing them exceptionally with `PipelineOrphanException`.
- **Stage failures complete the future normally** with
  `status=FAILED` + populated `stageOutputs` + `failureReason`. Only
  true timeouts and infrastructure faults complete exceptionally.
- The output route completes the sync future *before* dispatching to
  the external output sink (`CHANNEL` / `CAMEL_URI` / `LOG`) — sync
  callers do not wait on external delivery.
- A new class-level `onException(Exception.class)` clause on the
  pipeline route handles failures *outside* the per-stage try/catch
  (transport-auth, security-guard) so the sync future is always
  completed.

New configuration block (all optional, sensible defaults):

```yaml
jaiclaw:
  pipeline:
    sync:
      max-pending: 1024
      orphan-ttl: 10m
      sweep-interval: 60s
      completion-pool-size: 2
```

REST does not gain a sync mode in 0.9.0 — `POST /api/pipelines/{id}/trigger`
still returns HTTP 202 with the `PipelineExecutionHandle`. Adding
`?sync=true` is a candidate for a future release without breaking
changes.

---

## Breaking Changes

**One source-level break** affecting custom `PipelineGateway`
implementations (very rare in practice — most consumers inject the
default `DefaultPipelineGateway` bean):

- `PipelineGateway` interface adds three abstract `triggerAsync(...)`
  overloads. Custom implementations must add these methods (or
  delegate to `DefaultPipelineGateway`). The blocking
  `triggerAndAwait(...)` variants are `default` methods on the
  interface and require no work. The original fire-and-forget
  `trigger(...)` methods are unchanged.

All four new AgentMind extensions are additive and gated by opt-in
properties — no impact on existing apps. The `HookEvent` permit
additions are backwards-compatible: existing plugins that
pattern-match on the prior 17 subtypes continue to work; only
exhaustive `switch` statements over the sealed hierarchy need
updating, and the codebase audit found none.

Spring Boot 3.5.14 → 3.5.15 and the Netty/Tomcat pins are patch-level
upgrades with no API breakage; the upstream maintainers' compatibility
contracts apply.

---

## Dependency Updates

CVE-driven baseline updates land in 0.9.0:

| Dependency | 0.8.0 | 0.9.0 | Reason |
|---|---|---|---|
| Spring Boot | 3.5.14 | **3.5.15** | Clears 12 spring-{core,web} CVEs + 1 spring-security CVE |
| Spring Framework | 6.2.18 | **6.2.19** (transitive via Boot) | Clears 12 CVEs (5 HIGH) |
| Spring Security | 6.5.10 | **6.5.11** (transitive via Boot) | Clears CVE-2026-40988 (HIGH) |
| Netty | 4.1.132.Final | **4.1.135.Final** (explicit `netty-bom`) | Clears 30 CVEs (5 CRITICAL) — DNS cache poisoning, HTTP request smuggling |
| Tomcat | 10.1.54 | **10.1.55** (explicit pin) | Clears 7 CVEs (3 CRITICAL) — auth bypass, improper authorization |

Spring AI 1.1.7, Embabel 0.3.5, Camel 4.18.2 unchanged. The
`netty-bom` and `tomcat-embed-*` pins are declared in `<dependencyManagement>`
before the Spring Boot BOM import (first-match-wins) so the resolved
versions stay correct even if a future Boot patch reverts to an older
version.

The `jaiclaw-tendencies-honcho` sub-module intentionally does NOT
depend on a particular HTTP client; consumers wire their preferred
WebClient / OkHttp implementation against the minimal `HonchoClient`
SPI.

---

## Configuration Surface (Summary)

```yaml
jaiclaw:
  agentmind:
    soul:
      enabled: false
      personas:
        enabled: false
        dir: ${HOME}/.jaiclaw/agentmind/personas
      tenant:
        enabled: false
        write-roles: [ADMIN, OPERATOR]
    memory:
      enabled: false
      tenant:
        enabled: false
        agent-write-enabled: false
    tendencies:
      enabled: false
      provider: deterministic   # deterministic | local-llm | honcho
  pipeline:
    enabled: false              # unchanged — opt-in since 0.8.x
    sync:                       # NEW in 0.9.0
      max-pending: 1024
      orphan-ttl: 10m
      sweep-interval: 60s
      completion-pool-size: 2
```

See [`docs/user/OPERATIONS.md` § AgentMind Configuration](../docs/user/OPERATIONS.md#agentmind-configuration)
for the full reference.

---

## Bug Fixes

- None observable to users. Internal refinements:
  - `AgentMindSoulProperties` compact constructor defaults `personas`
    to `Personas(false, null)` so existing 4-arg callers from prior
    snapshots are not broken at runtime — but the canonical constructor
    is now 5-arg.

---

## Security Fixes

Dependency-CVE remediation pass against the 2026-06-15 OWASP scan
(NVD analyzer). 8 of 10 CRITICALs and 5 HIGHs closed via baseline
upgrades:

**Netty 4.1.132.Final → 4.1.135.Final** (30 CVEs cleared)
- **CVE-2026-45674 / CVE-2026-47691** (CRITICAL, CVSS 10.0) — DNS
  Cache Poisoning via insufficient bailiwick validation of CNAME/NS
  records in `DnsResolveContext`.
- **CVE-2026-42581** (CRITICAL, CVSS 9.8) — HTTP Request Smuggling in
  `HttpObjectDecoder` when conflicting Content-Length pairs with
  Transfer-Encoding: chunked.
- **CVE-2026-42579 / CVE-2026-42584** (CRITICAL, CVSS 9.1) — DNS codec
  missing RFC 1035 enforcement; HTTP response-pipelining mismatch in
  `HttpClientCodec`.

**Tomcat 10.1.54 → 10.1.55** (7 CVEs cleared)
- **CVE-2026-43512** (CRITICAL, CVSS 9.8) — Authentication Bypass via
  digest authentication.
- **CVE-2026-41293** (CRITICAL, CVSS 9.8) — Improper Input Validation.
- **CVE-2026-43515** (CRITICAL, CVSS 9.1) — Improper Authorization on
  HTTP-method-extension constraints.

**Spring Framework 6.2.18 → 6.2.19** (12 CVEs cleared)
- 5 HIGH (CVSS 7.5) — CVE-2026-41838, 41842, 41848, 41850, 41851.
- 7 MEDIUM clustered around the same Spring Framework security
  release.

**Spring Security 6.5.10 → 6.5.11** (3 CVEs cleared)
- **CVE-2026-40988** (HIGH, CVSS 7.5) — affects core, crypto, web.

One **residual HIGH** on the targeted set: **CVE-2026-42582** (Netty
4.1.135.Final, CVSS 7.5) — needs Netty 4.1.136+ when released;
tracked.

**Still outstanding** (not in 0.9.0 scope, deferred to 0.9.x):
- Azure SDK chain — CVE-2026-33117 (CRITICAL, CVSS 9.1) on
  `azure-core` / `azure-identity` / `azure-core-http-netty` /
  `azure-json`. Inclusion path needs investigation (likely Spring
  Cloud Azure via the optional Bedrock starter).
- gRPC chain — CVE-2026-33186 (CRITICAL, CVSS 9.1) on `grpc-api`,
  `grpc-core`, `grpc-protobuf`.
- Spring Cloud Vault 4.3.2 — CVE-2026-40982 (CRITICAL, CVSS 9.1) +
  CVE-2026-41002 (HIGH, CVSS 8.1). Awaiting newer Spring Cloud BOM
  patch.
- WhatsApp / Telegram / LINE adapter false positives — NVD CPE matcher
  hits the mobile-client CVE space for the Java/Camel adapters.
  `.security/dependency-check-suppressions.xml` to be populated.

**Application-level security posture (no change required from
operators):**
- Tenant-scope writes for Soul and Memory remain role-guarded by
  `jaiclaw.agentmind.{soul,memory}.tenant.write-roles` (default
  `[ADMIN, OPERATOR]`); the checks delegate to the standard servlet
  `HttpServletRequest.isUserInRole` so any auth integration
  automatically engages the guard.

See `security-report-2026-06-15.md` (gitignored) for the full scan
methodology and the remediation roadmap for 0.9.x.

---

## Demo + Skills

- `jaiclaw-examples/agentmind-demo` — runnable Spring Boot app
  demonstrating all four surfaces end-to-end.
- `.claude/skills/agentmind-e2e/` — operator skill that drives the
  demo out-of-process (local-only; `.claude/` is gitignored).
- `jaiclaw-examples/agentmind-demo/src/test/resources/goldens/` —
  checked-in golden files for the prompt composition byte-compare
  spec.

---

## Migration Notes

No required migration for typical apps. Optional uptake:

**AgentMind opt-in:**

1. Add the three starters you want:
   ```xml
   <dependency>
       <groupId>io.jaiclaw</groupId>
       <artifactId>jaiclaw-starter-agentmind-soul</artifactId>
   </dependency>
   ```
2. Flip the pillar-level enable flag in `application.yml`.
3. (Optional) Author per-agent or per-tenant Soul markdown via the
   agent tool or the operator REST endpoint.

See `jaiclaw-examples/agentmind-demo/README.md` for a walkthrough.

**Pipeline sync API uptake (opt-in, no config change required):**

```java
@Autowired PipelineGateway gateway;

// Blocking
PipelineExecutionResult result = gateway.triggerAndAwait(
        "my-pipeline", inputBody, Duration.ofSeconds(30));
if (result.status() == ExecutionStatus.SUCCESS) {
    String output = result.stageOutputs().get("last-stage-name");
}

// Or chainable
gateway.triggerAsync("my-pipeline", inputBody)
       .thenApply(r -> r.stageOutputs().get("last-stage-name"))
       .orTimeout(30, TimeUnit.SECONDS);
```

**Source-level break for custom `PipelineGateway` implementations
only** (rare): add the three `triggerAsync(...)` abstract methods.
See the Breaking Changes section.

**Security baseline upgrade**: no caller-side work. Verify Spring Boot
3.5.15, Netty 4.1.135.Final, and Tomcat 10.1.55 resolved as expected
in your build via `./mvnw dependency:list -am | grep -E 'spring-core|netty-transport|tomcat-embed-core'`.

---

## Acknowledgements

The AgentMind extensions are a Java/Spring port of the conceptual
model in [`NousResearch/hermes-agent`](https://github.com/NousResearch/hermes-agent).
Implementation specifics — multi-tenancy, prompt-cache layering,
HookEvent integration, and opt-in module gating — are JaiClaw's own.
