# JaiClaw Multi-Tenancy Remediation Plan

> **Status:** Implementation complete. All phases (0–10) have been executed.

## Context

JaiClaw supports two first-class operating modes:

1. **Single-tenant** — One organization, one data space. A personal AI assistant, a team bot, a departmental agent, or a company-wide deployment. May have many users with JWT-based authentication, roles, and authorization — but all users share the same organizational data boundary. There is no need to partition data by organization because there is only one.

2. **Multi-tenant** — Multiple organizations sharing a single deployment, each with strictly isolated data. CoachIQ serving 100 university football programs from one instance. Each tenant gets isolated sessions, memory, cron jobs, documents, and audit trails. Requires tenant resolution (JWT claims, bot token mapping, or custom resolver) and fail-closed enforcement.

**Both modes are intentional deployment choices**, not a legacy/modern split. A developer choosing single-tenant mode is making a deliberate architectural decision — not settling for a fallback. Authentication (API-key, JWT, SSO) is completely orthogonal to tenant mode.

### The Problem

An audit revealed **9 CRITICAL**, **5 MEDIUM**, and **3 LOW** gaps where multi-tenant isolation is missing or incomplete. The deeper issue is that JaiClaw lacks a formal tenant mode configuration. Components handle null tenant inconsistently — some filter, some don't, some silently allow cross-tenant access. Tenant awareness does not consistently propagate to all persistence layers.

### What This Plan Delivers

A configuration-driven tenant mode (`jaiclaw.tenant.mode: single|multi`) with a central `TenantGuard` that all components use, and **tenant-aware key/path resolution at every persistence layer** — Redis keys, file paths, SQL queries, vector store metadata, and in-memory map keys.

---

## Persistence Layer Audit

Before describing the fix, here is the complete inventory of every persistence layer and its current tenant isolation status.

### Already Tenant-Aware (no changes needed)

| Layer | Technology | Key/Path Pattern | Status |
|-------|-----------|-----------------|--------|
| `RedisCalendarProvider` | Redis | `tenant:{tenantId}:calendar:{calendarId}:event:{eventId}` | ✅ Complete |
| `VectorStoreSearchManager` | Spring AI VectorStore | `tenantId` metadata + filter expression | ✅ Complete |
| `JwtAuthenticationFilter` | ThreadLocal | Sets `TenantContextHolder` from JWT claim | ✅ Complete |

### Needs Tenant-Aware Key/Path Resolution

| Layer | Technology | Current Key/Path | Needed Key/Path (MULTI mode) | Priority |
|-------|-----------|-----------------|------------------------------|----------|
| **JsonFileCronJobStore** | JSON file | `cron-jobs.json` (single file, no tenant) | Filter by `tenantId` field in job list | CRITICAL |
| **H2CronJobDefinitionStore** | H2/JDBC | `SELECT * FROM cron_job_definitions` (no WHERE tenant) | `WHERE tenant_id = ?` on all queries | CRITICAL |
| **H2CronExecutionStore** | H2/JDBC | `SELECT * FROM cron_execution_history` (no WHERE tenant) | `WHERE tenant_id = ?` on all queries | CRITICAL |
| **WorkspaceMemoryManager** | Markdown file | `{baseDir}/MEMORY.md` | `{baseDir}/{tenantId}/MEMORY.md` | CRITICAL |
| **DailyLogAppender** | Markdown files | `{baseDir}/logs/{date}.md` | `{baseDir}/{tenantId}/logs/{date}.md` | CRITICAL |
| **SessionTranscriptStore** | JSONL files | `sessions/{sessionKey}.jsonl` | `sessions/{tenantId}/{sessionKey}.jsonl` | CRITICAL |
| **IdentityLinkStore** | JSON file | `{storePath}` (single file, key: `channel:userId`) | Add `tenantId` to link model; filter on read | CRITICAL |
| **JsonFileSubscriptionRepository** | JSON file | `{storagePath}/subscriptions.json` | Add `tenantId` to Subscription; filter on read | CRITICAL |
| **InMemoryDocStoreRepository** | ConcurrentHashMap | Key: entry ID (no tenant) | Composite key or filter by `tenantId` field | HIGH |
| **JsonFileDocStoreRepository** | JSON file | `{storagePath}/docstore.json` | Add `tenantId` to DocStoreEntry; filter on read | HIGH |
| **CanvasFileManager** | HTML files | `{canvasDir}/{id}.html` | `{canvasDir}/{tenantId}/{id}.html` | HIGH |
| **CanvasService** | In-memory state | Single `currentFile`/`visible` | `Map<String, CanvasState>` keyed by session | HIGH |
| **SessionManager** | ConcurrentHashMap | Key: `sessionKey` | Already has runtime tenant check; needs key prefix in MULTI | MEDIUM |
| **InMemoryCalendarProvider** | ConcurrentHashMap | Events key: `eventId` (no tenant prefix) | Composite key `{tenantId}:{eventId}` or validate on access | MEDIUM |
| **BrowserService** | ConcurrentHashMap | Key: string session ID | Prefix: `{tenantId}:{sessionId}` | MEDIUM |
| **HandshakeSessionStore** | ConcurrentHashMap | Key: `handshakeId` (UUID) | Validate tenant in session token | MEDIUM |
| **RateLimitFilter** | In-memory | Key: `jwt:{subject}` or `ip:{addr}` | Key: `{tenantId}:{subject}` or `{tenantId}:{ip}` | LOW |
| **ApiKeyAuthenticationFilter** | HTTP header | No tenant resolution | Support per-tenant API keys or pair with BotTokenResolver | LOW |

