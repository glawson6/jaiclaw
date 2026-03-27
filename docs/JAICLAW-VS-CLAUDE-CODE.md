# JaiClaw vs. Claude Code: Different Tools for Different Jobs

**Claude Code builds your application. JaiClaw IS your application.**

---

## Executive Summary

Claude Code (March 2026) is Anthropic's autonomous AI coding agent — the most capable AI-powered software engineering tool available. JaiClaw is a Java-native AI agent application runtime. They are **complementary, not competing.** Claude Code operates at development time and in CI/CD; JaiClaw operates at application runtime serving end users.

The confusion arises because both are called "AI agents." But they solve fundamentally different problems:

| | Claude Code | JaiClaw |
|---|---|---|
| **What it is** | AI coding assistant / development tool | AI agent application runtime |
| **When it runs** | Development time + CI/CD | Production runtime (24/7) |
| **Who uses it** | Developers building software | End users interacting with your AI application |
| **How it deploys** | CLI on developer machines, headless in CI | Spring Boot app on Docker/K8s serving users |
| **Language** | TypeScript (internal), Python/TS SDKs | Java 21 / Spring Boot 3.5 |

Think of it this way: **Claude Code is the carpenter. JaiClaw is the house.**

---

## What Claude Code Does Well (March 2026)

Claude Code has evolved into a powerful autonomous development platform:

- **Autonomous coding** — Reads, writes, refactors, and tests code across entire codebases
- **Voice mode** — Push-to-talk interaction in 20 languages
- **`/loop` command** — Interval-based task execution (e.g., `/loop 5m check the deploy`)
- **1M token context window** — On Max, Team, and Enterprise plans with Opus 4.6
- **10 simultaneous sub-agents** — Parallel workers each with independent context
- **Git worktree integration** — Each sub-agent works on an isolated code copy
- **Headless mode** — Non-interactive execution for CI/CD pipelines
- **Hooks** — Lifecycle scripts (before/after tool calls, session events)
- **MCP servers** — 3,000+ external integrations (databases, GitHub, Sentry, etc.)
- **Computer use** — Opens files, runs tools, navigates screens
- **Remote control** — Send instructions from mobile to desktop

These are genuinely impressive capabilities for **software development.** But they are development-time capabilities, not application-runtime capabilities.

---

## Where JaiClaw Goes and Claude Code Can't Follow

### 1. Cron Scheduling — Autonomous Agent Execution

**Claude Code:** The `/loop` command runs a prompt at fixed intervals (e.g., every 5 minutes). It requires Claude Code to be running. There is no persistent job store, no retry policy, no cluster-aware scheduling, no timezone support, no CRON expressions. If Claude Code exits, the loop stops.

**JaiClaw:** Two-tier cron system with production-grade scheduling.

```java
// Tier 1: CronService — lightweight, in-process
CronJob job = new CronJob(
    "daily-briefing",
    "Morning Recruiting Briefing",
    "default",                          // agent ID
    "0 6 * * MON-SAT",                 // standard CRON expression
    "America/New_York",                 // timezone-aware
    "Generate today's recruiting briefing...",
    "telegram",                         // delivery channel
    null, true, null, null
);
cronService.addJob(job);

// Tier 2: CronManager — Spring Batch + H2, queryable run history
// Manages via MCP tools: create_job, list_jobs, update_job, delete_job, job_history
```

JaiClaw cron jobs:
- Survive application restarts (H2-backed persistence)
- Support standard 5-field CRON expressions with timezone
- Execute on virtual threads for high concurrency
- Deliver results to any channel (Telegram, Slack, Email, SMS)
- Are managed by the agent itself via MCP tools (the agent can create its own scheduled tasks)
- Track run history with success/failure status

**Bottom line:** Claude Code can poll. JaiClaw can schedule production workloads.

---

### 2. Spring Boot Integration — Native vs. External

**Claude Code:** Can generate Spring Boot code. A `spring-boot-engineer` sub-agent exists. Spring AI's `spring-ai-agent-utils` reimplements some Claude Code capabilities as Spring AI tools. But Claude Code does not **run inside** a Spring Boot application. It is an external process that modifies files.

**JaiClaw:** IS a Spring Boot application. Every JaiClaw feature is a Spring Boot auto-configured component.

