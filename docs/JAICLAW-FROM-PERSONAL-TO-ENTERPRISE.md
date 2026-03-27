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
│  0 channels       1 channel        3-6 channels        All channels          │
│  No auth          API key          JWT roles            JWT multi-tenancy     │
│  In-memory        In-memory        Redis sessions       Redis + PostgreSQL   │
│  No audit         Minimal          Full audit trail     SOC 2 / HIPAA        │
│  ReAct loop       ReAct loop       ReAct + cron         GOAP multi-agent     │
│                                                                              │
│  Modules: 4-5     Modules: 8-10    Modules: 15-20      Modules: 25-39       │
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

This single POM gives you: Anthropic (Claude) as the LLM provider, Spring Shell CLI, and 5 bundled skills (coding, web-research, system-admin, conversation, summarize).

**Run it:**
```bash
./mvnw spring-boot:run
> /chat What's in my project?
```

**Modules used (~5):**
- `jaiclaw-tools` — File I/O, shell exec, web search, web fetch
- `jaiclaw-compaction` — Automatic context window management with identifier preservation
- `jaiclaw-skills` — 5 bundled behavioral skills
- `jaiclaw-shell` — Interactive Spring Shell CLI (via `jaiclaw-starter-shell`)
- `jaiclaw-spring-boot-starter` — Auto-configuration

**JaiClaw example:** [**Research Assistant**](../jaiclaw-examples/research-assistant/) — A multi-iteration research agent with tool loops, context compaction for managing large research contexts, and workspace memory for persisting findings across sessions.

**What makes this more than a chatbot wrapper:** Session persistence, context compaction with identifier preservation (file paths and UUIDs survive summarization), workspace memory (`MEMORY.md`), and daily log files. Your assistant remembers yesterday's conversation.

---

## Level 2: Team Bot

**Who:** A small team (5-50 people) that wants a shared AI assistant on a single messaging channel.

**What you add:** One channel adapter, the gateway, and optionally voice transcription.

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

**Modules added (+4, total ~8-10):**
- `jaiclaw-channel-telegram` — Telegram Bot API adapter (or Slack, Discord, SMS, Email, Signal)
- `jaiclaw-gateway` — REST API + webhook routing
- `jaiclaw-voice` — Voice memo transcription (Whisper STT)
- `jaiclaw-identity` — Cross-channel identity linking

**JaiClaw examples at this level:**

- [**Meeting Assistant**](../jaiclaw-examples/meeting-assistant/) — A Slack-connected bot that transcribes meetings via the voice module (`jaiclaw-voice`), links participants across channels (`jaiclaw-identity`), and posts summaries back to the team's Slack channel.

- [**Helpdesk Bot**](../jaiclaw-examples/helpdesk-bot/) — A support bot with custom `FaqTool` and `TicketTool` for searching knowledge bases and creating tickets. Demonstrates per-tenant session isolation and API key authentication.

- [**Telegram DocStore**](../jaiclaw-examples/telegram-docstore/) — A Telegram bot for document storage, retrieval, and analysis. Users send documents via Telegram chat and the agent ingests, indexes, and answers questions about them.

**What changes from Level 1:** The assistant is now **shared** — multiple users interact with it, each with their own session. Sessions are isolated per user automatically via the session key pattern `{agentId}:{channel}:{accountId}:{peerId}`. Skills provide behavioral guardrails without code changes — a non-engineer can author a new skill as a Markdown file.

---

## Level 3: Multi-Channel Departmental Platform

**Who:** A department or business unit (50-500 users) that needs the assistant available across multiple channels with scheduled automation, document processing, browser automation, and access control.

**What you add:** Multiple channel adapters, cron scheduling, document ingestion, browser tools, canvas output, calendar management, and the audit trail.

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