---

## Phase 0: Tenant Mode Configuration (Foundation)

### 0.1 Create TenantMode enum and TenantProperties
**File:** `jaiclaw-core/.../tenant/TenantMode.java` (NEW)

```java
public enum TenantMode {
    /** One organization, one data space. Personal assistant, team bot, company-wide. */
    SINGLE,
    /** Platform mode. Multiple organizations, strict isolation. Fail-closed. */
    MULTI
}
```

**File:** `jaiclaw-core/.../tenant/TenantProperties.java` (NEW)

```java
@ConfigurationProperties(prefix = "jaiclaw.tenant")
public record TenantProperties(
    TenantMode mode,          // default: SINGLE
    String defaultTenantId    // optional: stamps a default tenant ID for storage keys in SINGLE mode
) {
    public TenantProperties {
        if (mode == null) mode = TenantMode.SINGLE;
    }
    public boolean isMultiTenant() { return mode == TenantMode.MULTI; }
}
```

**Configuration examples:**
```yaml
# Single-tenant (default — no config needed)
jaiclaw:
  tenant:
    mode: single

# Multi-tenant (explicit opt-in)
jaiclaw:
  tenant:
    mode: multi
```

### 0.2 TenantGuard utility
**File:** `jaiclaw-core/.../tenant/TenantGuard.java` (NEW)

Central utility injected by all tenant-aware components. Provides three resolution strategies:

```java
public class TenantGuard {
    private final TenantProperties props;

    /** Returns tenantId or null. In MULTI mode, throws if no tenant context is set. */
    public String requireTenantIfMulti() { ... }

    /** Returns tenantId for storage key construction. In SINGLE mode returns "default" (or configured defaultTenantId).
        In MULTI mode, throws if no tenant context. */
    public String resolveTenantIdForStorage() { ... }

    /** Returns tenantId for prefixing keys/paths. In SINGLE mode returns empty string (no prefix).
        In MULTI mode, returns tenantId. */
    public String resolveTenantPrefix() { ... }

    public boolean isMultiTenant() { return props.isMultiTenant(); }
}
```

**Key resolution examples using TenantGuard:**

| Persistence Layer | SINGLE mode | MULTI mode |
|-------------------|-------------|------------|
| File path prefix | `{baseDir}/` (no tenant subdir) | `{baseDir}/{tenantId}/` |
| Redis key prefix | `calendar:default:` | `tenant:{tenantId}:calendar:` |
| SQL WHERE clause | No tenant filter | `AND tenant_id = ?` |
| Map key prefix | No prefix | `{tenantId}:` |
| JSON file filter | No filtering | Filter by `tenantId` field |

**Design rationale:**
- In `SINGLE` mode: No overhead. No partitioning. Storage paths and keys remain flat.
- In `MULTI` mode: Fail-closed. Missing tenant = exception, not silent data leak.
- Auth mode and tenant mode are independent. JWT in SINGLE mode provides user identity/roles, not tenant partitioning.
- Components inject `TenantGuard` instead of directly calling `TenantContextHolder`.

### 0.3 Auto-configure TenantGuard
**File:** `jaiclaw-spring-boot-starter/.../JaiClawCoreAutoConfiguration.java`

