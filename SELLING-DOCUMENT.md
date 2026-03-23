# JClaw: The Enterprise Java AI Agent Platform

**Build production AI agents in the language your team already knows.**

---

## The Problem

Your organization runs Java in production. Your engineers know Spring Boot. Your infrastructure is JVM-tuned. Your security team has approved your Java dependency supply chain.

Now leadership wants AI agents — and every framework on the market is Python.

Adopting LangChain, CrewAI, or AutoGen means:

- Hiring Python engineers or retraining your Java team
- Standing up a parallel deployment pipeline (pip, virtualenvs, Docker images, monitoring)
- Bridging your Java services to Python microservices via REST or gRPC
- Losing compile-time type safety, trading it for runtime `TypeError` surprises
- Re-implementing security, multi-tenancy, audit logging, and rate limiting in a language your security team hasn't vetted
- Accepting that only 2% of organizations have deployed agentic AI at scale — and most failures stem from production infrastructure gaps, not AI model quality

**JClaw eliminates this entire problem.** It's a Java 21 / Spring Boot AI agent platform that drops into your existing stack as a Maven dependency.

---

## What JClaw Is

JClaw is an embeddable, multi-channel AI agent runtime built on Spring Boot 3.5 and Spring AI. It provides everything between "I have an LLM API key" and "I have a production-grade AI assistant serving users across Telegram, Slack, Discord, Email, SMS, and Signal — with multi-tenancy, JWT security, formal audit trails, and a plugin system."

```
Your existing Spring Boot app
  └── pom.xml
        └── <dependency>jclaw-spring-boot-starter</dependency>
              ↓
        Auto-configured: AgentRuntime, ToolRegistry, SessionManager,
        ChannelAdapters, PluginSystem, Skills, Memory, Audit, MCP...
```

One dependency. Zero configuration required to start. Full production capability when you need it.

---

## Five Minutes to Your First Agent

### 1. Add the starter

```xml
<dependency>
    <groupId>io.jclaw</groupId>
    <artifactId>jclaw-starter-anthropic</artifactId>
    <type>pom</type>
</dependency>
```

### 2. Set your API key

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
```

### 3. Run it

```bash
./mvnw spring-boot:run
```

You now have a working AI assistant with built-in tools (file read/write, shell exec, web search, web fetch), session management, context window compaction, and memory — running on your existing JVM infrastructure.

### 4. Add channels

```yaml
jclaw:
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
```

Your agent is now reachable on Telegram and Slack simultaneously, with per-user session isolation across channels.

---

## Why Java Teams Choose JClaw

### 1. Zero-Friction Adoption for Java Shops

JClaw speaks Spring Boot natively. `@ConfigurationProperties` for typed config. `@ConditionalOnBean` for modular activation. `@ConditionalOnClass` for optional feature loading. Your Java engineers are immediately productive — no new language, no new build system, no new deployment pipeline.

**Compare:** LangChain requires Python expertise, pip dependency resolution, ASGI servers, and a separate Docker image pipeline. Even LangChain4j and Spring AI (bare) give you model access but leave you to build everything else: session management, channel integration, tools, plugins, security, and audit.

### 2. Compile-Time Safety That Prevents Production Incidents

JClaw's domain model uses Java 21 sealed interfaces and records:

```java
public sealed interface DeliveryResult {
    record Success(String platformMessageId, Map<String, Object> platformData) implements DeliveryResult {}
    record Failure(String errorCode, String message, boolean retryable) implements DeliveryResult {}
}
```

The compiler enforces that every code path handles both success and failure. Pattern matching via `switch` ensures exhaustive case coverage. Missing a case? Compilation error — not a 3 AM production incident.

Python frameworks rely on runtime type hints that are never enforced. A `TypeError` in LangChain surfaces in production when a user triggers an untested code path.

### 3. Enterprise Security Built In — Not Bolted On

JClaw ships three security layers:

| Layer | What It Does | Python Equivalent |
|-------|-------------|-------------------|
| **JWT tenant resolution** | Extracts `tenantId`, roles, and staffId from JWT tokens. Maps roles to tool profiles (MINIMAL, CODING, FULL). | Does not exist in any Python AI framework |
| **Per-sender rate limiting** | HTTP 429 with `Retry-After` headers, identified by JWT subject or IP. Background cleanup via virtual threads. | Manual implementation required |
| **ECDH cryptographic handshake** | Tool-driven P-256/X25519 key exchange for agent-to-MCP-server authentication. HMAC-SHA256 challenge-response. JDK-native crypto. | Does not exist in any AI framework |

Your security team doesn't need to vet a new language ecosystem. JClaw runs on the same JVM security model they've already approved.

### 4. Multi-Tenancy Is Architectural — Not an Afterthought

JClaw propagates tenant context through every layer:

```
JWT → TenantContext (ThreadLocal) → Session isolation → Memory isolation
                                  → Skill filtering   → Audit tagging
