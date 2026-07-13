# JaiClaw Docker Orchestration — Problem Space

> **Purpose of this document.** This states the *problem space* for adding Docker image/container
> orchestration to JaiClaw. It is a handoff artifact: a separate Claude Code session (with local
> filesystem access to the repo) should use it to produce a concrete **implementation plan**. This
> document deliberately does *not* prescribe the implementation — it frames goals, constraints,
> precedents, and the decisions to be made.

---

## 1. Context

JaiClaw is a Java 21 / Spring Boot 3.5 / Spring AI / Embabel agent framework. Agent capabilities are
exposed as `ToolCallback` implementations registered in the `ToolRegistry` and bridged to Spring AI /
Embabel via `SpringAiToolBridge`. Higher-level, composed capabilities live in the skills layer
(`jaiclaw-skills`, authored via `jaiclaw-skill-creator`). The runtime is reactive, with
`AgentRuntime.run()` as the single entry point driven by external triggers (chat, cron, pipeline,
webhooks).

There is an established precedent for infrastructure-facing tool modules: **`jaiclaw-tools-k8s`** in
`extensions/`. Docker orchestration is the same architectural shape and should follow that pattern.

**Repository:** `glawson6/jaiclaw` (local: `/Users/tap/dev/workspaces/openclaw/jaiclaw/`)

> **Note for the implementing session:** JaiClaw's `CLAUDE.md` and docs lag the actual
> implementation. Read source directly. Use `search_files` with `**/*.java` (exclude `**/target/**`)
> and batch `read_multiple_files`.

---

## 2. The Problem

JaiClaw agents currently have no first-class way to build, run, inspect, or manage Docker images and
containers. We want agents (and skills, cron jobs, pipelines) to orchestrate containerized workloads
as part of their toolset — e.g. build an image, run a service, tail logs, health-check, tear down,
roll back.

This must be done in a way that is consistent with JaiClaw's existing SPI-driven tool architecture,
its multi-tenancy conformance requirements, and its security posture — Docker daemon access is
**root-equivalent on the host** and cannot be treated like an ordinary low-risk tool.

---

## 3. Goals

- **G1.** Provide a set of Docker primitives as `ToolCallback` implementations, registered into
  `ToolRegistry` and bridged to Spring AI / Embabel, following the `jaiclaw-tools-k8s` pattern.
- **G2.** Provide higher-level composed **skills** (workflows) built on those primitives — e.g.
  deploy-service, build-and-run, health-sweep, rollback.
- **G3.** Enforce **security gating** appropriate to a privileged capability: Docker tools must not be
  in the FULL/default `ToolProfile`; access is opt-in and gated via `jaiclaw-tools-security`.
- **G4.** Full **multi-tenancy conformance** per JaiClaw's conformance checklist (see §5).
- **G5.** **Auditability**: container/image lifecycle operations emit `AuditEvent`s via
  `jaiclaw-audit`.
- **G6.** Work correctly in **SINGLE tenant mode** with no tenant context, no filtering, no path
  prefixing.

---

## 4. Proposed Shape (framing, not a decision)

Candidate module: **`jaiclaw-tools-docker`**, in `extensions/`, alongside `jaiclaw-tools-k8s`.

### 4.1 Daemon integration — options to evaluate

| Option | Summary | Tradeoffs |
|---|---|---|
| **docker-java** (`com.github.docker-java:docker-java`) | Typed client for the Engine API over Unix socket / TCP | Structured responses map cleanly to JaiClaw records/sealed types; battle-tested (Testcontainers wraps it). Primary candidate. |
| **CLI shell-out** (`ProcessBuilder`) | Invoke the `docker` CLI | Simplest; loses type safety; requires CLI present. Possible fallback adapter behind the same SPI. |
| **`spring-boot-docker-compose`** | Spring-managed compose lifecycle | Dev-time only, context-managed — **wrong tool** for agent-driven orchestration. Excluded. |

Recommended framing: **docker-java as primary**, behind an SPI, with an optional CLI-fallback adapter.

### 4.2 Candidate tool surface (verbs)

- **Image:** `docker_build`, `docker_pull`, `docker_push`, `docker_images`, `docker_rmi`, `docker_tag`
- **Container lifecycle:** `docker_run`, `docker_start`, `docker_stop`, `docker_restart`, `docker_rm`
- **Introspection:** `docker_ps`, `docker_inspect`, `docker_logs`, `docker_stats`
- **Interaction:** `docker_exec`
- **Multi-container:** `docker_compose_up` / `docker_compose_down` (or defer compose to a skill that
  composes the primitives)

### 4.3 Skills (workflows) — verbs vs. workflows split

Tools are verbs; skills are the composed workflows the agent actually invokes. Candidates:

- **deploy-service**: pull → stop old → run new → health-check → roll back on failure
- **build-and-run**: build from context → run → verify
- **health-sweep**: enumerate tenant-scoped containers → check health → report

---

## 5. Constraints & Cross-Cutting Requirements

These are non-negotiable and derive from existing JaiClaw conventions.

### 5.1 Security (highest priority)

- Docker socket access is root-equivalent. Docker tools **must not** be in the FULL `ToolProfile` by
  default. Gate via `jaiclaw-tools-security` with a dedicated restrictive profile; opt-in only.
- Treat these tools as privileged from day one — this is the single biggest design decision.

### 5.2 Multi-tenancy conformance (per `CLAUDE.md` checklist)

- Containers/images created for a tenant carry tenant-scoped **labels** (e.g.
  `jaiclaw.tenant=<tenantId>`); container names tenant-prefixed.