Register `TenantProperties` and `TenantGuard` beans. `TenantGuard` is `@ConditionalOnMissingBean` so apps can override.

---

## Phase 1: Async Context Propagation

### 1.1 TenantContextPropagator utility
**File:** `jaiclaw-core/.../tenant/TenantContextPropagator.java` (NEW)

Captures and restores `TenantContext` across async boundaries:
- `Runnable wrap(Runnable)` — wraps with current tenant context
- `<T> Supplier<T> wrap(Supplier<T>)` — for CompletableFuture
- `Executor wrap(Executor)` — delegating Executor

No-op when `TenantContextHolder.get()` is null (zero overhead in SINGLE mode).

### 1.2 Fix AgentRuntime async tenant loss — CRITICAL
**File:** `jaiclaw-agent/.../AgentRuntime.java` (line ~96)

Wrap the Supplier in `CompletableFuture.supplyAsync()` with `TenantContextPropagator.wrap()`.

### 1.3 Fix HookRunner virtual thread context loss — MEDIUM
**File:** `jaiclaw-plugin-sdk/.../HookRunner.java`

Wrap hook execution Runnables with `TenantContextPropagator.wrap()`.

---

## Phase 2: CronJob Tenant Isolation — CRITICAL

### 2.1 Add tenantId to CronJob model
**File:** `jaiclaw-core/.../model/CronJob.java`

Add `String tenantId` field (nullable — null = single-tenant).

### 2.2 Tenant-scope CronService operations
**File:** `jaiclaw-cron/.../CronService.java`

Inject `TenantGuard`. MULTI mode: filter `listJobs()`, verify tenant on `getJob()/removeJob()`, stamp on `addJob()`. SINGLE mode: no filtering.

### 2.3 Set tenant context in CronJobExecutor
**File:** `jaiclaw-cron/.../CronJobExecutor.java`

Before agent execution: if job has tenantId, set `TenantContextHolder`. Clear in finally block.

### 2.4 Tenant-scope JsonFileCronJobStore
**File:** `jaiclaw-cron/.../JsonFileCronJobStore.java`

Filter by `tenantId` field on reads when MULTI mode. Single file, tenantId as field.

### 2.5 Add tenant_id to H2 schema — both tables
**Files:**
- `jaiclaw-cron-manager/.../h2/H2CronJobDefinitionStore.java`
- `jaiclaw-cron-manager/.../h2/H2CronExecutionStore.java`

Add `tenant_id VARCHAR(255)` column to both `cron_job_definitions` and `cron_execution_history` tables. All queries get `AND tenant_id = ?` in MULTI mode.

### 2.6 Wire tenant into CronJobManagerService & MCP tools
**Files:** `CronJobManagerService.java`, `CronManagerMcpToolProvider.java`, `CronAgentFactory.java`

MCP tool provider already receives `TenantContext` — now passes it through. `CronAgentFactory` sets `TenantContextHolder` before execution.

---

## Phase 3: Memory & Logging Tenant Isolation — CRITICAL

### 3.1 Tenant-scope WorkspaceMemoryManager
**File:** `jaiclaw-memory/.../WorkspaceMemoryManager.java`

Inject `TenantGuard`. Path resolution:
- SINGLE: `{baseDir}/MEMORY.md`
- MULTI: `{baseDir}/{tenantId}/MEMORY.md`

### 3.2 Tenant-scope DailyLogAppender
**File:** `jaiclaw-memory/.../DailyLogAppender.java`

Path resolution:
- SINGLE: `{baseDir}/logs/{date}.md`
- MULTI: `{baseDir}/{tenantId}/logs/{date}.md`

### 3.3 Tenant-scope SessionTranscriptStore
**File:** `jaiclaw-memory/.../SessionTranscriptStore.java`

Path resolution:
- SINGLE: `sessions/{sessionKey}.jsonl`
- MULTI: `sessions/{tenantId}/{sessionKey}.jsonl`

---

## Phase 4: DocStore Tenant Isolation — CRITICAL

### 4.1 Add tenantId to DocStoreEntry
**File:** `jaiclaw-docstore/.../model/DocStoreEntry.java`

Add `String tenantId` field.

### 4.2 Tenant-scope InMemoryDocStoreRepository
**File:** `jaiclaw-docstore/.../repository/InMemoryDocStoreRepository.java`

