# JaiClaw: From Personal AI Assistant to Enterprise Agentic Platform

**One framework. Every scale. Zero platform changes.**

---

## The Spectrum

Most AI agent tools force a choice: a lightweight personal tool that can't scale, or an enterprise platform that's overkill for simple tasks. JaiClaw spans the entire range — the same codebase, the same modules, the same deployment model — from a developer's local terminal to a multi-tenant platform serving thousands of users across dozens of organizations.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         JaiClaw Application Spectrum                         │
│                                                                              │
│  Personal         Team             Department          Enterprise            │
│  ──────────────── ──────────────── ──────────────────  ────────────────────  │
│  Spring Shell     Single-channel   Multi-channel       Multi-tenant          │
│  CLI on laptop    bot for a team   gateway + cron      GOAP + K8s            │
│                                                                              │
│  1 user           5-50 users       50-500 users        Unlimited             │
│  0 channels       1 channel        3-7 channels        All 7 channels        │
│  No auth          API key          JWT roles            JWT multi-tenancy     │
│  In-memory        In-memory        Redis sessions       Redis + PostgreSQL   │
│  No audit         Minimal          Full audit trail     SOC 2 / HIPAA        │
│  ReAct loop       ReAct loop       ReAct + cron         GOAP multi-agent     │
│  1 LLM provider   1-2 providers    2-3 providers        Any of 11 providers  │
│                                                                              │
│  Modules: 4-5     Modules: 8-12    Modules: 18-25      Modules: 30-65       │
└──────────────────────────────────────────────────────────────────────────────┘
```

The key insight: **you never rewrite.** You add modules. The same `AgentRuntime` that powers a personal CLI assistant is the same runtime that powers an enterprise multi-tenant platform. You compose more modules around it.

---

## Level 1: Personal AI Assistant

**Who:** A single developer, researcher, or professional who wants an AI assistant on their machine.

**What you get:** A Spring Shell CLI application with tools for file read/write, shell execution, web search, web fetch, and context window compaction. Conversations are persistent. Memory survives restarts. No server. No Docker. No cloud.

**Starter:**
```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-personal-assistant</artifactId>
    <type>pom</type>
</dependency>
```

This single POM gives you: Anthropic (Claude) as the LLM provider, Spring Shell CLI, and 59 bundled skills (coding, web-research, system-admin, conversation, summarize, and dozens more).

**Choose your LLM provider** — 11 providers supported via dedicated starters:

| Provider | Starter | Credentials |
|----------|---------|-------------|
| Anthropic (default) | `jaiclaw-starter-anthropic` | `ANTHROPIC_API_KEY` |
| OpenAI | `jaiclaw-starter-openai` | `OPENAI_API_KEY` |
| Ollama (local, free) | `jaiclaw-starter-ollama` | *(none)* |
| Google Gemini | `jaiclaw-starter-gemini` | `GEMINI_API_KEY` |
| AWS Bedrock | `jaiclaw-starter-bedrock` | AWS credentials |
| Azure OpenAI | `jaiclaw-starter-azure-openai` | `AZURE_OPENAI_API_KEY` |
| DeepSeek | `jaiclaw-starter-deepseek` | `DEEPSEEK_API_KEY` |
| Mistral AI | `jaiclaw-starter-mistral` | `MISTRAL_API_KEY` |
| MiniMax | `jaiclaw-starter-minimax` | `MINIMAX_API_KEY` |
| Vertex AI | `jaiclaw-starter-vertex-ai` | GCP ADC |
| OCI GenAI | `jaiclaw-starter-oci-genai` | OCI config |

**Run it:**
```bash
./mvnw spring-boot:run
> /chat What's in my project?
```

**Modules used (~5):**
- `jaiclaw-tools` — File I/O, shell exec, web search, web fetch
- `jaiclaw-compaction` — Automatic context window management with identifier preservation
- `jaiclaw-skills` — 59 bundled behavioral skills (whitelist only what you need)
- `jaiclaw-shell` — Interactive Spring Shell CLI (via `jaiclaw-starter-shell`)
- `jaiclaw-spring-boot-starter` — Auto-configuration

**JaiClaw example:** [**Research Assistant**](../jaiclaw-examples/research-assistant/) — A multi-iteration research agent with tool loops, context compaction for managing large research contexts, and workspace memory for persisting findings across sessions.

**What makes this more than a chatbot wrapper:** Session persistence, context compaction with identifier preservation (file paths and UUIDs survive summarization), workspace memory (`MEMORY.md`), and daily log files. Your assistant remembers yesterday's conversation. The `jaiclaw-prompt-analyzer` CLI tool estimates token usage so you can tune which skills and tools are active.

---

## Level 2: Team Bot

**Who:** A small team (5-50 people) that wants a shared AI assistant on a single messaging channel.

**What you add:** One channel adapter, the gateway, and optionally voice transcription and OAuth credential management.

**Configuration:**
```yaml
jaiclaw:
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
  skills:
    allow-bundled:
      - web-research
      - summarize
      - conversation
