# MCP Design Patterns in JaiClaw

**Framework version:** 1.0.1-SNAPSHOT (post-1.0.0 release)
**Audience:** developers designing MCP integrations, architects evaluating JaiClaw for MCP-native systems.

Six canonical Model Context Protocol design patterns, each mapped to concrete JaiClaw code. Every pattern below is exhibited by at least one shipping module — the exemplars link back to real files you can read.

If you haven't yet, read the [MCP overview](mcp.md) first — it explains the SPIs (`McpToolProvider` / `McpResourceProvider`), the `/mcp/{serverName}` routing, and how the gateway wires everything together.

## Contents

1. [Direct API Wrapper](#1-direct-api-wrapper)
2. [Composite Service](#2-composite-service)
3. [MCP-to-Agent](#3-mcp-to-agent)
4. [Event-Driven Integration](#4-event-driven-integration)
5. [Hierarchical MCP](#5-hierarchical-mcp)
6. [Local Resource Access](#6-local-resource-access)
7. [Composing patterns](#composing-patterns)
8. [Choosing between patterns](#choosing-between-patterns)

Each pattern's diagram source is a JSON scene spec under [`mcp-diagrams/`](mcp-diagrams/). Regenerate with `./mcp-diagrams/render.sh`.

---

## 1. Direct API Wrapper

The simplest MCP pattern you can build. Each tool wraps exactly one API call. Low complexity, fast to ship, easy to debug when something breaks.

```
                            ┌────────────┐                ┌────────────────┐
                         ╌▶╌│ Server 2   │╌╌╌╌╌╌╌╌╌╌╌╌╌╌▶╌│  Server API    │
┌──────────┐        ╌╌╌╌╌   └────────────┘                └────────────────┘
│          │   ╌╌╌╌╌
│  Agent   │╌╌╌╌
│          │    ╌╌╌╌╌╌╌╌    ┌────────────┐                ┌────────────────┐
└──────────┘            ╌╌▶╌│ Server 1   │╌╌╌╌╌╌╌╌╌╌╌╌╌╌▶╌│  Server API    │
                            └────────────┘                └────────────────┘

                            MCP protocol
```

Diagram source: [`mcp-diagrams/direct-api-wrapper.json`](mcp-diagrams/direct-api-wrapper.json) (rendered artifact: [`.txt`](mcp-diagrams/direct-api-wrapper.txt))

### When to use it

- The upstream API is stable and coherent (one product, one auth scheme, one data model)
- You want each MCP tool to correspond 1:1 to a real API endpoint — easy to reason about, easy to mock, easy to attribute errors
- The value the MCP layer adds is discovery + argument validation, not orchestration

### How JaiClaw implements it

An `McpToolProvider` implementation whose tools call a single backing API. The provider stays thin: parse args → call the API client → serialize the response. No cross-tool coordination.

### Working example in the repo

**[`extensions/jaiclaw-docs/src/main/java/io/jaiclaw/docs/DocsMcpToolProvider.java`](../../../extensions/jaiclaw-docs/src/main/java/io/jaiclaw/docs/DocsMcpToolProvider.java)** exposes JaiClaw's documentation corpus. Every tool wraps exactly one method on `DocsRepository`:

- `search_docs` → `DocsRepository.search(query, maxResults)`
- Resource `read` → `DocsRepository.findByUri(uri)`

Adopter workflow: one API (`DocsRepository`), one MCP surface (`/mcp/docs`), zero orchestration. Extending it means adding one method to `DocsRepository` and one tool definition to the provider.

The GitHub tools ([`extensions/jaiclaw-tools-github/`](../../../extensions/jaiclaw-tools-github/)) follow the same pattern at a larger scale — 12 tools, each a direct wrapper over one Kohsuke `github-api` call.

### Not this pattern when...

- Multiple API calls are needed to satisfy one LLM request (see [Composite Service](#2-composite-service))
- The upstream API is chatty and the LLM would benefit from a coarser-grained abstraction
- Cross-cutting concerns (caching, retries, aggregation) belong in the MCP layer

---

## 2. Composite Service

Your agent sees one tool. Behind it, multiple APIs are being called and combined. The complexity is hidden. The result is clean.

```
                                                          ┌────────────────┐
                                                       ╌▶╌│  Server API    │
┌──────────┐                ┌────────────┐        ╌╌╌╌╌   └────────────────┘
│          │                │            │   ╌╌╌╌╌
│  Agent   │╌╌╌╌╌╌╌╌╌╌╌╌╌╌▶╌│ Server 1   │╌╌╌
│          │                │            │   ╌╌╌╌╌
└──────────┘                └────────────┘        ╌╌╌╌╌   ┌────────────────┐
                                                       ╌▶╌│  Server API    │
                                                          └────────────────┘
                            MCP protocol
```

Diagram source: [`mcp-diagrams/composite-service.json`](mcp-diagrams/composite-service.json) (rendered artifact: [`.txt`](mcp-diagrams/composite-service.txt))

### When to use it

- One conceptual operation spans multiple upstream systems (send-message-to-channel-with-history → messaging API + session API + channel adapter)
- You want the LLM to see coherent domain verbs, not the plumbing beneath them
- Retries, fallbacks, or fan-out are best-modeled inside the tool (LLMs don't reason well about "call A, if it fails call B")

### How JaiClaw implements it

An `McpToolProvider` whose tools coordinate multiple JaiClaw components (channel adapters, session managers, agent runtime) to service one MCP call. The provider owns the composition; the LLM sees a stable, high-level surface.

### Working example in the repo

**[`extensions/jaiclaw-messaging/src/main/java/io/jaiclaw/messaging/mcp/MessagingMcpToolProvider.java`](../../../extensions/jaiclaw-messaging/src/main/java/io/jaiclaw/messaging/mcp/MessagingMcpToolProvider.java)** exposes messaging as 8 tools. Take `agent_chat` — one LLM call resolves the tenant + agent, opens a session via `SessionManager`, dispatches through the right `ChannelAdapter`, and rolls the response back through the agent runtime. The LLM never sees any of that machinery — just `agent_chat(agentId, message, channel, sessionKey)`.

### Not this pattern when...

- The composition is genuinely simple (2-3 lines) — inlining a Direct API Wrapper is clearer
- The user (agent) actually benefits from seeing the sub-steps as separate tools (transparency, debugging, per-step LLM decision-making)
- The composition depends on runtime signals the LLM should influence — surface the sub-tools individually so the LLM can drive the orchestration

---

## 3. MCP-to-Agent

The server does not try to solve everything itself. It identifies what needs specialist reasoning and routes to the right agent. Modularity by design.

```
┌──────────┐        ┌──────────┐        ┌──────────┐      ┌────────────────┐
│          │        │          │        │ Server   │╌╌╌╌▶╌│ Specialist     │
│  Agent   │╌╌╌╌╌╌▶╌│ Server   │╌╌╌╌╌╌▶╌│          │      │                │
│          │        │          │        │   API    │╌◀╌╌╌╌│   agent        │
└──────────┘        └──────────┘        └──────────┘      └────────────────┘

                    MCP protocol
```

Diagram source: [`mcp-diagrams/mcp-to-agent.json`](mcp-diagrams/mcp-to-agent.json) (rendered artifact: [`.txt`](mcp-diagrams/mcp-to-agent.txt))

### When to use it

- The task requires domain-specific reasoning beyond string manipulation (planning, GOAP, RAG-with-reranking, code generation)
- You have a specialist agent already trained/configured for the sub-problem — the MCP tool should hand off, not re-implement
- You want the specialist agent's identity, tools, and lifecycle to be visible in your audit trail as a separate participant

### How JaiClaw implements it

The **`AgentOrchestrationPort`** SPI ([`core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/embabel/AgentOrchestrationPort.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/embabel/AgentOrchestrationPort.java)) defines the contract for dispatching to a specialist agent runtime:

```java
CompletableFuture<OrchestrationResult> execute(String workflowName, Map<String, Object> input);
List<WorkflowDescriptor> listWorkflows();
```

An `McpToolProvider` receives the LLM's request, translates it into a `workflowName` + input, and hands off to the port. The specialist agent (Embabel, an external LLM, an in-process planner) does the work; the MCP tool returns the result.

### Working example in the repo

**[`extensions/jaiclaw-embabel-delegate/src/main/java/io/jaiclaw/embabel/delegate/EmbabelAgentOrchestrationPort.java`](../../../extensions/jaiclaw-embabel-delegate/src/main/java/io/jaiclaw/embabel/delegate/EmbabelAgentOrchestrationPort.java)** is the concrete impl for Embabel — it looks up the requested agent in `AgentPlatform.agents()`, runs it as an `AgentProcess`, extracts the final blackboard result via `EmbabelInvocations`, and wraps the sync call in `supplyAsync` so pipeline stages can apply timeouts.

The pipeline module then exposes this via an MCP surface: an LLM calls `pipeline_trigger` with `runtime: EMBABEL`, and the specialist agent takes over. Modularity is preserved — swapping Embabel for a different specialist means implementing `AgentOrchestrationPort` once, not rewriting every calling tool.

### Not this pattern when...

- The "specialist" is really just a data lookup — use a Direct API Wrapper
- The routing decision is trivial (only one target) — inline the specialist's logic in the tool
- You need the caller to see the specialist's intermediate work in real time (see [Event-Driven Integration](#4-event-driven-integration))

---

## 4. Event-Driven Integration

The agent triggers an event and continues. Processing happens in the background without blocking the main workflow. Built for systems that need to stay responsive under load.

```
┌──────────┐        ┌──────────┐        ┌──────────┐      ┌────────────────┐
│          │        │          │        │ Event    │      │   Async        │
│  Agent   │╌╌╌╌╌╌▶╌│ Server   │╌╌╌╌╌╌▶╌│          │╌╌╌╌▶╌│  workflow      │
│          │        │          │        │stream    │      │ processing     │
└──────────┘        └──────────┘        └──────────┘      └────────────────┘

                    MCP protocol
```

Diagram source: [`mcp-diagrams/event-driven-integration.json`](mcp-diagrams/event-driven-integration.json) (rendered artifact: [`.txt`](mcp-diagrams/event-driven-integration.txt))

### When to use it

- The downstream work takes seconds-to-minutes and there's no need to block the LLM's turn
- Multiple listeners want to react to the same trigger (audit, metrics, side-effects) without adding orchestration to the tool
- You need back-pressure isolation — a slow downstream mustn't slow the agent

### How JaiClaw implements it

Two complementary primitives:

**`PipelineGateway.trigger()`** ([`extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/gateway/PipelineGateway.java`](../../../extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/gateway/PipelineGateway.java)) is the **fire-and-forget dispatcher**. An MCP tool invokes `trigger(pipelineId, input)` and immediately gets back a `PipelineExecutionHandle`. The pipeline runs on its own thread pool; the MCP tool returns to the agent within a few milliseconds. An alternative `triggerAsync(...)` variant returns a `CompletableFuture<PipelineExecutionResult>` for callers who want to await completion — same dispatcher, different consumer wire-up.

**`HookRunner`** ([`core/jaiclaw-plugin-sdk/src/main/java/io/jaiclaw/plugin/HookRunner.java`](../../../core/jaiclaw-plugin-sdk/src/main/java/io/jaiclaw/plugin/HookRunner.java)) is the **in-process event bus**. Any code path can fire a typed `HookEvent` subclass; every registered handler for that event class is invoked. Two modes:
- `fireVoid(event)` — parallel dispatch on virtual threads, exception-tolerant (audit, metrics, side-effects)
- `fireModifying(event)` — sequential dispatch by priority, handlers can transform the event (prompt redaction, response filtering)

### Working example in the repo

The pipeline MCP tool `pipeline_trigger` ([`extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/mcp/PipelineMcpToolProvider.java`](../../../extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/mcp/PipelineMcpToolProvider.java)) is the canonical instance. The LLM calls `pipeline_trigger(pipelineId, input)`, the tool calls `PipelineGateway.trigger(...)`, the pipeline runs asynchronously through its stages, and the LLM immediately gets back an execution ID it can poll later via `pipeline_status`. Meanwhile, every stage's transition fires `HookEvent` subclasses (`PipelineStageStartedEvent`, `PipelineStageCompletedEvent`) that audit + metrics handlers pick up automatically — no plumbing in the tool.

### Not this pattern when...

- The LLM needs the result before returning to the user (synchronous is simpler)
- The work takes milliseconds — the async overhead is more code than it saves
- There's no legitimate multi-consumer story (audit, metrics, downstream trigger) — a plain `CompletableFuture` return type is enough

---

## 5. Hierarchical MCP

One server owns the routing decisions. Sub-servers own the domain logic. Every layer has a clear responsibility. No overlap, no confusion at scale.

```
                                                     ┌────────────────────┐
                                                   ▶╌│  Customer MCP      │
                                                ╌╌╌  └────────────────────┘
┌──────────┐          ┌────────────────┐     ╌╌╌
│          │          │  Domain-       │  ╌╌╌        ┌────────────────────┐
│  Agent   │╌╌╌╌╌╌╌╌▶╌│   level        │╌╌╌╌╌╌╌╌╌╌╌▶╌│  Wallet MCP        │
│          │          │  server        │  ╌╌╌        └────────────────────┘
└──────────┘          └────────────────┘     ╌╌╌
                                                ╌╌╌  ┌────────────────────┐
                                                   ▶╌│  Payments MCP      │
                                                     └────────────────────┘
```

Diagram source: [`mcp-diagrams/hierarchical-mcp.json`](mcp-diagrams/hierarchical-mcp.json) (rendered artifact: [`.txt`](mcp-diagrams/hierarchical-mcp.txt))

### When to use it

- You have multiple MCP servers already (in-house or third-party) and want to expose them under one coherent surface to the LLM
- Different teams own the sub-servers, and you want the routing surface separate from the domain logic
- The system needs to grow — adding a new sub-server should mean registering a new provider, not rewriting the domain-level router

### How JaiClaw implements it

The gateway acts as both an MCP **host** and an MCP **client**. Three client transports at [`core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/transport/`](../../../core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/transport/):

- **`HttpMcpToolProvider`** — calls remote HTTP MCP servers, tracks `Mcp-Session-Id` header, multiplexes responses by JSON-RPC request ID
- **`SseMcpToolProvider`** — same shape but Server-Sent Events for streaming responses
- **`StdioMcpToolProvider`** — same shape but stdio (subprocess) transport for locally-installed servers

Each transport implements the same `McpToolProvider` SPI, so the domain-level router can register them as `@Bean`s and mount them at any local `/mcp/{name}` route. The LLM sees the domain-level server; underneath, calls fan out to the specialist sub-servers over whichever transport each uses.

### Working example in the repo

Any adopter Spring `@Configuration` can wire this in ~10 lines:

```java
@Bean
public McpToolProvider customerMcp() {
    return new HttpMcpToolProvider(
        "customer",
        URI.create("https://customer-mcp.internal/mcp"),
        // ... auth headers, timeout config
    );
}

@Bean
public McpToolProvider walletMcp() {
    return new HttpMcpToolProvider("wallet",
        URI.create("https://wallet-mcp.internal/mcp"), ...);
}

@Bean
public McpToolProvider paymentsMcp() {
    return new StdioMcpToolProvider("payments", List.of("/opt/payments-mcp/bin/serve"));
}
```

The gateway now hosts `/mcp/customer`, `/mcp/wallet`, `/mcp/payments` — each is a facade over the corresponding sub-server, with JaiClaw's tenant context + audit trail wrapped around every call.

For adding routing logic **on top** (one domain-level MCP tool that decides which sub-server to call), write a `CompositeService`-style `McpToolProvider` (see [pattern 2](#2-composite-service)) whose tools dispatch to the registered sub-providers via the `McpServerRegistry`.

### Not this pattern when...

- You only have one downstream MCP server — no hierarchy needed
- The domain-level router would essentially be a passthrough with no added value (auth wrapping, retries, tenant scoping) — just expose the sub-server directly
- The sub-servers change frequently and the routing decision belongs in application code, not a static Spring configuration

---

## 6. Local Resource Access

The agent reaches directly into the local file system to read, write, and process. No API call needed. No internet required. Full control over performance and privacy.

```
┌──────────┐                          ┌────────────────────────────────┐
│          │  Task: File processing   │   Server -> File system        │
│  Agent   │────────────────────────▶─│                                │
│          │  Execute                 │         (Local MCP)            │
└──────────┘                          └────────────────────────────────┘
```

Diagram source: [`mcp-diagrams/local-resource-access.json`](mcp-diagrams/local-resource-access.json) (rendered artifact: [`.txt`](mcp-diagrams/local-resource-access.txt))

### When to use it

- The data lives on disk (config, logs, checked-out code, generated artifacts) and shipping it to a remote service is wasteful or risky
- Privacy or air-gapped requirements forbid external calls
- The operation is high-throughput (many small reads/writes) and network latency would dominate

### How JaiClaw implements it

Three built-in tools ship out of the box — all in [`core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/):

- **[`FileReadTool.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/FileReadTool.java)** — read file content; input `{path, offset?, limit?}`, output with line numbers; workspace-boundary-safe
- **[`FileWriteTool.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/FileWriteTool.java)** — write file content; creates parent directories; workspace-boundary-safe
- **[`ShellExecTool.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ShellExecTool.java)** — run shell commands; 120s default timeout; policy-gated via `ExecPolicyConfig` (allowlist/denylist)

All three receive a `ToolContext` with `workspaceDir` set. The tools **refuse to reach outside the workspace boundary** — an LLM that asks for `../../etc/passwd` gets an error, not the file. The workspace boundary is enforced in code at [`core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/WorkspaceBoundary.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/WorkspaceBoundary.java).

### Working example in the repo

Every JaiClaw agent has `file_read`, `file_write`, and `shell_exec` in its `ToolRegistry` by default (as long as `jaiclaw-tools` is on the classpath — it always is, transitively via `jaiclaw-spring-boot-starter`). The `security-hardened` profile activates additional guards: `jaiclaw.tools.exec.kubectl.policy` for `kubectl` allowlisting, `jaiclaw.tools.code.workspace-boundary` for path-traversal protection.

For MCP consumers (not in-process agents), any of the built-in tools can be exposed via an `McpToolProvider` — [`AsciiRenderMcpToolProvider.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/mcp/AsciiRenderMcpToolProvider.java) is the model to copy.

### Not this pattern when...

- The data lives in a database or cloud service — use [Direct API Wrapper](#1-direct-api-wrapper)
- The workload is a **single** file operation as part of a larger flow — the tool is right, but the surrounding orchestration might be [Composite Service](#2-composite-service)
- You need audit + retention + backup of every read/write — the file system alone doesn't give you that; wrap the tools with [`AuditingChatModelBeanPostProcessor`](../../../extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/AuditingChatModelBeanPostProcessor.java) or a hook

---

## Composing patterns

The six patterns are not mutually exclusive. A production JaiClaw gateway typically exhibits **all six at once**, behind the same `/mcp/{name}` routing surface:

- Direct API Wrappers for stable third-party APIs (`github`, `discord`, `voicecall`)
- Composite Services for domain verbs (`messaging`, `pipeline`)
- MCP-to-Agent handoffs for specialist reasoning (`pipeline_trigger` with `runtime: EMBABEL`)
- Event-Driven Integration for long-running or fan-out work (`pipeline_trigger` fire-and-forget)
- Hierarchical MCP for third-party MCP servers rehosted under one facade (adopter-configured via `HttpMcpToolProvider` beans)
- Local Resource Access for workspace file operations (`file_read`, `file_write`, `shell_exec`)

**All six share the same tenant context, the same audit trail, the same rate limiting, and the same security policies.** An MCP client can't tell (and doesn't need to know) which pattern each of its tool calls implements.

The `McpServerRegistry` treats every `McpToolProvider` uniformly — Spring's `List<McpToolProvider>` injection sees Direct API Wrappers and Hierarchical MCP client-proxies as the same bean type. This is deliberate: adding a pattern to your gateway is always "register one more Spring bean", never "add a new integration layer".

## Choosing between patterns

Decision tree — pick the leftmost applicable pattern:

```
Is the data on the local filesystem?
    yes → Local Resource Access (§6)
    no  ↓

Is the LLM triggering async work that shouldn't block the tool call?
    yes → Event-Driven Integration (§4)
    no  ↓

Does the tool need to hand off to a specialist agent for reasoning?
    yes → MCP-to-Agent (§3)
    no  ↓

Are you fronting existing MCP servers under one facade?
    yes → Hierarchical MCP (§5)
    no  ↓

Does one MCP tool require multiple API calls to complete?
    yes → Composite Service (§2)
    no  → Direct API Wrapper (§1)
```

When in doubt, start with **Direct API Wrapper** — it's easiest to refactor into any of the others once you have real usage data. Premature composition is the most common failure mode.

## See also

- **[mcp.md](mcp.md)** — MCP feature overview: the SPIs, the gateway wiring, the REST surface, how to write your own MCP server
- **[CLAUDE-DESKTOP-MCP.md](../CLAUDE-DESKTOP-MCP.md)** — recipe for exposing a JaiClaw MCP server as a standalone JBang stdio server (Claude Desktop compatibility)
- **[docs/dev/ARCHITECTURE.md](../../dev/ARCHITECTURE.md)** — gateway architecture, how MCP fits alongside the shell, chat, and channel dispatch surfaces
- **[docs/compliance/README.md](../../compliance/README.md)** — how each pattern intersects with the eight regulatory frameworks (audit trail applies uniformly, tenant metadata carried through every call, FIPS crypto for transport, etc.)