```
Claude Code                          JaiClaw
─────────                            ──────
External process                     @SpringBootApplication
Reads/writes files                   @ConfigurationProperties
Runs shell commands                  @ConditionalOnBean / @ConditionalOnClass
Generates Spring code                @AutoConfiguration (3-phase ordered)
                                     @ConditionalOnMissingBean (every bean overridable)
                                     Spring AI ChatClient (native bridge)
                                     Spring Data (Redis, JPA)
                                     Spring Security (JWT)
                                     Spring Boot Actuator (health, metrics)
                                     Spring Batch (cron job management)
```

JaiClaw components participate in Spring's dependency injection, lifecycle management, transaction handling, and configuration system. They are not external processes calling into your app — they ARE your app.

**Bottom line:** Claude Code works WITH Spring Boot. JaiClaw works AS Spring Boot.

---

### 3. Multi-Channel Messaging — Built-in vs. Integrated

**Claude Code:** Connects to external services via MCP servers. There are MCP servers for Slack, Telegram, etc. These are integrations — Claude Code can send a message by calling an MCP tool. It does not receive inbound messages from users on those channels. It does not route conversations. It does not maintain per-user sessions across channels.

**JaiClaw:** Six native channel adapters with bidirectional messaging, session isolation, and cross-channel identity linking.

| Channel | Inbound | Outbound | Development Mode | Production Mode |
|---------|---------|----------|-----------------|-----------------|
| Telegram | Long polling / webhook | Bot API | No public URL needed | Webhook |
| Slack | Socket Mode / Events API | `chat.postMessage` | Socket Mode | Events API |
| Discord | Gateway WebSocket | REST API | WebSocket | Interactions |
| Email | IMAP polling | SMTP | Polling | Polling |
| SMS | Twilio webhook | Twilio Messages API | Webhook (ngrok) | Webhook |
| Signal | HTTP polling / daemon | REST / JSON-RPC | Polling | Daemon |

Every adapter normalizes platform-specific formats into `ChannelMessage` records. File attachments, platform metadata, and routing are handled automatically. `IdentityLinkService` resolves the same person across Telegram, Slack, and Email to a single canonical identity — shared conversation history, shared memory.

**Bottom line:** Claude Code can call messaging APIs. JaiClaw IS a messaging platform.

---

### 4. Multi-Tenancy — Platform-Level vs. Application-Level

**Claude Code:** Anthropic Enterprise supports "Tenant Restrictions" — controlling which users/organizations can access Claude. This is access control for Claude itself, not data isolation within your application. There is no concept of one Claude Code instance serving multiple client organizations with isolated data.

**JaiClaw:** Architectural multi-tenancy propagated through every layer.

```
JWT Token
  ├── tenantId: "university-of-georgia-football"
  ├── roles: ["HEAD_COACH"]
  ├── staffId: "coach-smith"
  │
  └── TenantContextHolder (ThreadLocal)
        ├── Session isolation:  {agentId}:{channel}:{tenantId}:{peerId}
        ├── Memory isolation:   queries scoped by tenantId
        ├── Skill filtering:    TenantSkillRegistry per-tenant
        ├── Audit tagging:      every AuditEvent carries tenantId
        ├── Tool profiling:     HEAD_COACH → FULL, STUDENT_ASSISTANT → MINIMAL
        └── Billing:            subscription plan per tenant
```

A single JaiClaw deployment can serve 100 university football programs. Each sees only their own prospects, conversations, audit trails, and skills. A misconfigured query cannot leak data because isolation is enforced at the framework level, not the application level.

**Bottom line:** Claude Code has user access control. JaiClaw has application-level data isolation.

---

### 5. Security & Authentication — User Auth vs. Application Auth

**Claude Code:** SAML 2.0, OIDC SSO, MFA, TLS 1.2+, AES-256 encryption. These secure access to Claude Code itself. They do not provide authentication for the application you build.

**JaiClaw:** Three security layers for your application's users.

| Layer | What It Does |
|-------|-------------|
| **JWT tenant resolution** | Extracts `tenantId`, roles, staffId from JWT. Maps roles to tool profiles (MINIMAL → FULL). |
| **Per-sender rate limiting** | HTTP 429 with `Retry-After`, identified by JWT subject or IP. Virtual thread cleanup. |
| **ECDH cryptographic handshake** | P-256/X25519 key exchange for agent-to-MCP-server authentication. HMAC-SHA256 challenge-response. |

**Bottom line:** Claude Code secures developer access. JaiClaw secures end-user access.

---

### 6. Audit Trails — Usage Logging vs. Business Audit

**Claude Code:** Enterprise audit logging tracks who prompted Claude, when, and what tools were used. Zero-Data-Retention (ZDR) option available. This is usage auditing for the development tool.

**JaiClaw:** Formal business audit trail for your application.