```

**Modules added (+5, total ~8-12):**
- `jaiclaw-channel-telegram` — Telegram Bot API adapter (or Slack, Discord, SMS, Email, Signal, Teams)
- `jaiclaw-gateway` — REST API + WebSocket + webhook routing
- `jaiclaw-voice` — Voice memo transcription (Whisper STT) and text-to-speech
- `jaiclaw-identity` — OAuth credential management, cross-provider auth rotation, external CLI sync (Claude, Codex, Qwen, MiniMax)
- `jaiclaw-docstore` — Document storage, indexing, and retrieval

**7 channel adapters** — all follow the same `ChannelAdapter` SPI:

| Channel | Module | No Public URL? | Best For |
|---------|--------|---------------|----------|
| Telegram | `jaiclaw-channel-telegram` | Polling mode | Mobile UX, voice memos |
| Slack | `jaiclaw-channel-slack` | Socket Mode | Team collaboration |
| Discord | `jaiclaw-channel-discord` | Gateway WS | Community bots |
| Email | `jaiclaw-channel-email` | IMAP polling | Async, document intake |
| SMS | `jaiclaw-channel-sms` | *(webhook)* | Mobile outreach |
| Signal | `jaiclaw-channel-signal` | *(bridge)* | Privacy-focused |
| Teams | `jaiclaw-channel-teams` | *(webhook)* | Enterprise Microsoft |

**JaiClaw examples at this level:**

- [**Meeting Assistant**](../jaiclaw-examples/meeting-assistant/) — A Slack-connected bot that transcribes meetings via the voice module (`jaiclaw-voice`), links participants across channels (`jaiclaw-identity`), and posts summaries back to the team's Slack channel.

- [**Helpdesk Bot**](../jaiclaw-examples/helpdesk-bot/) — A support bot with custom `FaqTool` and `TicketTool` for searching knowledge bases and creating tickets. Demonstrates per-tenant session isolation and API key authentication.

- [**Telegram DocStore**](../jaiclaw-examples/telegram-docstore/) — A Telegram bot for document storage, retrieval, and analysis. Users send documents via Telegram chat and the agent ingests, indexes, and answers questions about them.

- [**OAuth Provider Demo**](../jaiclaw-examples/oauth-provider-demo/) — Authenticate with an AI provider via OAuth (PKCE auth code or device code) before the agent operates. Demonstrates the `jaiclaw-identity` module's OAuth flow with 5 provider configs.

**What changes from Level 1:** The assistant is now **shared** — multiple users interact with it, each with their own session. Sessions are isolated per user automatically via the session key pattern `{agentId}:{channel}:{accountId}:{peerId}`. Skills provide behavioral guardrails without code changes — a non-engineer can author a new skill as a Markdown file.

---

## Level 3: Multi-Channel Departmental Platform

**Who:** A department or business unit (50-500 users) that needs the assistant available across multiple channels with scheduled automation, document processing, browser automation, telephony, and access control.

**What you add:** Multiple channel adapters, cron scheduling, document ingestion, browser tools, canvas output, calendar management, telephony, platform-specific tools, and the audit trail.

**Configuration (additive):**
```yaml
jaiclaw:
  security:
    mode: api-key
  channels:
    telegram:
      enabled: true
    slack:
      enabled: true
    email:
      enabled: true
  cron:
    enabled: true
  calendar:
    enabled: true
