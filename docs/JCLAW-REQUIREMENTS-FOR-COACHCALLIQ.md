# JClaw — Architectural & Library Requirements for CoachCallIQ

What JClaw needs to add, extend, or harden to support the CoachCallIQ coaching platform

| | |
|---|---|
| **Version** | 1.3 |
| **Date** | March 2026 |
| **Repo** | https://github.com/glawson6/jclaw |
| **Status** | Planning |

## 1. JClaw's Responsibility in the CoachCallIQ Architecture

### 1.1 Where JClaw Sits

CoachCallIQ is built on three layered frameworks, each owning a distinct concern:

```
┌──────────────────────────────────────────────────────┐
│                  CoachCallIQ Platform                 │
│   Domain model, recruiting workflows, compliance,    │
│   video pipeline, assessments, admin, billing        │
├────────────────────┬─────────────────────────────────┤
│      JClaw         │           Embabel               │
│  Channel edges &   │   Complex agentic workflows &   │
│  gateway runtime   │   multi-step LLM orchestration  │
├────────────────────┴─────────────────────────────────┤
│              Spring AI / Spring Boot                 │
│         LLM abstraction, component model             │
└──────────────────────────────────────────────────────┘
```

### 1.2 What JClaw Owns — Definitively

JClaw is the edge and runtime layer. It is responsible for everything that happens between the outside world (coaches on their phones) and the domain logic (CoachCallIQ) or agentic workflows (Embabel). JClaw does not own business logic. It does not own agentic planning. It owns the infrastructure that makes all of that reachable and safe.

**JClaw's concrete responsibilities in CoachCallIQ:**

| Responsibility | Description |
|---|---|
| Channel Adapters | Receiving and sending messages via Telegram, Slack, Discord, Email, SMS. Detecting file attachments (PDFs, MP4s) and video URLs in messages. |
| Gateway & Routing | HTTP/WebSocket REST gateway. Routing inbound messages to the correct agent session. Attachment routing to ingestion pipelines. |
| Tenant Resolution | Resolving the tenant (coaching program) from every inbound request — JWT claim, bot token mapping, phone number lookup. Establishing TenantContext before any processing. |
| Session Management | Creating, retrieving, and expiring agent sessions. Scoping sessions to a tenant. Maintaining conversation continuity across messages. |
| Memory Store | Storing and retrieving conversational memory and entity memory. All reads and writes tenant-scoped. Vector similarity search partitioned by tenant. |
| Tool Registry & Execution | Registering tools contributed by CoachCallIQ modules. Executing tool calls during agent turns. Enforcing tool-level authorization. |
| Skill & Plugin Loading | Discovering and loading skills and plugins. Tenant-aware skill selection (e.g., D1 football compliance skill vs. D3 baseball skill). |
| MCP Server Hosting | Hosting the coachcalliq-resource-server MCP endpoint so the resource knowledge base is queryable by the JClaw agent and external tools. |
| Document Parsing Pipeline | Accepting PDFs and other documents from channel attachments, parsing and chunking them, embedding into vector memory. |
| Rate Limiting | Per-tenant request rate limiting at the gateway to prevent one program consuming all LLM quota. |
| Audit Logging | Recording every agent call, tool execution, and channel message per tenant for compliance and debugging. |
| Security | JWT validation, role/permission propagation, tool-level authorization enforcement. |
| Observability | Metrics, distributed tracing spans, and health checks across all JClaw modules. |
| Embabel Bridge | Hosting the AgentOrchestrationPort SPI so CoachCallIQ tool implementations can delegate complex workflows to Embabel without coupling to it. |

### 1.3 What JClaw Does NOT Own

It is equally important to be explicit about what JClaw is not responsible for, to prevent scope creep and architectural confusion.

| NOT JClaw's Responsibility | Who Owns It |
|---|---|
| Prospect domain model (Prospect, Evaluation, VideoReference) | coachcalliq-domain |
| Recruiting workflow logic (offer tracking, contact periods) | coachcalliq-recruiting |
| NCAA compliance rules engine | coachcalliq-compliance |
| Video frame extraction and vision LLM analysis | coachcalliq-video + jclaw-media (JClaw provides the pipeline SPI; CoachCallIQ provides the domain-specific prompts and storage) |
| Psychological assessment connectors | coachcalliq-assessments |
| Complex multi-step agentic planning (GOAP) | Embabel (coachcalliq-agents) |
| Per-step LLM selection across a workflow | Embabel |
| Billing and tenant onboarding UI | coachcalliq-admin |
| Business rules for who can contact which recruit | coachcalliq-compliance |

