# JaiClaw Core Modules Reference

[Back to Developer Guide](../JAICLAW-DEVELOPER-GUIDE.md)

---

## Table of Contents

1. [jaiclaw-core](#jaiclaw-core)
2. [jaiclaw-channel-api](#jaiclaw-channel-api)
3. [jaiclaw-config](#jaiclaw-config)
4. [jaiclaw-tools](#jaiclaw-tools)
5. [jaiclaw-agent](#jaiclaw-agent)
6. [jaiclaw-skills](#jaiclaw-skills)
7. [jaiclaw-plugin-sdk](#jaiclaw-plugin-sdk)
8. [jaiclaw-memory](#jaiclaw-memory)
9. [jaiclaw-security](#jaiclaw-security)
10. [jaiclaw-gateway](#jaiclaw-gateway)

---

## jaiclaw-core

**Purpose**: Pure Java domain model with zero Spring dependency. Records, sealed interfaces, and enums forming the foundation of JaiClaw.

**Package**: `io.jaiclaw.core`
**Directory**: `core/jaiclaw-core`
**Dependencies**: None (pure Java)

### Key Interfaces & SPIs

- `Message` — sealed interface for conversation messages
- `ToolResult` — sealed interface for tool execution results
- `DeliveryResult` — sealed interface (in channel-api, but modeled here conceptually)
- `ToolCallback` — SPI for implementing tools
- `ToolApprovalHandler` — SPI for human-in-the-loop approval
- `ContextCompactor` — SPI for context window compaction
- `AgentHookDispatcher` — SPI for lifecycle hook dispatch
- `MemoryProvider` — SPI for workspace memory
- `McpToolProvider` — SPI for MCP tool contribution
- `PluginLifecycle` — SPI for plugin registration

### Class Reference

| Class | Type | Description |
|---|---|---|
| Message | sealed interface | All message types: User, Assistant, System, ToolResult |
| UserMessage | record | User-initiated message with sender ID and metadata |
| AssistantMessage | record | LLM response with model ID and token usage |
| SystemMessage | record | System message with context and metadata |
| ToolResultMessage | record | Result from tool call with tool call ID and name |
| TokenUsage | record | Token count metrics including cache tokens |
| Session | record | Conversation session with ID, key, agent, tenant, state, messages |
| SessionState | enum | ACTIVE, IDLE, COMPACTING, CLOSED |
| AgentIdentity | record | Agent metadata with name and description |
| ToolProfile | enum | Tool visibility: MINIMAL, CODING, MESSAGING, FULL |
| ToolProfileHolder | class | ThreadLocal holder for current ToolProfile |
| ToolContext | record | Runtime context provided to tools during execution |
| ToolResult | sealed interface | Success or Error |
| ToolCallback | interface | SPI for implementing tools (name, description, execute) |
| ToolDefinition | record | Metadata describing a tool for the LLM |
| ToolApprovalHandler | interface | SPI for human-in-the-loop tool approval |
| ToolApprovalDecision | sealed interface | Approved, Denied, or Modified |
| ToolLoopConfig | record | Configuration for tool execution loop |
| ToolCallEvent | record | Event record for tool call lifecycle hooks |
| PluginKind | enum | GENERAL, MEMORY, CONTEXT_ENGINE, CHANNEL, PROVIDER |
| PluginDefinition | record | Plugin metadata with name, version, kind |
| PluginLifecycle | interface | SPI for plugin registration/activation/deactivation |
| SkillDefinition | record | Skill with name, description, content, metadata |
| SkillMetadata | record | Version, platform support, required binaries, tenant IDs |
| HookName | enum | Lifecycle hooks covering agent execution pipeline |
| HookHandler | interface | Functional interface for hook handling |
| HookRegistration | record | Registered hook with priority and source |
| TenantContext | interface | Current tenant for multi-tenant isolation |
| DefaultTenantContext | record | Immutable TenantContext implementation |
| TenantContextHolder | class | ThreadLocal holder for current TenantContext |
| McpToolDefinition | record | Tool exposed via hosted MCP server |
| McpToolResult | record | Result of MCP tool execution |
| McpToolProvider | interface | SPI for contributing MCP tools |
| CompactionConfig | record | Context window compaction configuration |
| CompactionResult | record | Result of compaction operation |
| ContextCompactor | interface | SPI for context compaction |
| AgentHookDispatcher | interface | SPI for dispatching lifecycle hooks |
| MemoryProvider | interface | SPI for loading workspace memory |
| CronJob | record | Scheduled cron job definition |
| CronJobResult | sealed interface | Cron execution: Success or Failure |
| AudioResult | record | TTS synthesis result with audio data |
| TranscriptionResult | record | STT transcription with confidence |
| ChatType | enum | DIRECT, GROUP, CHANNEL, THREAD |
| RoutingBinding | record | Maps channel/peer to agent |
| IdentityLink | record | Links platform user identity to canonical ID |
| CanvasAction | record | Action for canvas visual output |
| ProxyAwareHttpClientFactory | class | Creates proxy-aware HttpClient instances; resolves from explicit config or env vars |
| ProxyAwareHttpClientFactory.ProxyConfig | record | Proxy connection details (host, port, username, password) |

### Sealed Interface Hierarchies

```
sealed interface Message
  ├── record UserMessage
  ├── record AssistantMessage
  ├── record SystemMessage
  └── record ToolResultMessage

sealed interface ToolResult
  ├── record Success(String output)
  └── record Error(String error)

sealed interface ToolApprovalDecision
  ├── record Approved
  ├── record Denied(String reason)
  └── record Modified(Map<String,Object> newArgs)

sealed interface CronJobResult
  ├── record Success(String output)
  └── record Failure(String error)
```

### Key SPIs Diagram

```
ToolCallback ←── (implemented by tools in jaiclaw-tools, extensions)
McpToolProvider ←── (implemented in jaiclaw-gateway, extensions)
ContextCompactor ←── (implemented in jaiclaw-compaction)
AgentHookDispatcher ←── (implemented in jaiclaw-plugin-sdk)
MemoryProvider ←── (implemented in jaiclaw-memory)
PluginLifecycle ←── (implemented in jaiclaw-plugin-sdk)
ToolApprovalHandler ←── (implemented by custom approval logic)
```

---

## jaiclaw-channel-api

**Purpose**: Channel adapter SPI, normalized ChannelMessage, attachment handling, and reusable CLI process bridge for external tool integration.

**Package**: `io.jaiclaw.channel`
**Directory**: `core/jaiclaw-channel-api`
**Dependencies**: `jaiclaw-core`

### Class Reference

| Class | Type | Description |
|---|---|---|
| ChannelAdapter | interface | SPI for messaging platform adapters (start/stop/send) |
| ChannelMessage | record | Normalized message between channel and gateway (with Direction) |
| ChannelMessageHandler | interface | Callback invoked by adapter for inbound messages |
| ChannelRegistry | class | Registry of available channel adapters |
| DeliveryResult | sealed interface | Delivery outcome: Success or Failure |
| AttachmentType | enum | PDF, IMAGE, VIDEO, AUDIO, DOCUMENT |
| AttachmentPayload | record | Normalized attachment with filename, type, bytes, MIME |
| AttachmentHandler | interface | SPI for extracting attachments |
| CliProcessConfig | record | Configuration for CLI process bridge |
| JsonRpcClient | class | JSON-RPC 2.0 client over TCP or stdio |
| CliProcessBridge | class | Manages external CLI process with JSON-RPC, health checks, restart |

### Class Relationships

```
ChannelAdapter (SPI)
  ├── start() / stop() / sendMessage()
  ├── channelId() → "telegram", "slack", etc.
  └── implemented by 7 channel modules

ChannelMessage ←→ ChannelMessageHandler
  │
  └── ChannelRegistry (collects all adapters)

CliProcessBridge ← CliProcessConfig
  └── JsonRpcClient (JSON-RPC 2.0 over TCP/stdio)
      └── used by SignalAdapter (EMBEDDED mode)
```

---

## jaiclaw-config

**Purpose**: `@ConfigurationProperties` records for all JaiClaw configuration, bound from `jaiclaw.*` in `application.yml`.

**Package**: `io.jaiclaw.config`
**Directory**: `core/jaiclaw-config`
**Dependencies**: `jaiclaw-core`, Spring Boot

### Class Reference

| Class | Type | Description |
|---|---|---|
| JaiClawProperties | record | Root configuration (`jaiclaw.*`) |
| IdentityProperties | record | Agent identity (name, description) |
| AgentProperties | record | Agent config map with default agent, AgentConfig, AgentModelConfig |
| ToolsProperties | record | Tool visibility, WebToolsProperties (SSRF), ExecToolProperties, CodeToolsProperties (workspace boundary) |
| SkillsProperties | record | Skills loading, allow list, workspace dir. **Default `allowBundled: ["*"]` loads all bundled skills — see [Skills Configuration](../../jaiclaw/docs/user/OPERATIONS.md#skills-configuration) for token impact warning** |
| PluginsProperties | record | Plugin enabling, allow/deny, PluginEntryConfig |
| MemoryProperties | record | Memory backend and model config |
| ModelsProperties | record | Model provider config with ModelProviderConfig, ModelDef |
| SessionProperties | record | Session scope and idle timeout |
| McpServerProperties | record | External MCP server connections with McpServerEntry |
| ToolLoopProperties | record | Tool loop mode, max iterations, approval requirement |
| ChannelsProperties | record | Channel-specific config for all 7 channels |
| HttpProperties | record | HTTP client config (wraps proxy settings) |
| HttpProxyProperties | record | HTTP proxy config (host, port, credentials, non-proxy-hosts) |

### Configuration Hierarchy

```
JaiClawProperties (root: jaiclaw.*)
  ├── IdentityProperties (jaiclaw.identity.*)
  ├── AgentProperties (jaiclaw.agent.*)
  │     └── AgentConfig → AgentModelConfig, ToolPolicyConfig
  ├── ToolsProperties (jaiclaw.tools.*)
  │     ├── WebToolsProperties (.web.ssrf-protection)
  │     ├── ExecToolProperties (.exec.*)
  │     └── CodeToolsProperties (.code.workspace-boundary)
  ├── SkillsProperties (jaiclaw.skills.*)
  │     ├── allowBundled: List<String> (default: ["*"] — ALL skills)
  │     ├── watchWorkspace: boolean (default: true)
  │     └── workspaceDir: String (default: null)
  ├── PluginsProperties (jaiclaw.plugins.*)
  ├── MemoryProperties (jaiclaw.memory.*)
  ├── ModelsProperties (jaiclaw.models.*)
  ├── SessionProperties (jaiclaw.session.*)
  ├── McpServerProperties (jaiclaw.mcp.*)
  ├── ToolLoopProperties (jaiclaw.tool-loop.*)
  ├── ChannelsProperties (jaiclaw.channels.*)
  └── HttpProperties (jaiclaw.http.*)
        └── HttpProxyProperties (jaiclaw.http.proxy.*)
```

---

## jaiclaw-tools

**Purpose**: Central tool registry, built-in tools (file, shell, web, Claude CLI), Spring AI tool bridge, and Embabel orchestration bridge.

**Package**: `io.jaiclaw.tools`
**Directory**: `core/jaiclaw-tools`
**Dependencies**: `jaiclaw-core`, `jaiclaw-channel-api`, Spring AI

### Class Reference

| Class | Type | Description |
|---|---|---|
| ToolRegistry | class | Central registry for all tools available to agent runtime |
| ToolCatalog | class | Constants for tool catalog section names |
| SpringAiToolBridge | class | Adapts JaiClaw ToolCallback to Spring AI ToolCallback |
| AbstractBuiltinTool | abstract class | Base class for built-in tools with error handling |
| FileReadTool | class | Reads file contents with line number support |
| FileWriteTool | class | Writes content to file with parent dir creation |
| ShellExecTool | class | Executes shell command and returns output |
| WebFetchTool | class | Fetches content from URL with timeout; proxy-aware; optional SSRF protection |
| WebSearchTool | class | Web search via DuckDuckGo HTML lite; proxy-aware |
| ClaudeCliTool | class | Invokes Claude CLI in non-interactive mode |
| SsrfGuard | class | SSRF protection — blocks requests to private/internal/link-local IPs |
| BuiltinTools | class | Factory for all built-in tools |
| AgentOrchestrationPort | interface | SPI for external orchestration platform |
| OrchestrationResult | record | Result of orchestrated workflow |
| WorkflowDescriptor | record | Available workflow from orchestration platform |
| NoOpOrchestrationPort | class | No-op when no orchestration configured |

### Class Relationships

```
ToolRegistry (central registry)
  │
  ├── BuiltinTools (factory)
  │     ├── FileReadTool ─────┐
  │     ├── FileWriteTool ────│
  │     ├── ShellExecTool ────│── all extend AbstractBuiltinTool
  │     ├── WebFetchTool ─────│     (implements ToolCallback from core)
  │     │     └── SsrfGuard   │     (opt-in SSRF protection)
  │     ├── WebSearchTool ────│
  │     └── ClaudeCliTool ────┘
  │
  └── SpringAiToolBridge
        └── JaiClaw ToolCallback → Spring AI ToolCallback

SsrfGuard (utility, in io.jaiclaw.tools.exec)
  └── Blocks: localhost, 127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12,
      192.168.0.0/16, 169.254.0.0/16, ::1, fe80::/10, fc00::/7

AgentOrchestrationPort (SPI)
  └── NoOpOrchestrationPort (default)
```

---

## jaiclaw-agent

**Purpose**: Agent runtime orchestrating the full execution lifecycle from user input through LLM interaction to assistant response.

**Package**: `io.jaiclaw.agent`
**Directory**: `core/jaiclaw-agent`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`, `jaiclaw-channel-api`, Spring AI

### Class Reference

| Class | Type | Description |
|---|---|---|
| AgentRuntime | class | Orchestrates execution from user input to response |
| AgentRuntimeContext | record | Context for single execution |
| JaiClawAgent | class | Embabel-based agent with GOAP planning |
| SystemPromptBuilder | class | Builds system prompt from identity, skills, tools, context |
| SessionManager | class | In-memory session manager with tenant-scoped isolation |
| ExplicitToolLoop | class | Manages tool call loop with hook observability and approval gates |

### Class Relationships

```
AgentRuntime (main orchestrator)
  ├── SessionManager (session lifecycle)
  ├── SystemPromptBuilder (prompt construction)
  │     ├── AgentIdentity
  │     ├── SkillLoader (from jaiclaw-skills)
  │     └── ToolRegistry (from jaiclaw-tools)
  ├── SpringAiToolBridge → ChatClient (Spring AI)
  ├── ExplicitToolLoop (tool call management)
  │     └── ToolApprovalHandler (human-in-the-loop)
  └── AgentHookDispatcher (lifecycle hooks)

JaiClawAgent (Embabel integration)
  └── AgentOrchestrationPort
```

---

## jaiclaw-skills

**Purpose**: Skill loading from classpath and workspace, SKILL.md parsing with YAML frontmatter, and tenant-aware skill registry.

**Package**: `io.jaiclaw.skills`
**Directory**: `core/jaiclaw-skills`
**Dependencies**: `jaiclaw-core`

> **Token impact warning**: The bundled skill library contains 59 SKILL.md files (~160KB total). With the default `allow-bundled: ["*"]`, roughly 27 skills pass eligibility checks on a typical machine and are injected verbatim into the system prompt — adding ~26,000 tokens per LLM request. Custom applications and examples should set `jaiclaw.skills.allow-bundled: []` or whitelist specific skills. See [Skills Configuration](../../jaiclaw/docs/user/OPERATIONS.md#skills-configuration) in the Operations Guide.

### Class Reference

| Class | Type | Description |
|---|---|---|
| SkillLoader | class | Loads skills from classpath resources and workspace directories |
| SkillMarkdownParser | class | Parses SKILL.md files with YAML frontmatter into SkillDefinition |
| SkillPromptBuilder | class | Builds skill section of agent system prompt |
| SkillEligibilityChecker | class | Checks platform eligibility for skills |
| TenantSkillRegistry | class | Tenant-aware registry filtering by tenant ID and version |

### Class Relationships

```
SkillLoader
  ├── SkillMarkdownParser (YAML frontmatter → SkillDefinition)
  │     └── SkillMetadata (version, tenantIds, platform)
  └── SkillEligibilityChecker (platform checks)

TenantSkillRegistry
  ├── filters by tenantId via SkillMetadata.tenantIds
  └── version-aware (latest wins)

SkillPromptBuilder
  └── renders skills into system prompt text
```

---

## jaiclaw-plugin-sdk

**Purpose**: Plugin SPI, multi-source discovery (Spring scan + ServiceLoader + explicit), and hook execution on virtual threads.

**Package**: `io.jaiclaw.plugin`
**Directory**: `core/jaiclaw-plugin-sdk`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`

### Class Reference

| Class | Type | Description |
|---|---|---|
| JaiClawPlugin | interface | SPI for plugins (register, activate, deactivate) |
| PluginApi | interface | API surface exposed to plugins during registration |
| PluginApiImpl | class | Default PluginApi delegating to ToolRegistry and PluginRegistry |
| PluginRegistry | class | Aggregates all plugin registrations (tools, hooks, services) |
| PluginRecord | record | Metadata for registered plugin including tools and hooks |
| PluginDiscovery | class | Discovers plugins from Spring scan, ServiceLoader, explicit |
| PluginOrigin | enum | BUNDLED, CLASSPATH, WORKSPACE |
| PluginStatus | enum | LOADED, DISABLED, ERROR |
| HookRunner | class | Executes hooks: void hooks in parallel, modifying hooks sequentially |
| HookRunnerAdapter | class | Adapts HookRunner to AgentHookDispatcher SPI |

### Class Relationships

```
PluginDiscovery
  ├── Spring component scan (@Component JaiClawPlugin beans)
  ├── ServiceLoader (META-INF/services)
  └── explicit PluginRegistry.register()
        │
        ▼
PluginRegistry
  ├── PluginRecord (per plugin: tools, hooks, status)
  └── PluginApiImpl → ToolRegistry (tool registration)

HookRunner
  ├── void hooks → virtual threads (fire-and-forget)
  └── modifying hooks → sequential execution
        │
        └── HookRunnerAdapter → AgentHookDispatcher (SPI bridge)
```

---

## jaiclaw-memory

**Purpose**: Semantic search over workspace files and conversation history with keyword, vector, and hybrid backends.

**Package**: `io.jaiclaw.memory`
**Directory**: `core/jaiclaw-memory`
**Dependencies**: `jaiclaw-core`, Spring AI (optional for VectorStore)

### Class Reference

| Class | Type | Description |
|---|---|---|
| MemorySearchManager | interface | SPI for semantic search |
| MemorySearchResult | record | Result with path, score, snippet, source |
| MemorySearchOptions | record | Max results, min score, session key |
| InMemorySearchManager | class | Keyword-based with tenant partitioning |
| VectorStoreSearchManager | class | Spring AI VectorStore-backed with tenant filtering |
| HybridSearchManager | class | Keyword + temporal decay combined |
| MemorySource | enum | MEMORY, SESSIONS, WORKSPACE, DAILY_LOG, TRANSCRIPT |
| WorkspaceMemoryManager | class | Manages MEMORY.md and daily log files |
| WorkspaceMemoryProvider | class | Adapts to MemoryProvider SPI |
| DailyLogAppender | class | Appends structured notes to daily markdown logs |
| MemorySaveTool | class | Tool for agent to save to long-term memory |
| SessionTranscriptStore | class | Persists session transcripts as JSONL files |

### Class Relationships

```
MemorySearchManager (SPI)
  ├── InMemorySearchManager (default, keyword-based)
  ├── VectorStoreSearchManager (@ConditionalOnBean VectorStore)
  └── HybridSearchManager (keyword + temporal decay)

WorkspaceMemoryManager
  ├── WorkspaceMemoryProvider → MemoryProvider SPI
  ├── DailyLogAppender (daily markdown logs)
  └── MemorySaveTool (agent-facing tool)

SessionTranscriptStore (JSONL persistence)
```

---

## jaiclaw-security

**Purpose**: JWT authentication, API key authentication, rate limiting, and tenant resolution with three security modes.

**Package**: `io.jaiclaw.security`
**Directory**: `core/jaiclaw-security`
**Dependencies**: `jaiclaw-core`, Spring Security

### Class Reference

| Class | Type | Description |
|---|---|---|
| JwtTokenValidator | class | Validates JWT tokens, extracts tenant context and roles |
| RoleToolProfileResolver | class | Maps JWT roles to ToolProfile values |
| JwtAuthenticationFilter | class | Spring Security filter for JWT validation |
| RateLimitFilter | class | Per-sender rate limiting with in-memory counters |
| ApiKeyProvider | class | API key lifecycle: explicit, file-based, or auto-generated |
| ApiKeyAuthenticationFilter | class | API key auth checking X-API-Key header; optional timing-safe comparison |
| JaiClawSecurityProperties | record | Security configuration properties including timing-safe API key flag |
| JaiClawSecurityAutoConfiguration | class | Auto-config for three modes: api-key, jwt, none |
| SecurityModeLogger | class | Logs active security mode at startup |

### Security Mode Flow

```
Inbound Request
  │
  ├── mode=api-key → ApiKeyAuthenticationFilter
  │     └── ApiKeyProvider (resolve key)
  │           ├── JAICLAW_API_KEY env var
  │           ├── ~/.jaiclaw/api-key file
  │           └── auto-generate
  │
  ├── mode=jwt → JwtAuthenticationFilter
  │     ├── JwtTokenValidator (verify + extract claims)
  │     ├── RoleToolProfileResolver (role → ToolProfile)
  │     └── TenantContext.setTenantId(...)
  │
  └── mode=none → passthrough

RateLimitFilter (applied regardless of mode)
  └── per-sender token bucket with cleanup
```

---

## jaiclaw-gateway

**Purpose**: REST API, WebSocket streaming, webhook dispatch, MCP server hosting, routing, tenant resolution, and observability.

**Package**: `io.jaiclaw.gateway`
**Directory**: `core/jaiclaw-gateway`
**Dependencies**: `jaiclaw-core`, `jaiclaw-channel-api`, `jaiclaw-agent`, `jaiclaw-config`, Spring Web

### Class Reference

| Class | Type | Description |
|---|---|---|
| GatewayService | class | Core service bridging channels to agent runtime |
| GatewayController | class | REST controller: /api/chat, /webhook/*, /api/channels |
| GatewayLifecycle | class | SmartLifecycle managing gateway start/stop |
| FilteredGatewayLifecycle | class | Extends GatewayLifecycle; routes messages through a GatewayMessageFilter before GatewayService |
| GatewayMessageFilter | interface | Marker interface for filters inserted between channel adapters and GatewayService |
| WebhookDispatcher | class | Dispatches webhooks to channel handlers |
| WebSocketSessionHandler | class | WebSocket handler for /ws/session/{key} |
| RoutingService | class | Routes by channel, chat type, @mention rules |
| MentionParser | class | Extracts @mention bot IDs (channel-specific patterns) |
| TenantResolver | interface | SPI for resolving tenant from request |
| JwtTenantResolver | class | Resolves tenant from JWT claims |
| BotTokenTenantResolver | class | Resolves tenant from channel bot token mapping |
| CompositeTenantResolver | class | Tries multiple resolvers in order |
| AttachmentRouter | interface | SPI for routing attachments to processing |
| LoggingAttachmentRouter | class | Default no-op with logging |
| GatewayMetrics | class | Atomic request/error counters |
| GatewayHealthIndicator | class | UP/DEGRADED based on channel status |
| McpServerRegistry | class | Registry of MCP tool providers by server name |
| McpController | class | REST controller for /mcp/{serverName}/* |
| McpTransportFactory | class | Creates MCP transport: stdio, SSE, HTTP |
| StdioMcpToolProvider | class | MCP via JSON-RPC over stdin/stdout |
| SseMcpToolProvider | class | MCP via Server-Sent Events; proxy-aware |
| HttpMcpToolProvider | class | MCP via HTTP transport; proxy-aware |
| McpServerConfigBootstrap | class | Bootstraps config-driven MCP connections on startup |
| McpSseServerController | class | SSE server transport: GET /mcp/{server}/sse + POST /jsonrpc |
| McpStdioBridge | class | Server-side stdio bridge: reads JSON-RPC from stdin, writes to stdout |

### Class Relationships

```
GatewayController (@RestController)
  ├── /api/chat → GatewayService → AgentRuntime
  ├── /webhook/* → WebhookDispatcher → ChannelAdapter
  └── /api/channels → ChannelRegistry

WebSocketSessionHandler
  └── /ws/session/{key} → GatewayService (streaming)

GatewayService (core orchestrator)
  ├── AgentRuntime (from jaiclaw-agent)
  ├── SessionManager
  ├── ChannelRegistry
  ├── RoutingService → MentionParser
  └── CompositeTenantResolver
        ├── JwtTenantResolver
        └── BotTokenTenantResolver

McpController (@RestController) — /mcp/{server}/tools/*
  └── McpServerRegistry
        ├── StdioMcpToolProvider (client)
        ├── SseMcpToolProvider (client)
        ├── HttpMcpToolProvider (client)
        └── McpServerConfigBootstrap (init)
              └── McpTransportFactory

McpSseServerController (@RestController) — /mcp/{server}/sse + /jsonrpc
  └── McpServerRegistry → McpToolProvider (any registered provider)

McpStdioBridge (standalone) — stdin/stdout JSON-RPC 2.0
  └── McpToolProvider (any single provider)

GatewayLifecycle (SmartLifecycle)
  └── starts/stops all ChannelAdapters

FilteredGatewayLifecycle (extends GatewayLifecycle)
  └── starts ChannelAdapters with GatewayMessageFilter interposed
        └── GatewayMessageFilter → GatewayService (filter chain)
```

---

## Cross-Module Dependency Summary

```
jaiclaw-core (Layer 0 — pure Java)
  │
  ├── jaiclaw-channel-api (Layer 1 — channel SPI)
  ├── jaiclaw-config (Layer 1 — configuration records)
  │
  ├── jaiclaw-tools (Layer 2 — tool system)
  │     └── depends on: core, channel-api
  │
  ├── jaiclaw-agent (Layer 3 — runtime)
  │     └── depends on: core, tools, channel-api
  ├── jaiclaw-skills (Layer 3)
  │     └── depends on: core
  ├── jaiclaw-plugin-sdk (Layer 3)
  │     └── depends on: core, tools
  ├── jaiclaw-memory (Layer 3)
  │     └── depends on: core
  ├── jaiclaw-security (Layer 3)
  │     └── depends on: core
  │
  └── jaiclaw-gateway (Layer 4 — HTTP/WS)
        └── depends on: core, channel-api, agent, config
```