```

**Modules added (+10-15, total ~18-25):**
- `jaiclaw-channel-slack`, `jaiclaw-channel-email`, `jaiclaw-channel-sms` — Additional channels
- `jaiclaw-cron` + `jaiclaw-cron-manager` — Scheduled autonomous agent tasks with H2 persistence and run history
- `jaiclaw-documents` + `jaiclaw-docstore` — PDF/HTML/text ingestion with full-text and vector search
- `jaiclaw-browser` — Playwright-based headless browser (8 tools: navigate, click, type, screenshot, evaluate, read, tabs, close)
- `jaiclaw-canvas` — Rich HTML artifact rendering pushed to clients (A2UI pattern)
- `jaiclaw-calendar` — Event scheduling with 8 MCP tools (create/list/update/delete events, available slots, calendars)
- `jaiclaw-voice-call` — Full telephony via Twilio: outbound/inbound calls, WebSocket media streaming, TwiML generation
- `jaiclaw-discord-tools` — Platform-specific Discord MCP tools: reactions, pins, threads, polls, message management
- `jaiclaw-slack-tools` — Platform-specific Slack MCP tools: reactions, pins, channel history, member info
- `jaiclaw-messaging` — MCP server exposing 8 cross-channel messaging tools (send, broadcast, sessions, agent-routed chat)
- `jaiclaw-audit` — Formal audit trail with tenant context, actor, action, resource, outcome, and timestamp

**JaiClaw examples at this level:**

- [**Daily Briefing**](../jaiclaw-examples/daily-briefing/) — A cron-scheduled agent that runs at 7 AM on weekdays, fetches weather and news via custom `WeatherTool` and `NewsTool`, and delivers a formatted morning digest to both Telegram and Email simultaneously.

- [**Price Monitor**](../jaiclaw-examples/price-monitor/) — An hourly cron job that uses Playwright (`jaiclaw-browser`) to scrape product pages on JavaScript-rendered sites, compares prices against thresholds, and sends SMS alerts via Twilio when prices drop.

- [**System Monitor**](../jaiclaw-examples/system-monitor/) — A full gateway deployment (`jaiclaw-starter-gateway` + `jaiclaw-starter-cron`) that runs daily Linux system health reports and delivers them to a Telegram user. Demonstrates combining starters for a production monitoring agent.

- [**Sales Report**](../jaiclaw-examples/sales-report/) — A weekly cron job (Monday 9 AM) that collects sales data via a custom `SalesFetchTool`, generates an HTML dashboard with `jaiclaw-canvas`, and delivers it to the team.

- [**Document Q&A**](../jaiclaw-examples/document-qa/) — PDF/HTML/text ingestion into a searchable document store with vector similarity search. Users upload documents, and the agent answers questions with citations. Context compaction handles long research conversations.

- [**Content Pipeline**](../jaiclaw-examples/content-pipeline/) — Multi-modal content analysis using `jaiclaw-media` for image/audio processing and `jaiclaw-documents` for PDF parsing. Demonstrates the plugin SPI with multiple custom tools (`AnalyzeImageTool`, `ExtractMetadataTool`).

- [**Voice Call Demo**](../jaiclaw-examples/voice-call-demo/) — Telephony integration with Twilio: outbound appointment reminder calls and inbound customer service via WebSocket media streaming.

**What changes from Level 2:**
- **Multi-channel:** The same user can talk to the agent on Telegram and Email — `IdentityLinkService` resolves them to the same canonical identity, sharing conversation history and memory.
- **Scheduled autonomy:** The agent acts on its own via cron jobs — daily briefings, price monitoring, report generation — without a human initiating the conversation. Cron jobs persist in H2, survive restarts, and track run history.
- **Document intelligence:** The agent ingests PDFs, transcripts, contracts, and reports into a searchable document store with vector similarity search.
- **Browser automation:** The agent navigates websites, fills forms, takes screenshots, and scrapes JavaScript-rendered pages via Playwright.
- **Telephony:** The agent makes and receives phone calls via Twilio with WebSocket media streaming — appointment reminders, customer service, voice interaction.
- **Calendar management:** The agent can create events, find available time slots, and manage calendars — all exposed as MCP tools the agent calls autonomously.
- **Platform-specific actions:** Beyond basic messaging, Discord and Slack tools provide reactions, pins, threads, polls, and message management as MCP tools.
- **Cross-channel messaging MCP:** External LLMs can invoke JaiClaw's channel messaging capabilities — send messages, broadcast, manage sessions — via the messaging MCP server.
- **Audit trail:** Every action is logged with actor, action, resource, outcome, and timestamp. Per-tenant, queryable.

---

## Level 4: Enterprise Multi-Tenant Platform

**Who:** An organization deploying the assistant across multiple teams, departments, or client organizations — each with data isolation, role-based access, compliance requirements, subscription billing, and security hardening.

**What you add:** JWT security with tenant resolution, role-based tool profiles, subscription billing, MCP server hosting, Kubernetes deployment, security hardening, and media analysis.

**Configuration (additive):**
```yaml
jaiclaw:
  security:
    mode: jwt
  subscription:
    enabled: true
    provider: stripe

