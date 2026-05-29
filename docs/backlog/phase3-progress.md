# Phase 3 — P2 Agent & Orchestration — Progress Tracker

**Started:** —
**Status:** Not Started
**Depends on:** Phase 2 complete

## Work Items

### 3.1 Task Executor & Flow Engine
- **Location:** New module `extensions/jaiclaw-tasks/` — but leverage **Camel SEDA routes** (already available)
- **Status:** NOT STARTED
- **Revised estimate:** 1 week (down from 2+ weeks)
- **Depends on:** 2.5 (Plugin State Store)
- **Approach:** Use existing **Apache Camel SEDA routes** (already wired into JaiClaw via `jaiclaw-camel`) instead of building a custom task engine. Camel provides async multi-consumer queues, content-based routing, error handling with retry, dead-letter channels, and timer-based polling — all without adding dependencies.
- **What Camel already provides (zero new dependencies):**
  - SEDA queues for async task execution
  - Content-based routing for flow dispatch
  - Error handling with retry + dead-letter channels
  - Timer-based polling for scheduled checks
  - Transaction support
  - Already wired into JaiClaw via `jaiclaw-camel`
- **What to build:**
  - [ ] `TaskRecord.java`, `TaskStatus.java`, `TaskFlow.java` (domain records — ~50 lines)
  - [ ] Camel route that picks up task requests from a SEDA queue, executes them, updates status (~100 lines)
  - [ ] `CreateTaskTool.java`, `ListTasksTool.java`, `UpdateTaskTool.java` (LLM tools — ~150 lines)
  - [ ] Optional JPA persistence for task state (~100 lines)
  - [ ] Config + auto-configuration
  - [ ] Spock specs
- **No need to build:** Custom `TaskExecutor` with `CompletableFuture` management, custom `FlowRegistry`, in-memory registries — Camel handles execution orchestration.
- **Graduation path:** If durability requirements grow (process restart survival, saga compensation, human-in-the-loop), add **Temporal.io** (`io.temporal:temporal-spring-boot-starter:1.32.0`). This requires running a Temporal server. Defer until actually needed.
- **Multi-tenancy:** Same considerations — filter by tenant, ownerKey embeds session key.
- **Notes:** ~400 lines total with Camel approach.

### 3.2 Thread Ownership
- **Location:** Enhance `core/jaiclaw-agent/`
- **Status:** NOT STARTED
- **Revised estimate:** 3-5 days (unchanged — fully custom, domain-specific)
- **Approach:** Custom implementation. No library helps meaningfully — Camel/Spring Integration content-based routers are the wrong abstraction for @-mention tracking with TTL-based thread ownership.
- **Files to create:**
  - [ ] `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/ownership/ThreadOwnershipTracker.java`
  - [ ] `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/ownership/MentionDetector.java`
  - [ ] `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/ownership/OwnershipEntry.java`
  - [ ] `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/ownership/ThreadOwnershipConfig.java`
  - [ ] Spock specs
  - [ ] Integrate into `GatewayService` message routing
- **Notes:** ~150-200 lines. `ConcurrentHashMap` + regex mention parser + TTL expiry. Could use Caffeine (from 2.5) for the TTL map.

### 3.3 Webhook Event Routing
- **Location:** Enhance existing `core/jaiclaw-gateway/` (NOT a new module)
- **Status:** NOT STARTED
- **Revised estimate:** 1-2 days (down from 1-2 weeks)
- **Approach:** Mostly covered by existing infrastructure:
  - **Spring MVC `@PostMapping`** — already present in the gateway
  - **Camel Webhook component** — auto-registers webhook endpoints for Camel-integrated channels (Telegram, WhatsApp)
  - JaiClaw gateway already dispatches webhook payloads to channel adapters
- **What to build:**
  - [ ] `WebhookRoute.java` — configuration record
  - [ ] `WebhookRouteRegistry.java` — register routes from YAML config at startup
  - [ ] `WebhookController.java` — dynamic `@PostMapping` that dispatches to registered routes
  - [ ] `HmacVerifier.java` — HMAC-SHA256 signature verification utility
  - [ ] Spock specs
- **No need to build:** New module, new starter, custom authenticator framework — Spring MVC + Camel handle the HTTP layer.
- **Configuration:**
  ```yaml
  jaiclaw:
    webhooks:
      routes:
        - route-id: github-events
          path: /webhooks/github
          auth-type: hmac-sha256
          secret: ${GITHUB_WEBHOOK_SECRET}
          target-agent-id: default
  ```
- **Notes:** ~100-150 lines. Incremental addition per webhook source.

### 3.4 Admin HTTP RPC
- **Location:** Enhance `core/jaiclaw-gateway/`
- **Status:** NOT STARTED
- **Revised estimate:** 1-2 days (unchanged)
- **Approach:** Spring MVC `@RestController` + consider using Spring Boot Actuator's `@Endpoint` for native integration with the Actuator framework.
- **What Actuator already provides:** `/actuator/health`, `/actuator/info`, `/actuator/env`, `/actuator/beans`, `/actuator/metrics`.
- **Files to create:**
  - [ ] `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/admin/AdminController.java`
  - [ ] `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/admin/AdminSecurity.java`
  - [ ] Spock specs
  - [ ] Conditional on `jaiclaw.admin.enabled=true`
- **Endpoints:**
  - GET `/admin/sessions` — List active sessions
  - GET `/admin/sessions/{key}` — Session details
  - DELETE `/admin/sessions/{key}` — Terminate session
  - GET `/admin/channels` — Channel adapter status
  - POST `/admin/channels/{id}/restart` — Restart channel
  - GET `/admin/agents` — List agents
  - GET `/admin/tools` — List registered tools
  - GET `/admin/plugins` — List plugins
  - GET `/admin/metrics` — Runtime metrics summary
  - POST `/admin/broadcast` — Send message to all sessions
- **Notes:** ~200-300 lines.

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 3.1 Task Executor: Camel SEDA routes → 1 week (was 2+ weeks), defer Temporal
  - 3.3 Webhooks: Spring MVC + Camel → 1-2 days (was 1-2 weeks), no new module