### 1.4 The Critical Boundary: JClaw vs Embabel

The most important architectural boundary to maintain:

- **JClaw** executes simple, single-turn tool calls: look up a prospect, log a contact, check a compliance flag. These complete within a single agent response cycle.
- **Embabel** executes multi-step agentic workflows that may span multiple LLM calls, replanning steps, and conditional branching: synthesize a full prospect evaluation, run a compliance audit, orchestrate a video analysis pipeline.

JClaw calls Embabel via the `AgentOrchestrationPort` SPI. Embabel never calls JClaw directly. The boundary is enforced by the SPI — if a tool needs more than one LLM call to produce its result, it should delegate to Embabel rather than accumulating complexity inside a JClaw tool.

### 1.5 Priority Classification

Requirements in this document are grouped by priority:

- **P0** — Blocking. CoachCallIQ cannot function without this.
- **P1** — High value. CoachCallIQ works around it but shouldn't have to.
- **P2** — Nice to have. Improves platform quality significantly.

## 2. Multi-Tenancy Support (P0)

This is the most critical gap. JClaw has no concept of a tenant today.

### 2.1 Tenant Context Propagation

JClaw needs a `TenantContext` that is established at the gateway/channel layer and propagated through the entire request chain — agent execution, tool calls, memory reads/writes, skill loading, and session management.

```java
// New interface in jclaw-core
public interface TenantContext {
    String getTenantId();
    String getTenantName();
    Map<String, Object> getMetadata();
}
```

```java
// Thread-local holder in jclaw-core
public class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    public static void set(TenantContext ctx) { CONTEXT.set(ctx); }
    public static TenantContext get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```

TenantContext must be set before any agent call and cleared after. Gateway and channel adapters are responsible for resolving it (from JWT, API key, bot token mapping, etc.).

### 2.2 Tenant-Scoped Memory

`jclaw-memory` must partition all vector store operations by `tenantId`. No tenant should ever retrieve memory records belonging to another tenant.

**Required changes:**

- Add `tenantId` as a mandatory metadata field on all memory records
- All `MemoryStore` search and retrieval methods must filter by current `TenantContextHolder.get().getTenantId()`
- Vector store namespacing strategy must be configurable (namespace-per-tenant vs. metadata filter)

### 2.3 Tenant-Scoped Sessions

`jclaw-agent` session management must scope sessions to a tenant. Session IDs should be globally unique but lookups must always be tenant-filtered.

**Required changes:**

- Session entity gets a `tenantId` field
- `SessionManager.getSession(sessionId)` enforces tenant match or throws
- `SessionManager.listSessions()` returns only sessions for the current tenant

### 2.4 Tenant-Scoped Tool Registry

Tools registered via plugins or skills need to be available globally but executed always within tenant context. Tool implementations must be able to access `TenantContextHolder` to scope their data operations.

No structural change needed — tools already run within a request. As long as TenantContext is propagated before tool execution (which 2.1 covers), this works.

## 3. Channel Extensions (P0)

### 3.1 Email Channel (jclaw-channel-email)

New module needed. Email is the primary channel for recruit outreach and is completely absent today.

**Requirements:**

- IMAP listener for inbound email (read messages, extract sender, subject, body)
- SMTP sender for outbound email
- Gmail OAuth2 support (MCP-compatible, following existing Gmail MCP pattern)
- Outlook/Exchange support (OAuth2 + Graph API)
- Email threading — maintain conversation context across reply chains
- Attachment handling — pass PDF attachments to the document ingestion pipeline
- Configurable polling interval (IMAP) vs. push (Gmail pub/sub)

**Channel config:**

```yaml
jclaw:
  channels:
    email:
      enabled: true
      provider: gmail   # gmail | outlook | imap
      polling-interval: 60s
      inbound-filter:
        folders: [INBOX, Recruiting]
```

### 3.2 SMS Channel (jclaw-channel-sms)

New module needed. Coaches text recruits constantly.

**Requirements:**

- Twilio adapter for inbound and outbound SMS
- Phone number → staff member identity resolution
- MMS support for media (photos from camps, etc.)
- Opt-out/opt-in compliance (TCPA)
- Configurable rate limiting to avoid carrier filtering