MULTI mode: filter all queries by `tenantId` in addition to existing `userId`/`chatId`.

### 4.3 Tenant-scope JsonFileDocStoreRepository
**File:** `jaiclaw-docstore/.../repository/JsonFileDocStoreRepository.java`

MULTI mode: filter by `tenantId` field on reads. Stamp `tenantId` on writes.

### 4.4 Tenant-scope DocStore MCP tools
Pass `TenantContext.tenantId()` into repository calls.

---

## Phase 5: Identity Tenant Isolation — CRITICAL

### 5.1 Add tenantId to identity link model
**File:** `jaiclaw-identity/.../IdentityLinkService.java`

Scope link/resolve/merge by tenantId. Same person on Telegram can belong to different tenants.

### 5.2 Tenant-scope IdentityLinkStore
**File:** `jaiclaw-identity/.../IdentityLinkStore.java`

Add `tenantId` to stored links. MULTI mode: filter on read/write. Key changes from `channel:userId` to include tenant context.

---

## Phase 6: Subscription Tenant Isolation — CRITICAL

### 6.1 Add tenantId to Subscription model
**File:** `jaiclaw-subscription/.../model/Subscription.java` (or equivalent)

Add `String tenantId` field.

### 6.2 Tenant-scope JsonFileSubscriptionRepository
**File:** `jaiclaw-subscription/.../repository/JsonFileSubscriptionRepository.java`

MULTI mode: filter by `tenantId` on all queries. Stamp `tenantId` on save.

---

## Phase 7: Canvas Tenant Isolation — HIGH

### 7.1 Per-session CanvasService state
**File:** `jaiclaw-canvas/.../CanvasService.java`

Replace single `currentFile`/`visible` with `Map<String, CanvasState>` keyed by session key (which includes tenantId in MULTI mode).

### 7.2 Per-tenant CanvasFileManager directories
**File:** `jaiclaw-canvas/.../CanvasFileManager.java`

Path resolution:
- SINGLE: `{canvasDir}/{id}.html`
- MULTI: `{canvasDir}/{tenantId}/{id}.html`

---

## Phase 8: Medium-Priority Fixes

### 8.1 GatewayService fail-closed in MULTI mode
**File:** `jaiclaw-gateway/.../GatewayService.java`

Inject `TenantGuard`. MULTI mode: reject messages with unresolved tenant context. SINGLE mode: no change.

### 8.2 InMemoryCalendarProvider tenant enforcement
Events key currently `eventId` only. MULTI mode: composite key `{tenantId}:{eventId}` or validate on access.

### 8.3 Browser session tenant scoping
**File:** `jaiclaw-browser/.../BrowserService.java`

MULTI mode: prefix session IDs with tenantId. SINGLE mode: no change.

### 8.4 SessionManager key prefix
**File:** `jaiclaw-agent/.../session/SessionManager.java`

Already has runtime tenant validation. MULTI mode: include tenantId in map key for defense-in-depth.

### 8.5 HandshakeSessionStore validation
**File:** `jaiclaw-tools-security/.../HandshakeSessionStore.java`

Stamp tenantId on session creation from `TenantContextHolder`. Validate tenantId on retrieval — return empty if tenant mismatch.

---

## Phase 9: Low-Priority Fixes

### 9.1 AuditLogger auto-stamp tenantId
Auto-populate from `TenantContextHolder` if null in `AuditEvent`.

### 9.2 Skills null-tenant fallback
MULTI mode + null tenant: return only globally-scoped skills.

### 9.3 Rate limiting per-tenant
**File:** `jaiclaw-security/.../RateLimitFilter.java`

MULTI mode rate limit key: `{tenantId}:{subject}` instead of `jwt:{subject}`.

### 9.4 ApiKeyAuthenticationFilter tenant support
Support per-tenant API keys (API key → tenantId mapping) as an alternative to JWT for multi-tenant deployments using API key auth.

---

## Implementation Order & Dependencies

```
Phase 0 (Config)  ──→ Phase 1 (Async) ──→ Phase 2 (Cron)
                                        ──→ Phase 3 (Memory + Transcripts)
                                        ──→ Phase 4 (DocStore)
                                        ──→ Phase 5 (Identity)
                                        ──→ Phase 6 (Subscription)
                                        ──→ Phase 7 (Canvas)
                                        ──→ Phase 8 (Medium)
                                        ──→ Phase 9 (Low)
```