# Enable security hardening (or use SPRING_PROFILES_ACTIVE=security-hardened)
jaiclaw.channels.slack.verify-signature: true
jaiclaw.channels.telegram.verify-webhook: true
jaiclaw.channels.telegram.mask-bot-token: true
jaiclaw.tools.web.ssrf-protection: true
jaiclaw.tools.code.workspace-boundary: true
jaiclaw.security.timing-safe-api-key: true
```

**Modules added (+8-15, total 30-65):**
- `jaiclaw-security` — JWT tenant resolution, role-to-tool-profile mapping, per-sender rate limiting, timing-safe API key comparison
- `jaiclaw-subscription` + `jaiclaw-subscription-telegram` — Stripe/PayPal/Telegram Stars billing with plan management and webhook verification
- `jaiclaw-tools-k8s` — 9 Fabric8 Kubernetes management tools
- `jaiclaw-tools-security` — ECDH P-256/X25519 key exchange, challenge-response, secure session establishment
- `jaiclaw-media` — Image/video/audio analysis SPI with async `CompositeMediaAnalyzer`
- MCP server hosting — Expose JaiClaw tools at `/mcp/*` for external LLMs (Claude Desktop, Cursor)
- `jaiclaw-code` — File edit, glob, grep tools with workspace boundary enforcement

**Security hardening** (all flags opt-in, default off):

| Protection | Flag | What It Does |
|-----------|------|-------------|
| Slack webhook HMAC | `verify-signature` | HMAC-SHA256 + replay protection |
| Telegram webhook secret | `verify-webhook` | Secret token verification |
| Bot token masking | `mask-bot-token` | SHA-256 hash in session keys |
| SSRF protection | `ssrf-protection` | Block private/internal IPs in WebFetchTool |
| Path traversal | `workspace-boundary` | Prevent code tools from escaping workspace |
| Timing-safe auth | `timing-safe-api-key` | `MessageDigest.isEqual()` for API key comparison |

Enable all at once: `SPRING_PROFILES_ACTIVE=security-hardened`

**JaiClaw examples at this level:**

- [**Helpdesk Bot**](../jaiclaw-examples/helpdesk-bot/) (enterprise mode) — Multi-tenant support platform with JWT authentication. Each client organization is a tenant with isolated sessions, FAQs, and ticket history. `TenantContext` flows through every layer.

- [**Incident Responder**](../jaiclaw-examples/incident-responder/) — A DevOps incident triage agent with explicit tool loops, human-in-the-loop approval for destructive operations, and hook observability for monitoring. Demonstrates enterprise-grade guardrails.

- [**Data Pipeline**](../jaiclaw-examples/data-pipeline/) — An ETL orchestrator with audit trail hooks and human approval gates for destructive operations. Every database mutation is logged and requires explicit approval.

- [**Security Handshake**](../jaiclaw-examples/security-handshake/) + [**Security Handshake Server**](../jaiclaw-examples/security-handshake-server/) — ECDH P-256/X25519 cryptographic key exchange orchestrated by a GOAP agent, with a standalone MCP server implementing the handshake protocol. Demonstrates agent-to-agent secure communication.

**What changes from Level 3:**
- **Multi-tenancy is architectural:** JWT tokens carry `tenantId`, `roles`, and `staffId`. Every session, memory query, skill lookup, and audit event is tenant-isolated at the framework level — not application-level filtering. A misconfigured query cannot leak data across tenants. `TenantContextHolder` propagates across async boundaries via `TenantContextPropagator`.
- **Role-based tool access:** `RoleToolProfileResolver` maps JWT roles to tool profiles. Admin -> `FULL` (all tools including shell exec). Standard User -> `MESSAGING` (communication tools only). Guest -> `MINIMAL` (read-only tools).
- **Security hardening:** HMAC webhook verification, SSRF protection, workspace boundary enforcement, timing-safe authentication — all opt-in flags that activate with a single profile.
- **Subscription billing:** Stripe, PayPal, and Telegram Stars integrations with plan management, activation, expiry, and webhook handling.
- **Kubernetes deployment:** JKube-integrated Docker images, Helm charts, horizontal scaling across gateway and app tiers, Redis for distributed session state.
- **MCP server hosting:** Your JaiClaw agent's tools are exposed as MCP endpoints — external LLMs (Claude Desktop, Cursor, other JaiClaw instances) can invoke them.
- **OAuth credential rotation:** `jaiclaw-identity` provides session rotation with round-robin and cooldown, user-pin sticky sessions, and external CLI credential sync across Claude, Codex, Qwen, and MiniMax CLIs.

---

## Level 5: Intelligent Multi-Agent Workflows with Embabel

At every level above, the agent operates in a **ReAct loop** — the LLM observes the current state, reasons about what to do next, and acts. This is powerful but non-deterministic: the same prompt may produce different tool call sequences on different runs.

When you add `jaiclaw-starter-embabel`, JaiClaw gains a fundamentally different capability: **GOAP planning** (Goal-Oriented Action Planning) via Rod Johnson's Embabel framework. This isn't a separate level in the scaling spectrum — it's an **orthogonal upgrade** that enhances any level from Level 1 through Level 4.

### What GOAP Changes

```
                 ReAct (Levels 1-4)                    GOAP (with Embabel)
                 ──────────────────                    ──────────────────
Planning:        LLM decides next step at each         A* algorithm computes optimal
                 iteration — non-deterministic          action sequence upfront

Correctness:     Hope the LLM reasons correctly        Preconditions and effects are
                                                        Java types — compiler-enforced

Parallelism:     Developer manually coordinates         Planner automatically detects
                 multiple tool calls                    independent actions and runs
                                                        them in parallel

Observability:   Inspect message history and logs       Typed blackboard with explicit
                                                        intermediate domain objects

Replay:          Not possible — different run,          Same preconditions → same plan
                 potentially different sequence         → same execution, every time

Auditability:    Log which tools were called            Audit exactly which actions
                                                        executed in which order with
                                                        typed inputs and outputs
```

### How It Works

Embabel uses Java type signatures as the planning language. Each `@Action` method declares its **preconditions** (input parameter types) and **effects** (return type). The GOAP planner uses A* search to find the shortest path from the initial state to the goal.

**One dependency activates it:**
```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-embabel</artifactId>
    <type>pom</type>
</dependency>
```

### Pattern 1: Serial Action Chaining — Code Review Bot

The [**Code Review Bot**](../jaiclaw-examples/code-review-bot/) demonstrates sequential GOAP planning. Two actions chain automatically based on type dependencies:

```java
@Agent(description = "Reviews code diffs and generates structured feedback")
public class CodeReviewAgent {

    @Action(description = "Analyze a code diff for bugs, style issues, and improvements")
    public DiffAnalysis analyzeDiff(String diff, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Analyze this code diff for bugs, security issues, "
                                + "and potential improvements.\n\nDiff:\n" + diff,
                        DiffAnalysis.class
                );
    }

    @Action(description = "Generate a formatted code review from the analysis")
    @AchievesGoal(description = "Code review is complete with actionable feedback")
    public ReviewComplete generateReview(DiffAnalysis analysis, OperationContext context) {
        String review = context.ai()
                .withDefaultLlm()
                .generateText(
                        "Generate a professional code review:\n"
                                + "Summary: " + analysis.summary() + "\n"
                                + "Issues: " + String.join(", ", analysis.issues()) + "\n"
                                + "Severity: " + analysis.severity()
                );
        boolean approved = "low".equalsIgnoreCase(analysis.severity());
        return new ReviewComplete(review, approved, analysis.issues().size());
    }
}
```

The GOAP planner sees:
- `analyzeDiff`: needs `String` -> produces `DiffAnalysis`
- `generateReview`: needs `DiffAnalysis` -> produces `ReviewComplete` (goal)

Plan: `analyzeDiff -> generateReview`. Computed once, executed deterministically.

```
String(diff) → [analyzeDiff] → DiffAnalysis → [generateReview] → ReviewComplete ✓
```

The intermediate `DiffAnalysis` is a typed domain record on the blackboard — not buried in conversation history. It's inspectable, loggable, and auditable:

```java
public record DiffAnalysis(
        String summary,
        List<String> issues,
        List<String> suggestions,
        String severity
) {}
```

### Pattern 2: Parallel Fan-Out/Fan-In — Travel Planner

The [**Travel Planner**](../jaiclaw-examples/travel-planner/) demonstrates automatic parallelism detection. The GOAP planner identifies that two actions share the same input type and can run concurrently:

```java
@Agent(description = "Plans trips by researching flights and hotels")
public class TravelPlannerAgent {

    @Action(description = "Search for available flights")
    public FlightOptions searchFlights(TravelRequest request, OperationContext context) {
        return context.ai().withDefaultLlm()
                .createObject("Search for flights to " + request.destination() + "...",
                        FlightOptions.class);
    }

    @Action(description = "Search for available hotels")
    public HotelOptions searchHotels(TravelRequest request, OperationContext context) {
        return context.ai().withDefaultLlm()
                .createObject("Search for hotels in " + request.destination() + "...",
                        HotelOptions.class);
    }

    @Action(description = "Assemble the final trip plan")
    @AchievesGoal(description = "Complete trip plan with flights, hotels, and itinerary")
    public TripPlan assemblePlan(TravelRequest request, FlightOptions flights,
                                 HotelOptions hotels, OperationContext context) {
        return context.ai().withDefaultLlm()
                .createObject("Assemble a trip plan...", TripPlan.class);
    }
}
```

The planner computes:
- `searchFlights` and `searchHotels` both take `TravelRequest` — no dependency between them -> **parallel execution**
- `assemblePlan` needs both `FlightOptions` AND `HotelOptions` -> waits for both -> **fan-in**

```
TravelRequest
    ├──→ [searchFlights] → FlightOptions  ──┐
    │                                        ├──→ [assemblePlan] → TripPlan ✓
    └──→ [searchHotels]  → HotelOptions  ──┘
         (parallel)                          (fan-in)
```

No manual sub-agent coordination. No developer-managed concurrency. The planner handles it.

### Pattern 3: Document Compliance with Audit Trail

The [**Compliance Checker**](../jaiclaw-examples/compliance-checker/) combines GOAP planning with `jaiclaw-documents` for PDF ingestion and `jaiclaw-audit` for formal compliance logging:

```java
@Agent(description = "Checks documents against compliance policies")
public class ComplianceAgent {

    @Action(description = "Extract compliance rules from a policy document")
    public PolicyDocument extractPolicy(String policyText, OperationContext context) {
        return context.ai().withDefaultLlm()
                .createObject("Extract compliance rules from:\n" + policyText,
                        PolicyDocument.class);
    }

    @Action(description = "Check document against extracted rules")
    @AchievesGoal(description = "Compliance verified with detailed report")
    public ComplianceReport checkCompliance(PolicyDocument policy, OperationContext context) {
        return context.ai().withDefaultLlm()
                .createObject("Check compliance against:\n"
                        + "Policy: " + policy.policyName() + "\n"
                        + "Rules: " + String.join("; ", policy.rules()),
                        ComplianceReport.class);
    }
}
```

```
String(policy) → [extractPolicy] → PolicyDocument → [checkCompliance] → ComplianceReport ✓
                                                                         + AuditEvent logged
```

Every step is audited. The `ComplianceReport` includes a numeric score, findings list, and pass/fail status — structured data, not free-text buried in a conversation.

### Pattern 4: Secure Multi-Agent Communication

The [**Security Handshake**](../jaiclaw-examples/security-handshake/) example uses Embabel GOAP to orchestrate an ECDH cryptographic key exchange between two JaiClaw agents. The planner sequences the handshake protocol steps, and `jaiclaw-tools-security` provides the P-256/X25519 key exchange and HMAC-SHA256 challenge-response. This is agent-to-agent secure communication — not human-to-agent.

### Why GOAP Matters for Scale

| Scale Challenge | ReAct Approach | GOAP Approach |
|----------------|---------------|---------------|
| Complex multi-step workflows | LLM may forget steps or choose wrong order | Plan is computed upfront — correct by construction |
| Compliance and audit | Log tool calls, hope sequence was right | Every action's preconditions and effects are typed and auditable |
| Performance at scale | Each step requires LLM round-trip to decide next action | Plan computed once — execute without re-evaluation |
| Debugging production issues | Read logs, reconstruct what happened | Replay: same inputs -> same plan -> same execution |
| Multi-step data pipelines | LLM might skip steps or repeat them | Type system prevents: Action2 cannot run without Action1's output |
| Parallel workloads | Manually code sub-agent coordination | Planner detects parallelism automatically from type dependencies |

### Embabel Is Additive — Not Replacement

GOAP does not replace the ReAct loop. It **augments** it. JaiClaw applications can use both:

- **ReAct** for open-ended conversations, research, and exploratory tasks where the path isn't known upfront
- **GOAP** for structured workflows where correctness, determinism, and auditability matter

The `AgentOrchestrationPort` SPI makes this seamless. When `jaiclaw-starter-embabel` is on the classpath, the `AgentRuntime` detects it and GOAP workflows become available. When it's absent, `NoOpOrchestrationPort` provides graceful degradation — the agent operates in standard ReAct mode.

```java
// JaiClaw's SPI — clean separation
public interface AgentOrchestrationPort {
    CompletableFuture<OrchestrationResult> execute(String workflowName, Map<String, Object> input);
    List<WorkflowDescriptor> listWorkflows();
    boolean isAvailable();
    String platformName();
}
```

This means any orchestration platform — not just Embabel — can plug into JaiClaw by implementing this interface.

---

## The Growth Path

The critical architectural decision in JaiClaw is that **each level is additive, not replacement.** Here's what that means in practice:

| Transition | What you do | What you DON'T do |
|-----------|-------------|-------------------|
| Personal -> Team | Add a channel adapter dependency + YAML config | Rewrite your agent logic |
| Team -> Department | Add more channels + cron + documents + audit | Change your tool implementations |
| Department -> Enterprise | Add JWT security + multi-tenancy + billing + hardening | Rebuild your session management |
| Any level -> GOAP | Add `jaiclaw-starter-embabel` + write `@Agent` classes | Touch existing ReAct workflows |

### Maven dependency is the scaling mechanism

```xml
<!-- Level 1: Personal -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-personal-assistant</artifactId>
</dependency>

<!-- Level 2: Team (add a channel) -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-channel-telegram</artifactId>
</dependency>

<!-- Level 3: Department (add automation + docs + telephony) -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-cron</artifactId>
</dependency>
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-documents</artifactId>
</dependency>
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-audit</artifactId>
</dependency>
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-voice-call</artifactId>
</dependency>
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-messaging</artifactId>
</dependency>

<!-- Level 4: Enterprise (add security + billing + hardening) -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-subscription</artifactId>
</dependency>

<!-- Any level: Add GOAP multi-agent workflows -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-embabel</artifactId>
</dependency>
```

No replatforming. No migration. No "enterprise edition." The same JAR, composed differently.

---

## 20 Examples Across the Spectrum

JaiClaw ships 20 working examples demonstrating every level:

| Example | Level | Key Modules | What It Demonstrates |
|---------|-------|-------------|---------------------|
| **Research Assistant** | 1 | tools, compaction, memory | Multi-iteration research with persistent findings |
| **Code Scaffolder** | 1 | tools, plugin-sdk | Spring AI tool loop with streaming and prompt hooks |
| **Meeting Assistant** | 2 | voice, identity, channel-slack | STT transcription, cross-channel identity, Slack delivery |
| **Helpdesk Bot** | 2-4 | security, gateway | Multi-tenant FAQ + tickets with JWT or API key auth |
| **Telegram DocStore** | 2 | docstore, channel-telegram | Document upload, indexing, and Q&A via Telegram |
| **OAuth Provider Demo** | 2 | identity | OAuth login (PKCE + device code) with 5 provider configs |
| **Daily Briefing** | 3 | cron, channel-telegram, channel-email | 7 AM weekday digest with weather + news |
| **Price Monitor** | 3 | cron, browser, channel-sms | Hourly Playwright scraping with SMS price alerts |
| **System Monitor** | 3 | starter-gateway, starter-cron | Daily Linux health reports to Telegram |
| **Sales Report** | 3 | cron, canvas | Weekly HTML dashboard generation |
| **Document Q&A** | 3 | documents, memory, compaction | PDF ingestion with semantic search and citations |
| **Content Pipeline** | 3 | media, documents, plugin-sdk | Multi-modal image/audio/PDF analysis |
| **Voice Call Demo** | 3 | voice-call, channel-sms | Twilio telephony: outbound reminders + inbound service |
| **Incident Responder** | 4 | plugin-sdk, gateway | DevOps triage with human-in-the-loop approval |
| **Data Pipeline** | 4 | plugin-sdk, gateway | ETL orchestration with audit hooks and approval gates |
| **Code Review Bot** | GOAP | starter-embabel, canvas | Serial action chaining: diff -> analysis -> review |
| **Travel Planner** | GOAP | starter-embabel, browser, voice | Parallel fan-out (flights || hotels) -> fan-in (plan) |
| **Compliance Checker** | GOAP | starter-embabel, documents, audit | Policy extraction -> compliance check with audit trail |
| **Security Handshake** | GOAP | starter-embabel, tools-security | ECDH key exchange orchestrated by GOAP planner |
| **Security Handshake Server** | GOAP | starter-embabel, tools-security | Standalone MCP server for handshake protocol |

---

## 18 Starters for Every Use Case

### AI Model Provider Starters (11)

| Starter | Provider | Credentials |
|---------|----------|-------------|
| `jaiclaw-starter-anthropic` | Anthropic (Claude) | `ANTHROPIC_API_KEY` |
| `jaiclaw-starter-openai` | OpenAI (GPT) | `OPENAI_API_KEY` |
| `jaiclaw-starter-gemini` | Google Gemini | `GEMINI_API_KEY` |
| `jaiclaw-starter-ollama` | Ollama (local models) | *(none)* |
| `jaiclaw-starter-bedrock` | AWS Bedrock | AWS credentials |
| `jaiclaw-starter-azure-openai` | Azure OpenAI | `AZURE_OPENAI_API_KEY` |
| `jaiclaw-starter-deepseek` | DeepSeek (R1, V3) | `DEEPSEEK_API_KEY` |
| `jaiclaw-starter-mistral` | Mistral AI | `MISTRAL_API_KEY` |
| `jaiclaw-starter-minimax` | MiniMax | `MINIMAX_API_KEY` |
| `jaiclaw-starter-vertex-ai` | Google Vertex AI | GCP ADC |
| `jaiclaw-starter-oci-genai` | Oracle Cloud GenAI | OCI config |

### Feature & Deployment Starters (7)

| Starter | What It Bundles |
|---------|-----------------|
| `jaiclaw-starter-shell` | Spring Shell CLI + Embabel shell |
| `jaiclaw-starter-personal-assistant` | Anthropic + Shell + 59 bundled skills |
| `jaiclaw-starter-gateway` | REST + WebSocket + all 7 channel adapters |
| `jaiclaw-starter-cron` | Cron manager + Spring Batch + H2 persistence |
| `jaiclaw-starter-calendar` | Calendar events + scheduling + MCP tools |
| `jaiclaw-starter-embabel` | JaiClaw core + Embabel GOAP agent platform |
| `jaiclaw-starter-k8s-monitor` | Anthropic + Shell + Telegram + 9 K8s tools + triage skill |

---

## Why This Matters

### For startups
Start with Level 1 or 2. Ship an MVP in days. When you land enterprise customers, add multi-tenancy and billing — without a rewrite. Add GOAP when your workflows need deterministic guarantees.

### For mid-size companies
Start with Level 3. Deploy a departmental AI assistant that handles real workflows — daily briefings, document analysis, price monitoring, phone calls. When other departments want in, add multi-tenancy. When compliance requires auditability, add Embabel.

### For enterprises
Start at Level 4 with GOAP. The security hardening, audit, multi-tenancy, compliance infrastructure, and deterministic planning are production-grade from day one. 65 modules mean you take exactly what you need.

### For all organizations
**You stay on Java.** Your existing CI/CD pipelines, security reviews, monitoring dashboards, deployment infrastructure, and engineering team skills all apply. No Python. No parallel stack. No cross-language integration tax.

---

## The JaiClaw Equation

```
Personal Assistant  =  Spring AI  +  JaiClaw Core  +  Shell
Team Bot            =  above      +  1 Channel     +  Skills  +  Identity
Department Platform =  above      +  Channels      +  Cron + Docs + Audit + Calendar + Telephony + Messaging MCP
Enterprise Platform =  above      +  JWT Security   +  Multi-Tenancy + Billing + Hardening + K8s
Multi-Agent GOAP    =  any above  +  Embabel        (deterministic, parallel, auditable)
```

One framework. Add modules. Ship.

---

*JaiClaw — Java 21 / Spring Boot 3.5 / Spring AI 1.1.4*
*65 modules. 20 examples. 18 starters. 11 AI providers. 7 channels. 59 skills.*
*From `./mvnw spring-boot:run` to Kubernetes. Same codebase.*