**Channel config:**

```yaml
jclaw:
  channels:
    sms:
      enabled: true
      provider: twilio
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      from-number: ${TWILIO_FROM_NUMBER}
```

## 4. Multimodal / Media Pipeline (P0)

### 4.1 Vision LLM Support in Tool Execution

`jclaw-tools` currently handles text tool calls only. CoachCallIQ needs tools that pass images or video frames to a vision-capable LLM.

**Required changes to jclaw-tools:**

- `ToolExecutionContext` must support passing binary media (images, video frames) alongside text
- Spring AI multimodal message construction should be available from within tool implementations
- Tool result type must support returning structured annotations alongside text

### 4.2 Media Ingestion Pipeline (New: jclaw-media)

A new optional module for async media processing.

**Requirements:**

- `MediaIngestionPort` SPI — accepts a media reference (URL or bytes), returns a `MediaAnalysisResult`
- Built-in implementation using Spring AI multimodal with GPT-4o Vision
- Pluggable alternative for local vision models via Ollama (LLaVA, BakLLaVA)
- Async execution with status callbacks (PENDING → PROCESSING → COMPLETE)
- Frame extraction for video (configurable interval, e.g., every 5 seconds)
- Results stored as structured annotations with timestamps

```java
// New SPI in jclaw-media
public interface MediaAnalysisProvider {
    CompletableFuture<MediaAnalysisResult> analyze(MediaInput input, AnalysisPrompt prompt);
}

public record MediaInput(String mediaUrl, MediaType type, Map<String, Object> metadata) {}

public record MediaAnalysisResult(
    List<TimestampedAnnotation> annotations,
    String summary,
    Map<String, Object> structuredData
) {}
```

## 5. Memory Enhancements (P1)

### 5.1 Structured Entity Memory

Today `jclaw-memory` is primarily conversational — it stores and retrieves message history and free-text observations. CoachCallIQ needs memory that is anchored to domain entities (a Prospect, an Evaluation, a Session).

**Required addition:**

- `EntityMemoryStore` — a memory store variant that links vector embeddings to a typed entity reference (entityType, entityId, tenantId)
- Enables queries like "retrieve all memory records about prospect prospectId=abc123"
- Distinct from session memory — entity memory persists beyond any single conversation

### 5.2 Memory TTL and Retention Policy

For compliance and data governance, CoachCallIQ needs to control how long memory records are retained.

**Required addition:**

- TTL field on memory records (optional, default = indefinite)
- Background retention policy enforcer (Spring `@Scheduled` based)
- Tenant-level default retention config

### 5.3 Memory Export

Programs need to export their data (FERPA requests, program transitions).

**Required addition:**

- `MemoryExportPort` SPI — export all memory records for a tenant as JSON/CSV
- Callable from a shell command or admin API endpoint

## 6. Plugin & Skill Enhancements (P1)

### 6.1 Tenant-Aware Skill Loading

Currently skills are loaded globally at startup. CoachCallIQ needs skills to be loadable per tenant — a D1 football program and a D3 baseball program need different compliance skills.

**Required changes to jclaw-skills:**

- `SkillLoader` must support tenant-scoped skill sets
- Skill selection strategy configurable per tenant (via tenant metadata or config)
- Skills can declare compatibility constraints (sport, division, ncaa-ruleset)

### 6.2 Skill Versioning

As NCAA rules change season to season, CoachCallIQ needs to update compliance skills without redeploying the platform.

**Required addition:**

- Skill version metadata (version, effectiveDate, expiryDate)
- `SkillRegistry` serves the correct version based on current date and tenant config
- Hot-reload of skills without gateway restart (low priority but valuable)

### 6.3 Plugin Isolation

CoachCallIQ will ship coaching-domain plugins that should be isolated from each other and from core JClaw operations. Currently the plugin SPI has no isolation guarantees.

**Required addition:**

- Plugin classloader isolation (optional, configurable)
- Plugin-level exception handling that prevents one plugin failure from crashing the agent

## 7. Gateway Enhancements (P1)

### 7.1 Tenant Routing at the Gateway

`jclaw-gateway` must resolve the tenant from each inbound request and set `TenantContext` before dispatching to the agent. Resolution strategies:

- **JWT claim** — `programId` in token claims (for REST/WebSocket)
- **Bot token mapping** — Telegram bot token → programId lookup table
- **Slack workspace** — Slack `team_id` → programId mapping
- **Discord server** — `guild_id` → programId mapping
- **Phone number** — SMS from-number → programId + staffId mapping

**Required addition:**

- `TenantResolver` SPI in `jclaw-gateway`
- Built-in implementations for JWT and bot-token-map strategies
- Tenant resolution failure → 401/reject message

### 7.2 Rate Limiting Per Tenant

SaaS requires preventing one tenant from consuming all LLM quota.

**Required addition:**

- Per-tenant rate limiter in the gateway (requests/minute, tokens/day)
- Configurable limits per subscription tier
- Rate limit exceeded → graceful message to user ("Your program has reached its daily limit")

### 7.3 Audit Logging

Every agent call, tool execution, and channel message must be audit-logged per tenant for compliance and debugging.

**Required addition:**

- `AuditEvent` model in `jclaw-core` (tenantId, staffId, eventType, timestamp, payload summary)
- `AuditLogger` SPI with a default database-backed implementation
- Configurable retention per tenant

## 8. Security Enhancements (P1)

### 8.1 JWT Authentication Support

`jclaw-gateway` needs a built-in Spring Security integration for JWT-based auth on REST and WebSocket endpoints.

**Required addition:**

- `jclaw-security` module (new) — Spring Security auto-configuration for JWT
- Token validation + TenantContext extraction from claims
- Role/permission propagation into the request context for tool-level authorization

### 8.2 Tool-Level Authorization

Some tools should only be callable by certain staff roles (e.g., only `COMPLIANCE_ADMIN` can override a compliance flag).

**Required addition:**

- `@RequiresPermission("COMPLIANCE_ADMIN")` annotation on tool implementations
- `ToolExecutionInterceptor` that checks current user permissions before executing a tool
- Permission denied → informative agent response rather than exception

## 9. Observability (P2)

### 9.1 Metrics

**Required addition:**

- Micrometer integration in `jclaw-agent`, `jclaw-tools`, `jclaw-gateway`
- Key metrics: agent latency, tool execution count/latency, LLM token usage per tenant, channel message volume
- Prometheus endpoint

### 9.2 Distributed Tracing

**Required addition:**

- OpenTelemetry integration across all modules
- Trace spans for: message received → tenant resolved → agent called → tools executed → response sent
- Trace IDs in audit logs for correlation

### 9.3 Health Checks

**Required addition:**

- Spring Boot Actuator health indicators per channel (Telegram connected, Slack connected, etc.)
- LLM provider health check (can we reach the model?)
- Memory store health check

## 10. Configuration & Operations (P2)

### 10.1 Dynamic Configuration

CoachCallIQ needs to update tenant configuration (LLM provider, channel tokens, skill sets) without restarting.

**Required addition:**

- `JClawConfigRefresh` event — signal the runtime to reload config for a specific tenant
- `@RefreshScope` support on key beans (channel adapters, LLM provider config)

### 10.2 Admin API

A programmatic API for managing JClaw instances at the platform level.

**Required addition:**

- REST endpoints (in `jclaw-gateway`) for: list tenants, get tenant status, reload tenant config, list active sessions, flush memory for tenant
- Secured by an ADMIN role separate from tenant-level roles

## 11. MCP Resource Server Support (P0)

CoachCallIQ's resource abstraction requires JClaw to support hosting an MCP server alongside the agent gateway — not just consuming MCP servers as a client. This is a meaningful architectural addition.

### 11.1 MCP Server Hosting in jclaw-gateway

Today JClaw acts as an MCP client (tools call out to MCP servers). For the resource abstraction, CoachCallIQ needs JClaw's gateway to also host an MCP server endpoint that external agents, staff tools, or the JClaw agent itself can query.

**Required addition to jclaw-gateway:**

- `McpServerEndpoint` — a configurable SSE or HTTP endpoint at `/mcp/{serverName}` exposing a registered set of MCP tools
- `McpToolProvider` SPI — implemented by modules (like `coachcalliq-resources`) to contribute tools to the hosted MCP server
- Tenant scoping — every MCP tool call is intercepted, TenantContext resolved from the request, and passed through to the tool implementation
- Auth — MCP endpoint secured by the same JWT mechanism as the REST gateway

