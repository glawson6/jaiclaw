# Building AI Agents with JaiClaw

*The 8-module architectural blueprint, mapped to concrete JaiClaw code.*

---

> **Looking for the plain-English primer first?** See [WHAT-IS-AGENTIC-AI.md](WHAT-IS-AGENTIC-AI.md) — no computer science degree required.
>
> **Ready to run something?** See [GETTING-STARTED.md](GETTING-STARTED.md) — install, boot a gateway, send your first agent message.
>
> **This document** is the architectural companion for engineers and architects: it decomposes an AI agent into 8 building blocks and shows exactly which JaiClaw code satisfies each block.

## Contents

- [Why 8 modules?](#why-8-modules)
- [The blueprint](#the-blueprint)
- Modules:
  1. [Define Purpose & Scope](#module-1--define-purpose--scope)
  2. [System Prompt Design](#module-2--system-prompt-design)
  3. [Choose LLM](#module-3--choose-llm)
  4. [Tools & Integrations](#module-4--tools--integrations)
  5. [Memory Systems](#module-5--memory-systems)
  6. [Orchestration](#module-6--orchestration)
  7. [User Interface](#module-7--user-interface)
  8. [Testing & Evals](#module-8--testing--evals)
- [Where JaiClaw fits in the landscape](#where-jaiclaw-fits-in-the-landscape)
- [Next steps](#next-steps)

---

## Why 8 modules?

An AI agent is not a single thing. It's a **composition of eight loosely-coupled concerns**, each of which can be answered independently, and each of which has a mature body of practice behind it. The decomposition below (adapted from AI For Leaders' *How to Build an AI Agent*) is the shortest useful list — fewer modules gloss over real decisions; more modules add noise.

Every module below is:

- **Named after the decision it represents** — not the technology that satisfies it, so the same decomposition applies whether you're using JaiClaw, LangGraph, or writing bare code
- **Independently swappable** — changing your LLM (Module 3) shouldn't force changes to your memory backend (Module 5)
- **Backed by JaiClaw code you can inspect** — every module in this document cites the specific classes, interfaces, and configuration paths that implement it

## The blueprint

```
┌────────────────┐           ┌────────────────┐           ┌────────────────┐
│ 1. Purpose     │           │ 2. Sys Prompt  │           │ 3. LLM         │
└────────────────┘╌          └────────────────┘           ╌────────────────┘
                   ╌╌                 ╌                 ╌╌
                     ╌                ╌                ╌
                      ╌               ╌               ╌
                       ╌╌             ╌             ╌╌
                         ╌            ╌            ╌
                          ╌╌┌──────────────────┐ ╌╌
┌────────────────┐          ╌    AGENT         │╌         ┌────────────────┐
│ 5. Memory      │╌╌╌╌╌╌╌╌╌╌│  BLUEPRINT       │╌╌╌╌╌╌╌╌╌╌│ 4. Tools       │
└────────────────┘          │                  │          └────────────────┘
                            │  8 modules       │
                            ╌──────────────────┘╌
                          ╌╌          ╌          ╌╌
                        ╌╌            ╌            ╌╌
                       ╌              ╌              ╌
                     ╌╌               ╌               ╌╌
┌────────────────┐ ╌╌        ┌────────╌───────┐         ╌╌┌────────────────┐
│ 6. Orchestrate │╌          │ 7. UI          │           ╌ 8. Testing     │
└────────────────┘           └────────────────┘           └────────────────┘
```

*Diagram source: [`agent-diagrams/agent-blueprint-hub.json`](agent-diagrams/agent-blueprint-hub.json). Regenerate: `./agent-diagrams/render.sh`*

---

## Module 1 — Define Purpose & Scope

```
MODULE 1 — DEFINE PURPOSE & SCOPE

┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐
│                │  │                │  │   Success      │  │              │
│   Use case     │  │  User needs    │  │   criteria     │  │ Constraints  │
│                │  │                │  │                │  │              │
└────────────────┘  └────────────────┘  └────────────────┘  └──────────────┘
```

**What this module is:** the human-side decisions. Who is this agent for? What outcome does success look like? What is the agent *not allowed* to do? This is the first module because every other module gets shaped by the answers here.

### What JaiClaw provides

The runtime carries every purpose/scope decision through structured configuration rather than convention. Adopters declare:

- **Agent identity** — [`AgentIdentity.java`](../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/model/AgentIdentity.java) — a record holding `id`, `name`, `description`. Populated via YAML (`jaiclaw.identity.*`) or Java `@Bean`, threaded into every prompt build via `SystemPromptBuilder`.
- **Per-agent configuration** — the `AgentConfig` record inside [`AgentProperties.java`](../../core/jaiclaw-config/src/main/java/io/jaiclaw/config/AgentProperties.java) captures `id`, `name`, `workspace`, `model`, `skills`, `tools`, `identity`, `llm`, `systemPrompt`.
- **Root config** — [`JaiClawProperties.java`](../../core/jaiclaw-config/src/main/java/io/jaiclaw/config/JaiClawProperties.java) binds `jaiclaw.*` — the 14 top-level property groups that shape every subsequent module.
- **Constraints** — tool profiles (`ToolPolicyConfig` under `jaiclaw.tools.policy`) allow/deny lists; per-agent `LlmConfig` (temperature, max tokens, timeout); tenant metadata (`data.retention_days`, `data.restriction_flags`) for compliance-scoped agents.

### Adopter starting point

```yaml
jaiclaw:
  identity:
    name: Support Agent
    description: Answers customer support questions from the KB.
  agent:
    default-agent: support
    agents:
      support:
        id: support
        name: Support Agent
        tools:
          profile: full
```

### Working example in the repo

[`jaiclaw-examples/code-review-bot/`](../../jaiclaw-examples/code-review-bot/) — a scoped agent (identity: "Code Review Agent", purpose: PR review) with an explicit tool profile and system prompt strategy declared in its `application.yml`.

### See also

- [`docs/user/COMPLIANCE.md`](COMPLIANCE.md) — per-tenant metadata (`data.restriction_flags`, `data.residency_required`, `hipaa.phi_processing`) that expresses regulatory constraints as first-class scope
- [`docs/user/CONFIGURATION.md`](CONFIGURATION.md) — minimal-viable YAML shapes

---

## Module 2 — System Prompt Design

```
MODULE 2 — SYSTEM PROMPT DESIGN

┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐
│                │  │    Role /      │  │                │  │              │
│    Goals       │  │   Persona      │  │ Instructions   │  │  Guardrails  │
│                │  │                │  │                │  │              │
└────────────────┘  └────────────────┘  └────────────────┘  └──────────────┘
```

**What this module is:** the text that goes into the LLM's `system` role before every conversation turn. It sets the agent's voice, its priorities, what it refuses to do, and how it structures answers.

### What JaiClaw provides

- **`SystemPromptBuilder`** — [`SystemPromptBuilder.java`](../../core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/SystemPromptBuilder.java) assembles the prompt from identity + skills + additional instructions. Called by `AgentRuntime` before every LLM turn.
- **Skills as prompt content** — the [`jaiclaw-skills`](../../core/jaiclaw-skills/) module loads Markdown files from `classpath:/skills/` and (optionally) `workspace/skills/`, then contributes their text to the prompt. Skills are the primary place adopters express *goals* + *instructions*. See [`AUTHORING-SKILLS.md`](AUTHORING-SKILLS.md).
- **AgentMind Soul overlay** — [`extensions/jaiclaw-agentmind-soul/`](../../extensions/jaiclaw-agentmind-soul/) — per-tenant + per-agent persona overlays spliced into the system prompt at build time via `SoulPromptInjector`. Adopters ship curated personas (`concise`, `technical`, `mentor`, `socratic`, `pirate`) as Markdown, swap them per session via a tool call.
- **Guardrails at prompt-build time** — [`BeforePromptBuildEvent`](../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/BeforePromptBuildEvent.java) is a `HookEvent` that lets adopters intercept and modify the prompt. Reference impl: `RegexPromptRedactor` in `extensions/jaiclaw-compliance/` redacts PHI/PII from prompts when the tenant is marked `hipaa.phi_processing=true`.

### Adopter starting point

```yaml
jaiclaw:
  agent:
    default-agent: default
    agents:
      default:
        id: default
        systemPrompt:
          strategy: inline
          content: |
            You are a helpful, terse assistant.
            Always respond in fewer than 100 words.
  skills:
    allow-bundled: []       # opt out of the 59 bundled skills (~26K tokens) unless you want them
```

### Working example in the repo

The [AgentMind persona seeder](../../extensions/jaiclaw-agentmind-soul/src/main/resources/personas/) ships 5 Markdown persona overlays that operate the same prompt-modification path an adopter would use.

### See also

- [`AUTHORING-SKILLS.md`](AUTHORING-SKILLS.md) — how to author Markdown skills that contribute to the system prompt
- [`docs/user/SKILLS.md`](SKILLS.md) — bundled-skill cost tuning (default `["*"]` adds ~26K tokens per LLM call)
- [`docs/compliance/section-508.md`](../compliance/section-508.md) — accessibility guardrails; [`docs/compliance/hipaa.md`](../compliance/hipaa.md) — PHI redaction guardrails

---

## Module 3 — Choose LLM

```
MODULE 3 — CHOOSE LLM

┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐
│                │  │  Parameters    │  │   Context      │  │   Cost /     │
│   Base model   │  │  (temp, ...)   │  │    window      │  │  latency     │
│                │  │                │  │                │  │              │
└────────────────┘  └────────────────┘  └────────────────┘  └──────────────┘
```

**What this module is:** which model, what parameters, how big a context window, at what cost / latency. Includes the multi-provider question (do you want to switch providers without touching code?) and the fallback question (what happens when your primary provider is down?).

### What JaiClaw provides

- **11 LLM providers via Spring AI** — Anthropic, OpenAI, Gemini, Ollama, Bedrock, Azure OpenAI, DeepSeek, Mistral, MiniMax, Vertex AI, OCI GenAI. Selected via `spring.ai.model.chat` (single-provider path) or per-agent `LlmConfig.provider` (multi-agent path).
- **Per-agent LLM config** — [`LlmConfig.java`](../../core/jaiclaw-config/src/main/java/io/jaiclaw/config/LlmConfig.java) holds `provider`, `model`, `temperature` (default 0.7), `maxTokens` (default 4096), `timeoutSeconds` (default 120), `fallbacks` (list of alternate models), `thinkingModel` (extended-reasoning variant).
- **Multi-provider bridge** — `LlmProviderBridgeEnvironmentPostProcessor` in [`core/jaiclaw-config`](../../core/jaiclaw-config/) resolves the effective provider at startup, translating `jaiclaw.*` properties into the Spring AI `spring.ai.*` namespace so an adopter can swap Anthropic ↔ MiniMax ↔ Bedrock without changing agent code.
- **Context-window compaction** — [`extensions/jaiclaw-compaction/`](../../extensions/jaiclaw-compaction/) — `CompactionService` uses tiktoken (jtokkit) to estimate token counts and applies LLM-based summarization when the session context nears the model's window limit. Two compressors ship: `ToolResultCompressor` (compress large tool outputs mid-conversation) and `TruncatingToolResultCompressor` (hard-cut at a threshold).
- **Model catalog** — [`extensions/jaiclaw-model-catalog/`](../../extensions/jaiclaw-model-catalog/) — declarative catalog of known models with their context windows, so operators can pick appropriately without memorizing provider-specific limits.

### Adopter starting point

```yaml
spring:
  ai:
    model:
      chat: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        model: claude-sonnet-4-5

jaiclaw:
  agent:
    agents:
      default:
        llm:
          provider: anthropic
          model: claude-sonnet-4-5
          temperature: 0.3
          maxTokens: 4096
          fallbacks:
            - claude-haiku-4-5
```

### Working example in the repo

[`jaiclaw-examples/agentmind-demo/`](../../jaiclaw-examples/agentmind-demo/) — configures multiple LLM providers with fallback and demonstrates the full three-layer Embabel + Spring AI + JaiClaw configuration pattern documented in [`CLAUDE.md`](../../CLAUDE.md).

### See also

- [`docs/user/OLLAMA-TUNING-GUIDE.md`](OLLAMA-TUNING-GUIDE.md) — parameter tuning for local Ollama models
- [`docs/user/anthropic-models-spring-ai.md`](anthropic-models-spring-ai.md) — Anthropic model IDs for Spring AI
- [`docs/user/features/compaction.md`](features/compaction.md) — context-window management deep-dive

---

## Module 4 — Tools & Integrations

```
MODULE 4 — TOOLS & INTEGRATIONS

┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐
│   Simple   │  │    API     │  │     MCP    │  │   Agent    │  │ Custom   │
│  (local)   │  │  (web/db)  │  │   server   │  │  as tool   │  │   fns    │
│            │  │            │  │            │  │            │  │          │
└────────────┘  └────────────┘  └────────────┘  └────────────┘  └──────────┘
```

**What this module is:** the abilities the agent can exercise beyond text generation. Reading files, calling APIs, invoking other agents, running custom Java functions. This is where an agent stops being a chat interface and starts being a doer.

### What JaiClaw provides

- **`ToolCallback` SPI** — [`ToolCallback.java`](../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/tool/ToolCallback.java) is the core tool interface: `ToolDefinition definition()` + `ToolResult execute(Map<String, Object> params, ToolContext context)`. Every in-process tool implements it; auto-registered via `ToolBeanDiscovery`.
- **8 built-in tools** — [`BuiltinTools.java`](../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/BuiltinTools.java) ships `FileReadTool`, `FileWriteTool`, `ShellExecTool`, `WebFetchTool`, `WebSearchTool`, `ClaudeCliTool`, `AsciiRenderTool`, `AsciiBoxTool` out of the box. All are workspace-boundary-safe (see [`WorkspaceBoundary.java`](../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/WorkspaceBoundary.java)).
- **MCP server hosting** — 22 in-repo `McpToolProvider` implementations covering documentation, messaging, memory, calendar, GitHub, Discord, Kanban, pipeline, and more. Every one is auto-mounted at `/mcp/{serverName}` by the gateway. See [`features/mcp.md`](features/mcp.md) for the SPI and [`features/mcp-design-patterns.md`](features/mcp-design-patterns.md) for the six canonical MCP patterns.
- **Agent-as-tool** — [`AgentOrchestrationPort`](../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/embabel/AgentOrchestrationPort.java) lets one agent invoke another as if it were a tool. Reference impl: `EmbabelAgentOrchestrationPort` bridges to Embabel's GOAP planner.
- **Custom functions** — adopter writes a `@Bean ToolCallback` in their Spring `@Configuration`, framework auto-discovers and registers.
- **Spring AI bridge** — [`SpringAiToolBridge.java`](../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/SpringAiToolBridge.java) adapts JaiClaw's `ToolCallback` to Spring AI's native `ToolCallback` so JaiClaw tools appear seamlessly in agents built on Spring AI's `ChatClient`.

### Adopter starting point

```java
@Configuration
public class MyToolsConfig {
    @Bean
    public ToolCallback fetchOrderTool(OrderService orders) {
        return new AbstractBuiltinTool(new ToolDefinition(
                "fetch_order",
                "Look up an order by ID.",
                ToolCatalog.SECTION_CUSTOM,
                """
                {"type":"object","properties":{"orderId":{"type":"string"}},"required":["orderId"]}"""
        )) {
            @Override
            protected ToolResult doExecute(Map<String, Object> params, ToolContext ctx) {
                var order = orders.findById((String) params.get("orderId"));
                return new ToolResult.Success(order.toJson());
            }
        };
    }
}
```

That's it — auto-discovered, auto-registered, available to every agent.

### Working example in the repo

[`extensions/jaiclaw-tools-github/`](../../extensions/jaiclaw-tools-github/) — 12 `ToolCallback` implementations wrapping the GitHub API. Each is ~50 lines. Auto-mounted at `/mcp/github` via [`GithubToolsMcpProvider.java`](../../extensions/jaiclaw-tools-github/src/main/java/io/jaiclaw/tools/github/mcp/GithubToolsMcpProvider.java).

### See also

- [`AUTHORING-TOOLS.md`](AUTHORING-TOOLS.md) — write a custom `ToolCallback` step-by-step
- [`features/mcp.md`](features/mcp.md) — expose tools as MCP servers
- [`features/mcp-design-patterns.md`](features/mcp-design-patterns.md) — the six MCP design patterns

---

## Module 5 — Memory Systems

```
MODULE 5 — MEMORY SYSTEMS

┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐
│ Episodic   │  │  Working   │  │  Vector    │  │   SQL /    │  │  File    │
│  (conv)    │  │  memory    │  │    DB      │  │  struct    │  │ store    │
│            │  │            │  │            │  │            │  │          │
└────────────┘  └────────────┘  └────────────┘  └────────────┘  └──────────┘
```

**What this module is:** what the agent remembers, at what scope, and for how long. Episodic (last N conversation turns), working (mid-turn scratch), semantic (vector embeddings for RAG), structured (relational lookups), and file-based (artifacts, transcripts, generated content).

### What JaiClaw provides

- **Episodic — `SessionManager` SPI** — [`SessionManager.java`](../../core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/session/SessionManager.java) — `getOrCreate(sessionKey, agentId)`, `appendMessage()`, `retrieveMessages()`, `reset()`. Default `InMemorySessionManager`; Redis backend via [`extensions/jaiclaw-session-redis/`](../../extensions/jaiclaw-session-redis/) survives pod restarts. All session storage tenant-scoped.
- **Working memory** — messages assembled per-session, passed to `ChatClient` for each turn. Hooks (`AgentHookDispatcher`) fire on message send/receive for adopter-supplied side effects.
- **Vector store** — [`VectorStoreSearchManager.java`](../../core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/VectorStoreSearchManager.java) wraps Spring AI's `VectorStore` (works with Weaviate, LanceDB, Chroma, etc.). Fallback: `InMemorySearchManager` (keyword search) when no `VectorStore` bean is present.
- **Structured/SQL** — no framework mandate; adopters use Spring Data / JPA / MyBatis. Example: [`extensions/jaiclaw-kanban/`](../../extensions/jaiclaw-kanban/) uses JPA-backed board/column persistence; [`extensions/jaiclaw-tasks/`](../../extensions/jaiclaw-tasks/) uses `TaskStore` SPI with in-memory + Redis + JDBC-Postgres backends.
- **File storage** — [`ArtifactStore`](../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/artifact/ArtifactStore.java) SPI (`save()`, `findById()`, `delete()`); [`SessionTranscriptStore`](../../core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/SessionTranscriptStore.java) persists message history as JSON.
- **AgentMind Memory** — [`extensions/jaiclaw-agentmind-memory/`](../../extensions/jaiclaw-agentmind-memory/) — per-user blob memory (`MemoryProvider` SPI, `BoundedBlobMemoryStore` reference impl) with TENANT/AGENT/PEER scopes. Char-budgeted compaction. Spliced into the user message as `<memory-context>...</memory-context>`.
- **Memory Wiki** — [`extensions/jaiclaw-memory-wiki/`](../../extensions/jaiclaw-memory-wiki/) — wiki-style shared knowledge base for structured cross-session memory.

### Adopter starting point

```yaml
# In-memory session store (default, no config needed).
# For durable session memory across pod restarts:
jaiclaw:
  agent:
    session:
      backend: redis
  agentmind:
    memory:
      enabled: true   # per-user blob memory overlay
```

```xml
<!-- Add for durable sessions -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-session-redis</artifactId>
</dependency>
```

### Working example in the repo

[`jaiclaw-examples/agentmind-demo/`](../../jaiclaw-examples/agentmind-demo/) exercises the full memory stack — session, AgentMind memory, and Soul persona overlay all wired into one runnable Spring Boot app.

### See also

- [`docs/user/SESSION-BACKENDS.md`](SESSION-BACKENDS.md) — chat-history storage: in-memory vs Redis
- [`docs/user/features/workspace-memory.md`](features/workspace-memory.md) — workspace-scoped memory patterns

---

## Module 6 — Orchestration

```
MODULE 6 — ORCHESTRATION

┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐
│          │ │          │ │          │ │          │ │          │ │         │
│ Routes   │ │Triggers  │ │  Params  │ │  Queues  │ │   A2A    │ │ Errors  │
│          │ │          │ │          │ │          │ │          │ │         │
└──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ └─────────┘
```

**What this module is:** how work flows through the system. Routes are the workflow topology. Triggers are what starts a run. Parameters shape each stage. Queues buffer async work. Agent-to-agent (A2A) is how one agent hands off to another. Error handling is what happens when a stage fails.

### What JaiClaw provides

- **Declarative pipeline DSL** — [`extensions/jaiclaw-pipeline/`](../../extensions/jaiclaw-pipeline/) — YAML pipelines composed of stages (`PROCESSOR`, `AGENT`, `CAMEL`). Definitions come from inline YAML, per-file YAML (via `classpath*:`), or `@Bean` Java DSL. See [`docs/user/pipeline/PIPELINE-STUDIO.md`](pipeline/PIPELINE-STUDIO.md) for the visual builder.
- **`PipelineGateway`** — [`PipelineGateway.java`](../../extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/gateway/PipelineGateway.java) — 3 entry points: `trigger()` (fire-and-forget), `triggerAsync()` (`CompletableFuture`), `triggerAndAwait()` (blocking with timeout).
- **Triggers** — `MANUAL` (`direct:pipeline-<id>`), `HTTP` (`/api/pipelines/{id}/trigger`), `CRON` (via `camel-quartz-starter`), `FILE` (Camel `file://` URI), `CAMEL_URI` (any Camel component — Kafka, JMS, AMQP, etc.).
- **Parameters** — every stage has a `config` map + template resolution (`{{stages.X.output}}`, `{{input}}`, `{{pipeline.executionId}}`, `{{pipeline.tenantId}}`). See [`docs/user/pipeline/PIPELINE-PROCESSOR-CATALOG.md`](pipeline/PIPELINE-PROCESSOR-CATALOG.md).
- **Queues** — default SEDA (in-process); per-stage overrides for Kafka + AMQP via Camel transport config. HMAC-SHA256 + Bearer Token auth on transports.
- **Agent-to-agent** — [`AgentOrchestrationPort`](../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/embabel/AgentOrchestrationPort.java) again — pipeline `AGENT` stages with `runtime: EMBABEL` dispatch to specialist Embabel agents synchronously; agent chat via [`MessagingMcpToolProvider`](../../extensions/jaiclaw-messaging/src/main/java/io/jaiclaw/messaging/mcp/MessagingMcpToolProvider.java) enables MCP-level agent-to-agent messaging.
- **Error handling** — pipeline `errorStrategy: STOP | RETRY | DEAD_LETTER | SKIP`. Framework-wide: [`jaiclaw-web-errors-{core,mvc,webflux}`](../../extensions/jaiclaw-web-errors-core/) modules provide default `@ExceptionHandler` behaviour that logs 5xx errors before returning and does not leak `ex.getMessage()` to unauthenticated callers.

### Adopter starting point

```yaml
jaiclaw:
  pipeline:
    enabled: true
    pipelines:
      - id: research-brief
        trigger:
          type: HTTP
        stages:
          - name: search
            type: PROCESSOR
            bean: WebSearchProcessor
            config: { query: "{{input}}" }
          - name: summarize
            type: AGENT
            agent: default
            config: { promptTemplate: "Summarize: {{stages.search.output}}" }
        errorStrategy: RETRY
```

### Working example in the repo

[`jaiclaw-examples/support-triage-pipeline/`](../../jaiclaw-examples/support-triage-pipeline/) — 3-stage pipeline (classify → route → respond) with error handling and audit events.

### See also

- [`docs/user/pipeline/PIPELINE-DASHBOARD.md`](pipeline/PIPELINE-DASHBOARD.md) — read-only live pipeline dashboard
- [`docs/user/pipeline/PIPELINE-STUDIO-API.md`](pipeline/PIPELINE-STUDIO-API.md) — authoring REST surface for hot deploy/undeploy
- [`docs/user/WEB-ERROR-HANDLING.md`](WEB-ERROR-HANDLING.md) — framework default exception handling

---

## Module 7 — User Interface

```
MODULE 7 — USER INTERFACE

┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐
│    Chat        │  │                │  │     API        │  │   Slack /    │
│ interface      │  │   Web app      │  │  endpoint      │  │  Discord     │
│                │  │                │  │                │  │              │
└────────────────┘  └────────────────┘  └────────────────┘  └──────────────┘
```

**What this module is:** how end users talk to the agent. Terminal? Web page? Slack message? A cURL against an API? An MCP client like Claude Desktop? Each surface has its own conventions, formats, and latency budgets.

### What JaiClaw provides

- **Interactive shell** — [`apps/jaiclaw-shell/`](../../apps/jaiclaw-shell/) + [`apps/jaiclaw-cli/`](../../apps/jaiclaw-cli/) — Spring Shell REPL with `chat`, `new-session`, `sessions`, `session-history`, `tools`, `status`, etc. Fast-path commands (`version`, `doctor`) run in bash without JVM startup.
- **Web apps** — [`apps/jaiclaw-pipeline-studio/`](../../apps/jaiclaw-pipeline-studio/) (React + React Flow SPA for pipeline authoring) and [`extensions/jaiclaw-pipeline-dashboard/`](../../extensions/jaiclaw-pipeline-dashboard/) (read-only monitoring). Both served by any gateway with the appropriate module on classpath.
- **API endpoints** — [`core/jaiclaw-gateway/`](../../core/jaiclaw-gateway/) exposes `/api/chat`, `/api/sessions`, `/api/pipelines/*`, `/mcp/*`, `/actuator/*`. All authenticated (API key or JWT); all tenant-aware.
- **Channel adapters (11 shipped)** — [`ChannelAdapter.java`](../../core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/ChannelAdapter.java) SPI. Implementations under [`channels/`](../../channels/): Telegram, Slack, Discord, Teams, Google Chat, LINE, Matrix, Signal, Email, SMS, WhatsApp. Each converts platform-native messages to/from a common `ChannelMessage` record.
- **GitHub Actions bot** — [`apps/jaiclaw-cli-github/`](../../apps/jaiclaw-cli-github/) — a runnable fat jar invoked by a GitHub Actions workflow on every `/`-prefixed PR/issue comment. Dispatches to slash commands (`/chat`, `/faq`, `/summarize`, `/help`).
- **Claude Desktop compatibility** — [`docs/user/CLAUDE-DESKTOP-MCP.md`](CLAUDE-DESKTOP-MCP.md) — recipe for exposing a JaiClaw MCP server as a standalone JBang stdio server usable by Claude Desktop.

### Adopter starting point

```yaml
# Enable a channel — Slack shown; same shape for others.
jaiclaw:
  channels:
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
      app-token: ${SLACK_APP_TOKEN}
```

### Working example in the repo

Every runnable example under [`jaiclaw-examples/`](../../jaiclaw-examples/) demonstrates a UI surface. [`jaiclaw-examples/kanban-demo/`](../../jaiclaw-examples/kanban-demo/) shows REST + SSE + ASCII + Actuator surfaces on one running app.

### See also

- [`AUTHORING-CHANNELS.md`](AUTHORING-CHANNELS.md) — build a new channel adapter for a messaging platform we don't ship
- [`docs/compliance/section-508.md`](../compliance/section-508.md) — accessibility posture across every UI surface

---

## Module 8 — Testing & Evals

```
MODULE 8 — TESTING & EVALS

┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐
│   Unit         │  │   Latency      │  │   Quality      │  │  Iterate     │
│   tests        │  │   testing      │  │   metrics      │  │ & improve    │
│                │  │                │  │                │  │              │
└────────────────┘  └────────────────┘  └────────────────┘  └──────────────┘
```

**What this module is:** how you know the agent works today and how you'll know when a change breaks it tomorrow. Includes unit tests of tools and hooks, latency measurements against your SLA, quality metrics for LLM outputs (accuracy, refusal rate, format compliance), and the iteration loop that closes findings into improvements.

### What JaiClaw provides

- **Unit tests via Spock** — the reactor ships ~540 `*Spec.groovy` files across every module. Framework: Spock (Groovy BDD). Adopters follow the same pattern for their custom tools + agents. See [`docs/user/AUTHORING-TOOLS.md`](AUTHORING-TOOLS.md) § "Testing your tool" for the shape.
- **Latency + quality metrics** — Spring Boot Actuator + Micrometer instrumentation throughout. Custom actuator endpoints: `/actuator/pipelines`, `/actuator/kanban`, `/actuator/agentmind-tendencies`. Metrics namespace: `jaiclaw.*` (tool calls, LLM latencies, session events).
- **Audit trail as an eval substrate** — [`AuditEvent.java`](../../extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/AuditEvent.java) captures `timestamp`, `actor`, `action`, `outcome` (SUCCESS/FAILURE/DENIED), `details`, plus compliance fields (`lawfulBasis`, `dataCategories`, `recipients`, `retentionDays`, `consentToken`). `AuditingChatModelBeanPostProcessor` emits `model.inference.request` on every LLM call.
- **Trajectory recording** — [`TrajectoryRecorder`](../../extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/TrajectoryRecorder.java) captures step-by-step action tracking; [`TranscriptStore`](../../extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/TranscriptStore.java) archives full session Markdown for post-hoc review.
- **E2E validation skills** — under [`.claude/skills/`](../../.claude/skills/): `e2e-test` (JaiClaw bootstrap + provider connectivity), `agentmind-e2e`, `kanban-e2e`, `pipeline-author`, plus `dep-check` (dependency updates), `security-scan` (CVEs + secrets + OWASP), and the [Maven `jaiclaw:compliance-report` goal](../../jaiclaw-maven-plugin/src/main/java/io/jaiclaw/maven/ComplianceReportMojo.java) (cross-checks docs vs code).
- **Iterate & improve** — the audit trail feeds any adopter-supplied SIEM / analytics system. LLM-call metrics (`model.inference.request` events) drive dashboards. Trajectory records enable regression comparison across model or prompt changes.

### Adopter starting point

```groovy
// A Spock spec for a custom tool
class FetchOrderToolSpec extends Specification {
    def "returns order details for a valid ID"() {
        given:
        def orders = Mock(OrderService)
        def tool = new FetchOrderTool(orders)
        orders.findById("A-42") >> new Order("A-42", "shipped")

        when:
        def result = tool.execute([orderId: "A-42"], null)

        then:
        result instanceof ToolResult.Success
        result.content().contains("shipped")
    }
}
```

### Working example in the repo

Every extension module has its own Spock spec suite. See [`extensions/jaiclaw-tools-github/src/test/groovy/`](../../extensions/jaiclaw-tools-github/src/test/groovy/) for a comprehensive tool-testing example, or [`.claude/skills/e2e-test/SKILL.md`](../../.claude/skills/e2e-test/SKILL.md) for the framework-wide e2e recipe.

### See also

- [`docs/user/PRODUCTION-DEPLOYMENT.md`](PRODUCTION-DEPLOYMENT.md) § 6 Observability — actuator + Micrometer + metric namespaces
- [`docs/compliance/README.md`](../compliance/README.md) — audit trail as a compliance evidence substrate

---

## Where JaiClaw fits in the landscape

The "How to Build an AI Agent" infographic (see attribution below) grouped popular AI agent tools into four categories. Here is where JaiClaw sits:

| Category | Product / Platform | LLM | Deployment | Key Features | Best For |
|---|---|---|---|---|---|
| **Development Frameworks** | **JaiClaw** | Any (Spring AI: 11 providers) | Local / Cloud / K8s | Multi-tenant by default, 22 MCP surfaces, 8-framework compliance substrate (Section 508, FedRAMP, FISMA, NIST 800-53, FIPS 140-3, CMMC, HIPAA, GDPR), declarative pipelines, 11 channels | Enterprise Java shops, compliance-critical deployments, multi-agent Spring Boot applications |
| Development Frameworks | LangGraph | Any | Local / Cloud | Graph-based flows, state management, cycles | Complex workflows, production apps |
| Development Frameworks | CrewAI | Any | Local / Cloud | Role-based, 40+ integrations, task delegation | Multi-agent teams, autonomous systems |
| Development Frameworks | LlamaIndex | Any | Local / Cloud | RAG-first, data connectors, query engines | Knowledge-intensive apps, document Q&A |

Other categories from the infographic — **Consumer AI Agents** (ChatGPT, Claude, Perplexity), **Agentic Coding Tools** (Cursor, Windsurf, Claude Code), and **No-Code Builders** (Lindy, Relay.app, n8n) — target different audiences and use cases. Adopters typically build *on top of* Development Frameworks like JaiClaw to produce systems that end users consume through interfaces resembling the other three categories.

For a deeper positioning comparison against Spring AI, LangChain4j, and Embabel-alone, see [`docs/POSITIONING.md`](../POSITIONING.md).

## Next steps

You've seen the 8-module blueprint and JaiClaw's answers to each. The natural next reads:

- **Hands-on** — [`GETTING-STARTED.md`](GETTING-STARTED.md) — install JaiClaw, boot a gateway, send your first agent message
- **Configuration** — [`CONFIGURATION.md`](CONFIGURATION.md) — minimal-viable YAML for the three most common setups (Anthropic+Telegram, OpenAI+Slack, Ollama-only)
- **Extending** — [`AUTHORING-TOOLS.md`](AUTHORING-TOOLS.md), [`AUTHORING-SKILLS.md`](AUTHORING-SKILLS.md), [`AUTHORING-CHANNELS.md`](AUTHORING-CHANNELS.md)
- **Production** — [`PRODUCTION-DEPLOYMENT.md`](PRODUCTION-DEPLOYMENT.md) — Kubernetes manifests, Helm values, secrets, observability, security hardening, cloud-provider notes, runbook

---