```java
public record AuditEvent(
    String id,           // UUID
    Instant timestamp,
    String tenantId,     // which organization
    String actor,        // which user
    String action,       // what they did
    String resource,     // what it affected
    AuditOutcome outcome, // SUCCESS / FAILURE / DENIED
    Map<String, String> details
) {}
```

Every tool call, agent action, message sent, and security event is audited with tenant context and outcome. The `AuditLogger` SPI supports pluggable backends — in-memory for development, database-backed for production. Designed for SOC 2, HIPAA, and NCAA compliance frameworks.

**Bottom line:** Claude Code logs developer activity. JaiClaw logs your application's business activity.

---

### 7. Persistence & State Management

**Claude Code:** Operates on files and shell commands. No ORM, no connection pooling, no transaction management. It can interact with databases via MCP servers, but it manages no persistent state of its own beyond conversation history.

**JaiClaw:** Full state management stack.

| Layer | Implementation |
|-------|---------------|
| Session state | In-memory (dev) or Redis (production) |
| Conversation transcripts | JSONL per session key, survives restarts |
| Semantic search | BM25 (in-memory) or Spring AI VectorStore (pgvector, Pinecone, etc.) |
| Document store | Chunking pipeline with full-text, vector, and hybrid search |
| Workspace memory | Persistent Markdown files per tenant |
| Daily logs | Timestamped daily conversation logs |
| Cron job state | H2 database with run history |
| Audit events | Pluggable SPI (in-memory or database) |
| Calendar events | CalendarProvider SPI (in-memory or Redis) |

**Bottom line:** Claude Code reads and writes files. JaiClaw manages application state.

---

### 8. Plugin System & Extensibility

**Claude Code:** Hooks (before/after tool calls, session events), MCP servers for tool integration, custom sub-agents, skills via `CLAUDE.md` files.

**JaiClaw:** 16-hook plugin system with three discovery sources.

| Hook Phase | JaiClaw Hooks | Claude Code Equivalent |
|-----------|--------------|----------------------|
| Model resolution | `BEFORE_MODEL_RESOLVE` | None |
| Prompt construction | `BEFORE_PROMPT_BUILD` | None |
| Agent lifecycle | `BEFORE_AGENT_START`, `AGENT_END` | Session start/end hooks |
| LLM I/O | `LLM_INPUT`, `LLM_OUTPUT` | None |
| Context compaction | `BEFORE_COMPACTION`, `AFTER_COMPACTION` | None |
| Session lifecycle | `SESSION_START`, `SESSION_END`, `BEFORE_RESET` | Session hooks |
| Message pipeline | `MESSAGE_RECEIVED`, `MESSAGE_SENDING`, `MESSAGE_SENT` | None |
| Tool execution | `BEFORE_TOOL_CALL`, `AFTER_TOOL_CALL` | Before/after tool hooks |

JaiClaw plugins are discovered via three sources: Spring component scanning, Java `ServiceLoader`, and programmatic registration. Plugins can register tools, intercept hooks, and access plugin-specific configuration. Void hooks execute in parallel on virtual threads; modifying hooks execute sequentially in priority order.

**Bottom line:** Both have hooks. JaiClaw's cover the full application lifecycle; Claude Code's cover the development workflow.

---

### 9. GOAP Planning vs. ReAct

**Claude Code:** Uses ReAct-style reasoning — the LLM decides the next action at each step. Non-deterministic. The same prompt may produce different tool call sequences.

**JaiClaw (with Embabel):** GOAP (Goal-Oriented Action Planning) via A* search.

| | Claude Code (ReAct) | JaiClaw + Embabel (GOAP) |
|---|---|---|
| Planning | LLM decides next step | A* finds optimal sequence upfront |
| Determinism | Non-deterministic | Deterministic given same preconditions |
| Observability | Log inspection | Typed blackboard with intermediate states |
| Correctness | Hope-based | Preconditions and effects are compiler-enforced |
| Replay | Not possible | Same preconditions → same plan → same execution |
| Parallelism | Sub-agents (manual) | Planner identifies parallelizable actions automatically |

```java
@Agent
public class ComplianceAgent {
    @Action(precondition = "String", effect = "PolicyDocument")
    public PolicyDocument extractPolicy(String rawPolicy) { ... }

    @Action(precondition = {"PolicyDocument", "String"}, achievesGoal = true)
    public ComplianceReport checkCompliance(PolicyDocument policy,
                                             String targetDocument) { ... }
}
// GOAP planner automatically sequences: extractPolicy → checkCompliance
```