**Modules added (+8-12, total ~15-20):**
- `jaiclaw-channel-slack`, `jaiclaw-channel-email`, `jaiclaw-channel-sms` — Additional channels
- `jaiclaw-cron` + `jaiclaw-cron-manager` — Scheduled autonomous agent tasks with H2 persistence and run history
- `jaiclaw-documents` + `jaiclaw-docstore` — PDF/HTML/text ingestion with full-text and vector search
- `jaiclaw-browser` — Playwright-based headless browser (8 tools: navigate, click, type, screenshot, evaluate, read, tabs, close)
- `jaiclaw-canvas` — Rich HTML output pushed to clients
- `jaiclaw-calendar` — Event scheduling with 8 MCP tools (create/list/update/delete events, available slots, calendars)
- `jaiclaw-audit` — Formal audit trail with tenant context and outcomes

**JaiClaw examples at this level:**

- [**Daily Briefing**](../jaiclaw-examples/daily-briefing/) — A cron-scheduled agent that runs at 7 AM on weekdays, fetches weather and news via custom `WeatherTool` and `NewsTool`, and delivers a formatted morning digest to both Telegram and Email simultaneously.

- [**Price Monitor**](../jaiclaw-examples/price-monitor/) — An hourly cron job that uses Playwright (`jaiclaw-browser`) to scrape product pages on JavaScript-rendered sites, compares prices against thresholds, and sends SMS alerts via Twilio when prices drop.

- [**System Monitor**](../jaiclaw-examples/system-monitor/) — A full gateway deployment (`jaiclaw-starter-gateway` + `jaiclaw-starter-cron`) that runs daily Linux system health reports and delivers them to a Telegram user. Demonstrates combining starters for a production monitoring agent.

- [**Sales Report**](../jaiclaw-examples/sales-report/) — A weekly cron job (Monday 9 AM) that collects sales data via a custom `SalesFetchTool`, generates an HTML dashboard with `jaiclaw-canvas`, and delivers it to the team.

- [**Document Q&A**](../jaiclaw-examples/document-qa/) — PDF/HTML/text ingestion into a searchable document store with vector similarity search. Users upload documents, and the agent answers questions with citations. Context compaction handles long research conversations.

- [**Content Pipeline**](../jaiclaw-examples/content-pipeline/) — Multi-modal content analysis using `jaiclaw-media` for image/audio processing and `jaiclaw-documents` for PDF parsing. Demonstrates the plugin SPI with multiple custom tools (`AnalyzeImageTool`, `ExtractMetadataTool`).

**What changes from Level 2:**
- **Multi-channel:** The same user can talk to the agent on Telegram and Email — `IdentityLinkService` resolves them to the same canonical identity, sharing conversation history and memory.
- **Scheduled autonomy:** The agent acts on its own via cron jobs — daily briefings, price monitoring, report generation — without a human initiating the conversation. Cron jobs persist in H2, survive restarts, and track run history.
- **Document intelligence:** The agent ingests PDFs, transcripts, contracts, and reports into a searchable document store with vector similarity search.
- **Browser automation:** The agent navigates websites, fills forms, takes screenshots, and scrapes JavaScript-rendered pages via Playwright.
- **Calendar management:** The agent can create events, find available time slots, and manage calendars — all exposed as MCP tools the agent calls autonomously.
- **Audit trail:** Every action is logged with actor, action, resource, outcome, and timestamp. Per-tenant, queryable.

---

## Level 4: Enterprise Multi-Tenant Platform

**Who:** An organization deploying the assistant across multiple teams, departments, or client organizations — each with data isolation, role-based access, compliance requirements, and subscription billing.

**What you add:** JWT security with tenant resolution, role-based tool profiles, subscription billing, MCP server hosting, Kubernetes deployment, and media analysis.

**Configuration (additive):**
```yaml
jaiclaw:
  security:
    mode: jwt
  subscription:
    enabled: true
    provider: stripe
```

**Modules added (+8-15, total 25-39):**
- `jaiclaw-security` — JWT tenant resolution, role → tool profile mapping, per-sender rate limiting
- `jaiclaw-subscription` — Stripe/PayPal/Telegram Stars billing with plan management
- `jaiclaw-tools-k8s` — 9 Fabric8 Kubernetes management tools
- `jaiclaw-media` — Image/video/audio analysis SPI
- MCP server hosting — Expose JaiClaw tools at `/mcp/*` for external LLMs (Claude Desktop, Cursor)