Phase 0 first — `TenantGuard` and `TenantProperties` are the foundation.
Phase 1 next — `TenantContextPropagator` needed for async boundaries.
Phases 2–9 are independent of each other.

---

## Two First-Class Modes

### SINGLE mode (`jaiclaw.tenant.mode: single` — default)

| Aspect | Behavior |
|--------|----------|
| **Who chooses this** | Personal assistants, team bots, departmental agents, single-company deployments |
| **Configuration** | Zero config required. Default mode. |
| **Authentication** | Any auth mode — API-key, JWT, SSO, none. A single-tenant deployment with 50 users and JWT for roles/permissions is perfectly valid. Auth is orthogonal to tenant mode. |
| **Data isolation** | Single organizational data space. All authenticated users share the same data boundary. |
| **Storage paths** | Root paths. No tenant subdirectories. `{baseDir}/MEMORY.md`, `{baseDir}/logs/` |
| **Redis keys** | Standard keys without tenant prefix |
| **SQL queries** | No tenant_id WHERE clause |
| **In-memory maps** | Keys without tenant prefix |
| **JSON file stores** | No filtering by tenantId field |
| **Cron** | All jobs visible. No filtering. No tenantId stamping. |
| **Performance** | `TenantGuard` short-circuits. Zero overhead from tenant checks. |
| **TenantContextHolder** | May or may not be set (ignored for data partitioning). Even if JWT contains a tenantId claim, it is not used for storage isolation. |
| **Gateway** | Processes all messages. No fail-closed rejection. |

### MULTI mode (`jaiclaw.tenant.mode: multi`)

| Aspect | Behavior |
|--------|----------|
| **Who chooses this** | SaaS platforms, multi-org deployments |
| **Configuration** | Explicit opt-in: `jaiclaw.tenant.mode: multi` |
| **Authentication** | Any auth mode that provides a tenantId — JWT claims, BotTokenResolver, custom TenantResolver. |
| **Data isolation** | Strict. Every persistence operation scoped by tenantId. Cross-tenant access = exception. |
| **Storage paths** | Per-tenant: `{baseDir}/{tenantId}/MEMORY.md`, `{baseDir}/{tenantId}/logs/` |
| **Redis keys** | Prefixed: `tenant:{tenantId}:...` (already done in calendar) |
| **SQL queries** | All queries include `WHERE tenant_id = ?` |
| **In-memory maps** | Keys prefixed with `{tenantId}:` or filtered on access |
| **JSON file stores** | All reads/writes filtered by tenantId field |
| **Cron** | Jobs tagged with tenantId. CRUD filtered. Executor restores tenant context. |
| **Performance** | Tenant resolution, filtering, and context propagation active. |
| **TenantContextHolder** | Must be set with a valid tenantId. Null = rejected (fail-closed). |
| **Gateway** | Rejects messages without resolved tenant context. |

### Key Principle: Auth Mode ≠ Tenant Mode

Authentication (`jaiclaw.security.mode`) and tenancy (`jaiclaw.tenant.mode`) are independent axes:

| | `security.mode: api-key` | `security.mode: jwt` | `security.mode: none` |
|---|---|---|---|
| **`tenant.mode: single`** | Personal bot, small team | Company-wide with user roles/permissions | Dev/prototype |
| **`tenant.mode: multi`** | Needs BotTokenResolver for tenant mapping | tenantId from JWT claim + user auth | Not recommended (no identity source) |

All four combinations of `{api-key, jwt} × {single, multi}` are valid deployment configurations.

---

## Phase 10: CLAUDE.md Multi-Tenancy Conformance Guideline

### 10.1 Update JaiClaw CLAUDE.md
**File:** `/Users/tap/dev/workspaces/openclaw/jaiclaw/CLAUDE.md`

Added "Multi-Tenancy Conformance Check" section with 5 verification points.

### 10.2 Update CoachIQ CLAUDE.md
**File:** `/Users/tap/dev/workspaces/campaigns/coachiq/CLAUDE.md`

Added conformance guideline adapted for CoachIQ.

---

## Verification

Every component must be tested in **both modes** — single-tenant is a first-class mode, not a skip-tests path.

### Unit tests (Spock) — both modes