```

Session keys encode tenant boundaries: `{agentId}:{channel}:{accountId}:{peerId}`. A tenant's Telegram user cannot access another tenant's sessions, memory, skills, or audit trail.

**No Python AI framework has this.** LangChain, CrewAI, and AutoGen are single-tenant by design. Adding multi-tenancy requires rebuilding the session, memory, and security layers from scratch.

### 5. 39 Fine-Grained Modules — Take Only What You Need

JClaw is not a monolith. It's 39 modules with explicit dependency boundaries:

```
Need just tools + Spring AI bridge?        → jclaw-tools (1 module)
Need a personal assistant with Telegram?   → jclaw-starter-personal-assistant (starter POM)
Need a multi-channel gateway?              → jclaw-starter-gateway (all channels)
Need K8s monitoring?                       → jclaw-starter-k8s-monitor (9 kubectl tools + triage skill)
Need GOAP multi-step planning?             → jclaw-starter-embabel (Embabel integration)
```

**Compare:** LangChain is `pip install langchain` — one monolithic package. You get everything whether you need it or not, and dependency conflicts are a constant pain point.

---

## Use Cases: From Simple to Sophisticated

### Developer Productivity Agent

**Starter:** `jclaw-starter-anthropic` + Spring Shell

A coding assistant with file read/write, shell execution, glob/grep search, and context window compaction. Runs locally as a Spring Shell CLI — no Docker, no cloud, no API keys beyond the LLM provider.

**Modules used:** jclaw-tools, jclaw-code, jclaw-compaction, jclaw-shell

### Multi-Channel Customer Support Bot

**Starter:** `jclaw-starter-gateway`

A helpdesk bot connected to Telegram, Slack, and Email simultaneously. FAQ search + ticket creation tools. JWT-authenticated API with per-tenant session isolation. Formal audit trail for compliance.

**Modules used:** jclaw-gateway, jclaw-channel-telegram, jclaw-channel-slack, jclaw-channel-email, jclaw-security, jclaw-audit

### Scheduled Business Intelligence

**Starter:** `jclaw-starter-anthropic` + jclaw-cron + jclaw-canvas

A daily briefing agent that runs at 7 AM, fetches weather and news via custom tools, generates an HTML dashboard on Canvas, and delivers a formatted digest to Telegram and Email.

**Modules used:** jclaw-cron, jclaw-canvas, jclaw-channel-telegram, jclaw-channel-email

### GOAP-Orchestrated Code Review

**Starter:** `jclaw-starter-embabel`

An Embabel-powered agent that receives a GitHub PR diff, uses GOAP planning to chain analysis and review actions, and generates a structured code review with inline comments — deterministic, observable, and auditable.

```
GOAP Planner: String(diff) → analyzeDiff → DiffAnalysis → generateReview → ReviewComplete
```

**Modules used:** jclaw-starter-embabel, jclaw-plugin-sdk, jclaw-canvas

### Platform Engineering K8s Monitor

**Starter:** `jclaw-starter-k8s-monitor`

A Telegram-connected K8s monitoring agent with 9 Fabric8 kubectl tools, a behavioral triage skill with mutation safety protocols, and escalation guidelines. One Maven dependency gives your SRE team an AI-powered ops assistant.

```xml
<dependency>
    <groupId>io.jclaw</groupId>
    <artifactId>jclaw-starter-k8s-monitor</artifactId>
    <type>pom</type>
</dependency>
```

**Modules used:** jclaw-tools-k8s, jclaw-skills, jclaw-channel-telegram

### Document Compliance Verification

**Starter:** `jclaw-starter-embabel` + jclaw-documents + jclaw-audit

A GOAP-planned compliance checker that extracts policy rules from regulatory documents (PDF, HTML), evaluates target documents against those rules, generates a pass/fail compliance report with findings and scores, and logs every step to a tamper-evident audit trail.

**Modules used:** jclaw-documents, jclaw-audit, jclaw-starter-embabel

---

## Multi-Agent Flows with Embabel

JClaw integrates with Embabel, Rod Johnson's GOAP-based agent planning framework. When `jclaw-starter-embabel` is on the classpath, JClaw replaces simple ReAct loops with A*-planned action sequences.

### What GOAP Gives You That ReAct Doesn't

| | ReAct (LangChain, CrewAI) | GOAP (JClaw + Embabel) |
|---|---|---|
| **Planning** | LLM decides next action at each step | A* finds optimal action sequence upfront |
| **Determinism** | Non-deterministic (LLM whim) | Deterministic given the same preconditions |
| **Observability** | Log scraping | Typed blackboard with observable intermediate states |
| **Correctness** | Hope-based | Preconditions and effects are compiler-enforced |
| **Replay** | Not possible | Same preconditions → same plan → same execution |

### Example: Multi-Step Travel Planning

```java
@Agent
public class TravelPlannerAgent {