```yaml
jclaw:
  mcp:
    server:
      enabled: true
      path: /mcp
      servers:
        - name: resources
          description: "CoachCallIQ resource knowledge base"
```

### 11.2 McpToolProvider SPI in jclaw-tools

A new SPI that allows any JClaw or CoachCallIQ module to contribute tools to a hosted MCP server, separate from the agent's internal tool registry:

```java
public interface McpToolProvider {
    String getServerName();
    List<McpToolDefinition> getTools();
    McpToolResult execute(String toolName,
                          Map<String, Object> args,
                          TenantContext tenant);
}
```

`coachcalliq-resources` implements `McpToolProvider` to expose `resource_search`, `resource_ingest`, `resource_get`, and `resource_list`.

### 11.3 Document Parsing & Embedding Pipeline

The resource ingestion pipeline requires the following additions to JClaw:

**New module `jclaw-documents`** — document parsing abstraction:

- PDF text extraction (Apache PDFBox or Tika)
- HTML/web page body extraction (Jsoup)
- Plain text passthrough
- Configurable chunking strategy (fixed-size, sentence-boundary, paragraph)

**`EmbeddingPipeline` in `jclaw-memory`** — low-level API for chunking, embedding, and storing arbitrary text with custom metadata:

```java
public interface EmbeddingPipeline {
    List<String> chunk(String text, ChunkingStrategy strategy);
    List<float[]> embed(List<String> chunks);
    void store(List<EmbeddableChunk> chunks, TenantContext tenant);
}
```

**`BackgroundTaskExecutor`** — async ingestion jobs submitted without blocking the agent response path. Backed by Spring `@Async` with a configurable thread pool.

## 12. PDF Upload via Messaging Channels (P0)

This is a critical usability requirement. A scout should be able to send a PDF directly inside Telegram or SMS — exactly the same way they would send a photo — and have it automatically ingested as a resource attached to a prospect. There should be no separate upload portal, no web UI, no friction.

### 12.1 The Scout Workflow

```
Scout (on Telegram):
  [attaches PDF file: "Marcus_Thompson_Scouting_Report.pdf"]
  "Upload this for Marcus Thompson"

JClaw Agent:
  "Got it — I've attached the scouting report to Marcus Thompson's profile
   and it will be searchable within a few minutes."
```

Or even simpler — the agent infers context from the filename and active session:

```
Scout:
  [attaches PDF: "Marcus_Thompson_Camp_Report.pdf"]

Agent:
  "I see a scouting report for Marcus Thompson. Attaching to his profile now.
   Any notes you want to add before I index it?"
```

### 12.2 What JClaw Channel Adapters Need to Support

Every channel adapter that accepts file attachments must implement `AttachmentHandler` — a new interface in `jclaw-channel-api`:

```java
public interface AttachmentHandler {
    boolean supports(AttachmentType type);
    AttachmentPayload extract(ChannelMessage message);
}

public record AttachmentPayload(
    String filename,
    AttachmentType type,       // PDF | IMAGE | VIDEO | AUDIO | DOCUMENT
    byte[] bytes,
    String mimeType,
    long sizeBytes
) {}
```

**Telegram adapter changes** (`jclaw-channel-telegram`):

- Detect `document` message type in the Telegram update payload
- Download the file bytes via Telegram's `getFile` API
- Wrap in `AttachmentPayload` and forward to `ChannelMessageDispatcher` alongside the text context
- Support files up to Telegram's 20MB bot API limit (flag larger files for direct upload)

**SMS adapter changes** (`jclaw-channel-sms` — new):

- Handle MMS attachments via Twilio's `MediaUrl` field
- Download media from Twilio's CDN
- Same `AttachmentPayload` wrapping

**Email adapter changes** (`jclaw-channel-email` — new):

- Parse MIME multipart messages for attachments
- Support inline PDFs and attached PDFs
- Strip email footers/signatures before passing body text as context

### 12.3 Attachment Routing in jclaw-gateway

When a `ChannelMessage` arrives carrying an `AttachmentPayload`, the gateway must route it differently from a plain text message. A new `AttachmentRouter` in `jclaw-gateway`:

```java
public interface AttachmentRouter {
    void route(AttachmentPayload attachment,
               ChannelMessage context,
               TenantContext tenant);
}
```

**Default routing rules:**