**Bottom line:** Claude Code is brilliant at figuring out what to do next. JaiClaw can prove what it will do before it does it.

---

### 10. Skills System

**Claude Code:** `CLAUDE.md` files provide project-level instructions. Custom sub-agents can be defined with specialized prompts.

**JaiClaw:** Markdown-based skills with eligibility checking, tenant scoping, and version management.

```yaml
---
alwaysInclude: false
platforms: [darwin, linux]
requiredBins: [kubectl]
version: "1.0"
tenantIds: ["university-of-georgia"]
---
# K8s Monitoring Triage Workflow
When an alert fires...
```

JaiClaw skills support:
- Platform-specific eligibility (OS, required binaries)
- Per-tenant filtering (different schools see different skills)
- Version management with cache invalidation
- Non-engineer authoring (behavioral changes via Markdown, no code)
- Injected into system prompt by `SystemPromptBuilder`

**Bottom line:** Both use Markdown for behavioral instructions. JaiClaw adds eligibility, versioning, and multi-tenant scoping.

---

## The Complementary Relationship

The most productive setup is **Claude Code building JaiClaw applications:**

```
Development Time                          Production Runtime
─────────────────                         ──────────────────

Claude Code                               JaiClaw
  │                                         │
  ├── Writes Java code                      ├── Serves end users
  ├── Generates Spring Boot configs         ├── Routes messages across channels
  ├── Creates Spock tests                   ├── Manages sessions & memory
  ├── Refactors modules                     ├── Enforces multi-tenancy
  ├── Reviews pull requests                 ├── Executes cron jobs
  ├── Debugs issues                         ├── Audits every action
  └── Deploys to K8s                        └── Runs 24/7 on your infrastructure
```

Claude Code is the best tool for building JaiClaw applications. JaiClaw is the best runtime for deploying them.

---

## Summary Table

| Capability | Claude Code (March 2026) | JaiClaw |
|-----------|--------------------------|---------|
| **Primary purpose** | AI-powered software development | AI agent application runtime |
| **Runs when** | Development / CI/CD | Production (24/7) |
| **Language** | TypeScript/Python SDKs | Java 21 / Spring Boot 3.5 |
| **Scheduling** | `/loop` (interval polling, non-persistent) | 2-tier CRON (persistent, timezone, virtual threads) |
| **Spring integration** | Generates Spring code externally | IS a Spring Boot application |
| **Messaging channels** | Via MCP integrations (outbound) | 6 native adapters (bidirectional) |
| **Multi-tenancy** | Platform access control | Architectural data isolation (JWT → ThreadLocal → every layer) |
| **Security** | SSO/MFA for developer access | JWT + rate limiting + ECDH for application users |
| **Audit** | Developer usage logging | Business event audit trail (SOC 2/HIPAA capable) |
| **Persistence** | File system | Redis sessions, vector stores, document stores, H2, JSONL |
| **Plugin hooks** | ~4 lifecycle hooks | 16 lifecycle hooks across full agent cycle |
| **Planning** | ReAct (LLM-driven, non-deterministic) | GOAP via Embabel (A*-planned, deterministic) |
| **Skills** | `CLAUDE.md` project files | Versioned, tenant-scoped, eligibility-checked Markdown |
| **Sub-agents** | Up to 10 parallel workers | Embabel GOAP with automatic parallelism detection |
| **MCP** | 3,000+ server integrations | Client (3 transports) + Server (expose tools to external LLMs) |
| **Calendar** | None built-in | CalendarService with 8 MCP tools (events, scheduling, availability) |
| **Identity** | Per-developer | Cross-channel canonical identity (same user on Telegram + Slack + Email) |
| **Billing** | Anthropic subscription | Stripe/PayPal/Telegram Stars per-tenant billing |
| **Deployment** | CLI + headless mode | Docker + Kubernetes + Helm + JKube |
| **Context management** | 1M token window | Automatic compaction at 80% with identifier preservation |
| **Voice** | Push-to-talk (20 languages) | STT transcription (Whisper) for voice memos |
| **Browser** | Computer use (screen control) | 8 Playwright tools (navigate, click, type, screenshot, evaluate) |
| **Scaling model** | More sub-agents per developer | Horizontal K8s scaling across gateway + app tiers |

---

## One Sentence

**Claude Code is the most powerful AI development tool ever built. JaiClaw is the most complete Java AI agent runtime ever built. Use Claude Code to build your JaiClaw application, then deploy JaiClaw to serve your users.**

---

*March 2026*