    @Action(precondition = "TravelRequest", effect = "FlightOptions")
    public FlightOptions searchFlights(TravelRequest request) { ... }

    @Action(precondition = "TravelRequest", effect = "HotelOptions")
    public HotelOptions searchHotels(TravelRequest request) { ... }

    @Action(precondition = {"FlightOptions", "HotelOptions", "TravelRequest"},
            achievesGoal = true)
    public TripPlan assemblePlan(FlightOptions flights, HotelOptions hotels,
                                 TravelRequest request) { ... }
}
```

The GOAP planner identifies that flights and hotels can be searched in parallel, then assembles the final plan — without the LLM needing to reason about execution order.

---

## The MCP Ecosystem

JClaw supports the Model Context Protocol in both directions:

**As MCP Client (3 transports):**
- **Stdio** — subprocess management with JSON-RPC over stdin/stdout
- **HTTP** — Streamable HTTP with Bearer auth
- **SSE** — Server-Sent Events with connection sync

Connect to any MCP-compatible tool server — filesystem, database, API wrappers — with YAML configuration:

```yaml
jclaw:
  mcp-servers:
    filesystem:
      transport: stdio
      command: npx
      args: [-y, "@modelcontextprotocol/server-filesystem", /workspace]
```

**As MCP Server:**

Expose JClaw's tool registry to external LLMs via REST endpoints at `/mcp/*`. Any tool registered in JClaw becomes available to Claude Desktop, Cursor, or any MCP-compatible client.

---

## Competitive Positioning

### vs. LangChain / LangChain4j

LangChain is a Python library for chaining LLM calls. LangChain4j brings similar abstractions to Java. Both provide model access and RAG primitives.

**JClaw provides everything above that:** session management, multi-tenancy, 6 channel adapters, plugin system with 16 lifecycle hooks, skills system, audit logging, rate limiting, JWT security, MCP hosting, cron scheduling, browser automation, voice TTS/STT, cross-channel identity linking, and Spring Boot auto-configuration.

You could build all of this on top of LangChain4j. JClaw means you don't have to.

### vs. Spring AI (bare)

Spring AI provides the model abstraction layer — `ChatClient`, `VectorStore`, `ToolCallback`. JClaw builds on Spring AI and adds the agent runtime, session management, tool registry with profile filtering, channel adapters, gateway, and every feature module listed above.

Spring AI is the JDBC. JClaw is the Spring Data JPA.

### vs. CrewAI / AutoGen (Python)

CrewAI uses role-based delegation between LLM agents. AutoGen uses LLM-to-LLM conversation. Both are Python-only, single-tenant, and lack production infrastructure (security, audit, rate limiting, channel adapters).

JClaw replaces their multi-agent patterns with Embabel's GOAP planning — provably correct, deterministic, and observable — while providing the production infrastructure they lack.

### vs. Building from Scratch

A team building a production AI agent on Spring Boot typically spends 3-6 months implementing session management, context window handling, tool registration, channel integration, security, and deployment. JClaw ships all of this as tested, documented, modular components.

---

## Deployment Options

| Mode | Command | Use Case |
|------|---------|----------|
| **Spring Shell CLI** | `./start.sh shell` | Local development, personal assistant |
| **Docker Shell** | `./start.sh cli` | CLI without local Java |
| **Local Gateway** | `./start.sh local` | Development gateway |
| **Docker Gateway** | `./start.sh` | Production deployment |
| **Kubernetes** | `./mvnw k8s:build k8s:apply -Pk8s` | Scalable production |

JKube-integrated Docker image builds — no separate Dockerfile. Helm chart for K8s deployment. Redis for distributed session state. Horizontal scaling across gateway and app tiers.

---

## Who JClaw Is For

**Enterprise Java teams** building internal AI assistants, customer-facing chatbots, or automated workflows — who need multi-tenancy, security, and audit compliance without leaving the JVM.

**Platform engineering teams** who want an AI-powered K8s monitoring agent connected to their team's Slack or Telegram — deployed as a single Spring Boot app.

**Startups with Java backends** who want to add AI capabilities to their existing Spring Boot services without introducing Python infrastructure.

**Any organization** that runs Java in production and doesn't want to maintain a parallel Python stack just for AI.

---

## Getting Started

```bash
git clone https://github.com/jclaw/jclaw.git
cd jclaw
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
./mvnw install -DskipTests

# Interactive setup wizard
./start.sh shell
```

The 12-step onboarding wizard validates your LLM provider connectivity, configures channels with live token validation, and generates `application-local.yml` — ready to run.