| Component | SINGLE mode test | MULTI mode test |
|-----------|-----------------|-----------------|
| **TenantGuard** | `resolveTenantIdForStorage()` returns "default"; `resolveTenantPrefix()` returns "" | Throws when no context; returns tenantId when set |
| **TenantContextPropagator** | No-op wrap (null context, zero overhead) | Context survives async + virtual threads |
| **CronService** | All jobs visible, no filtering | Jobs filtered by tenant; cross-tenant denied |
| **H2CronStores** | Queries without tenant_id filter | All queries include `WHERE tenant_id = ?` |
| **WorkspaceMemory** | `{baseDir}/MEMORY.md` | `{baseDir}/{tenantId}/MEMORY.md` |
| **DailyLog** | `{baseDir}/logs/{date}.md` | `{baseDir}/{tenantId}/logs/{date}.md` |
| **SessionTranscripts** | `sessions/{key}.jsonl` | `sessions/{tenantId}/{key}.jsonl` |
| **DocStore** | All docs by userId/chatId | Filtered by tenantId + userId/chatId |
| **IdentityLink** | Links resolved globally | Links scoped by tenantId |
| **Subscription** | All subscriptions by userId | Filtered by tenantId |
| **Canvas paths** | `{dir}/{id}.html` | `{dir}/{tenantId}/{id}.html` |
| **Gateway** | Processes without tenant | Rejects unresolved tenant |
| **RateLimitFilter** | Key: `jwt:{subject}` | Key: `{tenantId}:{subject}` |

### Integration tests
1. **SINGLE mode**: Boot with default config (no tenant properties). Full flow — messages, cron, documents, memory, transcripts. Verify everything works identically to current behavior.
2. **MULTI mode**: Boot with `jaiclaw.tenant.mode: multi`. Two tenants. Verify complete data isolation across all persistence layers. Verify unresolved tenant is rejected.

### Startup validation
MULTI mode + no `TenantResolver` beans registered → log WARNING: "Multi-tenant mode enabled but no TenantResolver configured. All requests will be rejected."

---

## Files Modified (Complete)

| Module | Files | Change Type |
|--------|-------|-------------|
| jaiclaw-core | `TenantMode.java` (NEW), `TenantProperties.java` (NEW), `TenantGuard.java` (NEW), `TenantContextPropagator.java` (NEW), `CronJob.java`, `IdentityLink.java` | Config + utilities + models |
| jaiclaw-config | `TenantConfigProperties.java` (NEW), `JaiClawProperties.java` | Spring config binding |
| jaiclaw-spring-boot-starter | `JaiClawAutoConfiguration.java` | Register new beans |
| jaiclaw-agent | `AgentRuntime.java`, `SessionManager.java` | Async propagation + key prefix |
| jaiclaw-plugin-sdk | `HookRunner.java` | Virtual thread propagation |
| jaiclaw-cron | `CronService.java`, `CronJobExecutor.java` | Tenant filtering + context |
| jaiclaw-cron-manager | `H2CronJobDefinitionStore.java`, `H2CronExecutionStore.java`, `schema.sql`, `CronExecutionRecord.java`, `CronAgentFactory.java` | Schema + filtering |
| jaiclaw-memory | `WorkspaceMemoryManager.java`, `DailyLogAppender.java`, `SessionTranscriptStore.java` | Per-tenant paths |
| jaiclaw-docstore | `DocStoreEntry.java`, `InMemoryDocStoreRepository.java`, `JsonFileDocStoreRepository.java` | Model + filtering |
| jaiclaw-identity | `IdentityLinkStore.java` | Tenant scoping |
| jaiclaw-subscription | `Subscription.java`, `JsonFileSubscriptionRepository.java` | Model + filtering |
| jaiclaw-canvas | `CanvasService.java`, `CanvasFileManager.java` | Per-tenant state/dirs |
| jaiclaw-gateway | `GatewayService.java` | Fail-closed in MULTI |
| jaiclaw-calendar | `InMemoryCalendarProvider.java` | Access validation |
| jaiclaw-browser | `BrowserService.java` | Session key prefix |
| jaiclaw-security | `RateLimitFilter.java` | Tenant-aware keys |
| jaiclaw-tools-security | `HandshakeSession.java`, `HandshakeSessionStore.java` | Tenant stamping + validation |
| jaiclaw-audit | `InMemoryAuditLogger.java` | Auto-stamp tenantId |
| jaiclaw (root) | `CLAUDE.md` | Add multi-tenancy conformance check |
| coachiq (root) | `CLAUDE.md` | Add multi-tenancy conformance check |