- `docker_ps` / `docker_images` and any listing/inspection **filter by tenant label** when
  `TenantGuard.isMultiTenant()`.
- Async work (build streams, log tailing, `-d` waits) wrapped with `TenantContextPropagator`.
- New components inject `TenantGuard`, not `TenantContextHolder` directly.
- Everything degrades correctly to SINGLE mode (no labels/filtering/prefixing required).

### 5.3 Audit

- Emit `AuditEvent`s via `jaiclaw-audit` on build / run / stop / rm and other state-changing ops.

### 5.4 Crash recovery (known JaiClaw limitation to design around)

- In-flight executions (handler lambdas, in-memory wait state) do **not** survive JaiClaw restarts,
  even though persisted records do. The container itself survives; the in-memory "am I still waiting
  on this build/run" state does not.
- Mitigation to specify: label containers with the originating task / idempotency key so a recovery
  manager can **reconcile daemon state against persisted records** on restart, rather than relying on
  in-memory state. (Mirrors the kanban `KanbanRecoveryManager` / `SmartLifecycle` approach.)

### 5.5 Consistency with existing patterns

- Records for value types; sealed interfaces for result unions (mirror `ToolResult` Success/Error).
- `jaiclaw-core` stays Spring-free; the module depends on the tools layer, not the reverse.
- Follow the standalone/dual-mode build conventions where relevant.

---

## 6. Motivating Use Case: n8n as a Containerized Workflow Backend

A concrete driver for this capability is orchestrating **n8n** (fair-code workflow automation) in
Docker.

- n8n self-hosts via the official image `docker.n8n.io/n8nio/n8n`, exposing port `5678`, persisting
  state to a volume at `/home/node/.n8n`. Defaults to SQLite; can target Postgres via `DB_TYPE` /
  `DB_POSTGRESDB_*`. Supports a Redis-backed **queue mode** (main instance + worker containers) for
  parallel scale.
- n8n can be integrated with **MCP** to expose workflows to AI agents. JaiClaw already does MCP
  hosting (`McpToolProvider` → `McpServerRegistry` → `/mcp/*`).

**Design question this raises (for the implementation plan to address):** the interesting integration
is not merely "JaiClaw starts an n8n container." Two viable, non-exclusive relationships:

1. **JaiClaw orchestrates n8n containers** (lifecycle, scaling workers) via the Docker tools/skills.
2. **n8n is an MCP-exposed workflow backend** that JaiClaw agents call.

The plan should decide whether to support one, both, or sequence them.

---

## 7. Non-Goals (for this scope)

- Kubernetes orchestration — already covered by `jaiclaw-tools-k8s`; this is Docker-daemon-level.
- A general container registry / image-signing pipeline (Trivy/Grype/cosign/Kyverno etc.) — separate
  concern from agent-invocable Docker tools.
- Building a full n8n integration in this module — n8n is a *motivating use case*, not the deliverable.
- Production hardening of the Docker host itself (socket exposure policy, rootless Docker) — call out
  as deployment guidance, don't implement.

---

## 8. Open Questions for the Implementation Plan

1. **Daemon transport**: Unix socket vs TCP; how is the endpoint configured
   (`@ConfigurationProperties` record in `jaiclaw-config`)? How are credentials/TLS handled?
2. **SPI boundary**: what does the `DockerClient` abstraction look like so docker-java and a CLI
   fallback can both sit behind it?
3. **ToolProfile design**: one privileged Docker profile, or finer-grained (read-only vs. mutating)?
4. **Tenant labeling scheme**: exact label keys/values; how listing filters compose with
   docker-java's label-filter API.
5. **Recovery reconciliation**: what persisted record backs a Docker op, and what does the
   `SmartLifecycle` reconcile-on-startup routine check?
6. **Compose support**: first-class tools vs. skill-composed primitives.
7. **Streaming outputs**: how build/log streams surface through the tool result model and channels
   (SSE?), with tenant context propagated.
8. **n8n sequencing**: orchestration-first, MCP-backend-first, or both.

---

## 9. Source to Read Before Planning

Start here (read actual source, not docs):

- `extensions/jaiclaw-tools-k8s/` — the closest precedent; mirror its module structure, SPI style,
  profile tagging, and tenancy handling.
- `core/jaiclaw-tools/` — `ToolRegistry`, `ToolCallback` SPI, `SpringAiToolBridge`, `ToolProfile`,
  Embabel bridge.
- `extensions/jaiclaw-tools-security/` — how privileged tools are gated.
- `extensions/jaiclaw-audit/` — `AuditEvent`, `AuditLogger` SPI usage.
- Multi-tenancy machinery — `TenantGuard`, `TenantContextPropagator`, `TenantContextHolder`
  (grep across modules).
- `jaiclaw-config/` — `@ConfigurationProperties` record conventions.
- `core/jaiclaw-skills/` + `tools/jaiclaw-skill-creator/` — for authoring the composed workflows.
- Gateway MCP hosting — `McpToolProvider`, `McpServerRegistry`, `/mcp/*` routing — relevant to the
  n8n-as-MCP-backend question.

---

## 10. Definition of Done (for the eventual implementation)

- `jaiclaw-tools-docker` module builds and tests (Spock, `*Spec`) alongside the monorepo.
- Docker primitive tools registered and profile-gated; not in FULL by default.
- At least one composed skill demonstrating deploy/rollback.
- Multi-tenancy conformance verified in both SINGLE and MULTI modes.
- Audit events emitted for state-changing ops.
- Restart reconciliation demonstrated (label → persisted record → daemon reconcile).
- Docs updated: `CLAUDE.md` (directory layout, module count, dependency graph), developer guide,
  `docs/ARCHITECTURE.md`.