**JaiClaw examples at this level:**

- [**Helpdesk Bot**](../jaiclaw-examples/helpdesk-bot/) (enterprise mode) — Multi-tenant support platform with JWT authentication. Each client organization is a tenant with isolated sessions, FAQs, and ticket history. `TenantContext` flows through every layer.

- [**Incident Responder**](../jaiclaw-examples/incident-responder/) — A DevOps incident triage agent with explicit tool loops, human-in-the-loop approval for destructive operations, and hook observability for monitoring. Demonstrates enterprise-grade guardrails.

- [**Data Pipeline**](../jaiclaw-examples/data-pipeline/) — An ETL orchestrator with audit trail hooks and human approval gates for destructive operations. Every database mutation is logged and requires explicit approval.

- [**Security Handshake**](../jaiclaw-examples/security-handshake/) + [**Security Handshake Server**](../jaiclaw-examples/security-handshake-server/) — ECDH P-256/X25519 cryptographic key exchange orchestrated by a GOAP agent, with a standalone MCP server implementing the handshake protocol. Demonstrates agent-to-agent secure communication.

**What changes from Level 3:**
- **Multi-tenancy is architectural:** JWT tokens carry `tenantId`, `roles`, and `staffId`. Every session, memory query, skill lookup, and audit event is tenant-isolated at the framework level — not application-level filtering. A misconfigured query cannot leak data across tenants.
- **Role-based tool access:** `RoleToolProfileResolver` maps JWT roles to tool profiles. Admin → `FULL` (all tools including shell exec). Standard User → `MESSAGING` (communication tools only). Guest → `MINIMAL` (read-only tools).
- **Subscription billing:** Stripe, PayPal, and Telegram Stars integrations with plan management, activation, expiry, and webhook handling.
- **Kubernetes deployment:** JKube-integrated Docker images, Helm charts, horizontal scaling across gateway and app tiers, Redis for distributed session state.
- **MCP server hosting:** Your JaiClaw agent's tools are exposed as MCP endpoints — external LLMs (Claude Desktop, Cursor, other JaiClaw instances) can invoke them.

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
- `analyzeDiff`: needs `String` → produces `DiffAnalysis`
- `generateReview`: needs `DiffAnalysis` → produces `ReviewComplete` (goal)

Plan: `analyzeDiff → generateReview`. Computed once, executed deterministically.

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
- `searchFlights` and `searchHotels` both take `TravelRequest` — no dependency between them → **parallel execution**
- `assemblePlan` needs both `FlightOptions` AND `HotelOptions` → waits for both → **fan-in**

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
| Debugging production issues | Read logs, reconstruct what happened | Replay: same inputs → same plan → same execution |
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
| Personal → Team | Add a channel adapter dependency + YAML config | Rewrite your agent logic |
| Team → Department | Add more channels + cron + documents + audit | Change your tool implementations |
| Department → Enterprise | Add JWT security + multi-tenancy + billing | Rebuild your session management |
| Any level → GOAP | Add `jaiclaw-starter-embabel` + write `@Agent` classes | Touch existing ReAct workflows |

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

<!-- Level 3: Department (add automation + docs) -->
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

<!-- Level 4: Enterprise (add security + billing) -->
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

## 17 Examples Across the Spectrum

JaiClaw ships 17 working examples demonstrating every level:

| Example | Level | Key Modules | What It Demonstrates |
|---------|-------|-------------|---------------------|
| **Research Assistant** | 1 | tools, compaction, memory | Multi-iteration research with persistent findings |
| **Code Scaffolder** | 1 | tools, plugin-sdk | Spring AI tool loop with streaming and prompt hooks |
| **Meeting Assistant** | 2 | voice, identity, channel-slack | STT transcription, cross-channel identity, Slack delivery |
| **Helpdesk Bot** | 2-4 | security, gateway | Multi-tenant FAQ + tickets with JWT or API key auth |
| **Telegram DocStore** | 2 | docstore, channel-telegram | Document upload, indexing, and Q&A via Telegram |
| **Daily Briefing** | 3 | cron, channel-telegram, channel-email | 7 AM weekday digest with weather + news |
| **Price Monitor** | 3 | cron, browser, channel-sms | Hourly Playwright scraping with SMS price alerts |
| **System Monitor** | 3 | starter-gateway, starter-cron | Daily Linux health reports to Telegram |
| **Sales Report** | 3 | cron, canvas | Weekly HTML dashboard generation |
| **Document Q&A** | 3 | documents, memory, compaction | PDF ingestion with semantic search and citations |
| **Content Pipeline** | 3 | media, documents, plugin-sdk | Multi-modal image/audio/PDF analysis |
| **Incident Responder** | 4 | plugin-sdk, gateway | DevOps triage with human-in-the-loop approval |
| **Data Pipeline** | 4 | plugin-sdk, gateway | ETL orchestration with audit hooks and approval gates |
| **Code Review Bot** | GOAP | starter-embabel, canvas | Serial action chaining: diff → analysis → review |
| **Travel Planner** | GOAP | starter-embabel, browser, voice | Parallel fan-out (flights ∥ hotels) → fan-in (plan) |
| **Compliance Checker** | GOAP | starter-embabel, documents, audit | Policy extraction → compliance check with audit trail |
| **Security Handshake** | GOAP | starter-embabel, tools-security | ECDH key exchange orchestrated by GOAP planner |

---

## 11 Starters for Every Use Case

| Starter | What It Bundles |
|---------|-----------------|
| `jaiclaw-starter-anthropic` | JaiClaw core + Claude (Anthropic) |
| `jaiclaw-starter-openai` | JaiClaw core + GPT (OpenAI) |
| `jaiclaw-starter-gemini` | JaiClaw core + Gemini (Google) |
| `jaiclaw-starter-ollama` | JaiClaw core + Ollama (local models) |
| `jaiclaw-starter-shell` | Spring Shell CLI + Embabel shell |
| `jaiclaw-starter-personal-assistant` | Anthropic + Shell + 5 bundled skills |
| `jaiclaw-starter-gateway` | REST + WebSocket + all 6 channel adapters |
| `jaiclaw-starter-cron` | Cron manager + Spring Batch + H2 persistence |
| `jaiclaw-starter-calendar` | Calendar events + scheduling + MCP tools |
| `jaiclaw-starter-embabel` | JaiClaw core + Embabel GOAP agent platform |
| `jaiclaw-starter-k8s-monitor` | Anthropic + Shell + Telegram + 9 K8s tools + triage skill |

---

## Why This Matters

### For startups
Start with Level 1 or 2. Ship an MVP in days. When you land enterprise customers, add multi-tenancy and billing — without a rewrite. Add GOAP when your workflows need deterministic guarantees.

### For mid-size companies
Start with Level 3. Deploy a departmental AI assistant that handles real workflows — daily briefings, document analysis, price monitoring. When other departments want in, add multi-tenancy. When compliance requires auditability, add Embabel.

### For enterprises
Start at Level 4 with GOAP. The security, audit, multi-tenancy, compliance infrastructure, and deterministic planning are production-grade from day one. 39 modules mean you take exactly what you need.

### For all organizations
**You stay on Java.** Your existing CI/CD pipelines, security reviews, monitoring dashboards, deployment infrastructure, and engineering team skills all apply. No Python. No parallel stack. No cross-language integration tax.

---

## The JaiClaw Equation

```
Personal Assistant  =  Spring AI  +  JaiClaw Core  +  Shell
Team Bot            =  above      +  1 Channel     +  Skills
Department Platform =  above      +  Channels      +  Cron + Docs + Audit + Calendar
Enterprise Platform =  above      +  JWT Security   +  Multi-Tenancy + Billing + K8s
Multi-Agent GOAP    =  any above  +  Embabel        (deterministic, parallel, auditable)
```

One framework. Add modules. Ship.

---

*JaiClaw — Java 21 / Spring Boot 3.5 / Spring AI 1.1*
*39 modules. 17 examples. 11 starters.*
*From `./mvnw spring-boot:run` to Kubernetes. Same codebase.*