- **PDF** → `DocumentIngestionPipeline` (extract text → chunk → embed → store as Resource)
- **IMAGE** → `MediaAnalysisProvider` (vision LLM analysis)
- **VIDEO** → `MediaAnalysisProvider` (frame extraction + vision LLM)
- **AUDIO** → `SpeechTranscriptionProvider` (transcribe → store as Resource)

Each route is configurable and pluggable. CoachCallIQ overrides the default PDF route to also link the resource to the prospect inferred from message context.

### 12.4 Prospect Linking from Context

When a PDF is uploaded, the agent needs to infer which prospect to link it to. Three resolution strategies, tried in order:

1. **Explicit mention** — scout says "for Marcus Thompson" → name-match against tenant's prospect list
2. **Filename parsing** — `Marcus_Thompson_Report.pdf` → extract name tokens, fuzzy match against prospects
3. **Session context** — if the conversation was already about a specific prospect, link to them
4. **Fallback** — ingest as unlinked resource, ask the scout to confirm the prospect

## 13. New Modules Summary

| Module | Priority | Description |
|---|---|---|
| `jclaw-channel-email` | P0 | Email channel adapter (Gmail, Outlook, IMAP/SMTP) with attachment handling |
| `jclaw-channel-sms` | P0 | SMS/MMS channel adapter (Twilio) with PDF/image attachment handling |
| `jclaw-media` | P0 | Async media/video ingestion + vision LLM analysis pipeline |
| `jclaw-documents` | P0 | Document parsing (PDF, HTML, text), chunking, embedding pipeline |
| `jclaw-security` | P1 | JWT auth, tenant resolution, Spring Security auto-config |
| `jclaw-audit` | P1 | Audit logging SPI + default database implementation |

## 14. Existing Module Changes Summary

| Module | Changes Required |
|---|---|
| `jclaw-core` | TenantContext, TenantContextHolder, AuditEvent, entity memory model |
| `jclaw-memory` | Tenant partitioning, entity memory store, EmbeddingPipeline, TTL/retention, export SPI |
| `jclaw-agent` | Tenant-scoped sessions, tenant context propagation through execution |
| `jclaw-tools` | Multimodal tool context, tool-level authorization interceptor, McpToolProvider SPI, AgentOrchestrationPort SPI |
| `jclaw-skills` | Tenant-aware skill loading, skill versioning, compatibility constraints |
| `jclaw-plugin-sdk` | Plugin classloader isolation, plugin-level exception handling |
| `jclaw-channel-api` | AttachmentHandler SPI, AttachmentPayload, AttachmentType enum |
| `jclaw-channel-telegram` | AttachmentHandler implementation — PDF, image, video download + routing |
| `jclaw-gateway` | Tenant routing, TenantResolver SPI, AttachmentRouter, MCP server hosting, rate limiting, audit hooks, admin API |
| `jclaw-gateway-app` | Micrometer + OTel integration, Actuator health indicators |
| `jclaw-spring-boot-starter` | Wire all new auto-configurations, conditional enablement, conditional Embabel wiring via `@ConditionalOnClass` |

## 15. Recommended Build Order

Given dependencies between the changes, the recommended implementation sequence:

1. **TenantContext in jclaw-core** — everything else depends on this
2. **Tenant partitioning in jclaw-memory** — needed before any real data flows
3. **TenantResolver SPI in jclaw-gateway** — needed to establish context at the edge
4. **jclaw-security module** — JWT auth before any multi-program pilot
5. **AttachmentHandler SPI in jclaw-channel-api** — enables PDF upload from day one
6. **PDF attachment support in jclaw-channel-telegram** — scouts can send PDFs immediately
7. **jclaw-documents module** — PDF parsing, chunking, embedding pipeline
8. **MCP server hosting in jclaw-gateway** — resource server exposed via MCP
9. **jclaw-channel-email** — high coach demand, buildable in parallel with above
10. **jclaw-channel-sms** — MMS PDF support follows same pattern as Telegram
11. **jclaw-media** — video pipeline, most complex, build after document pipeline is proven
12. **Skill versioning + tenant-aware loading** — needed before compliance skill goes live
13. **Audit logging** — compliance requirement, needed before enterprise sales
14. **Observability (metrics, tracing, health)** — needed for production operations
15. **Embabel AgentOrchestrationPort + conditional auto-config** — enables complex workflow delegation; add after core platform is stable
