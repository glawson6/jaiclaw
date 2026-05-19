# JaiClaw Developer Guide

> Comprehensive reference for the JaiClaw framework — architecture, modules, classes, operations, and design patterns.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Class Index](#class-index)
4. [Module Quick Reference](#module-quick-reference)
5. [Operations Quick Start](#operations-quick-start)
6. [Design Patterns](#design-patterns)

---

## Overview

**JaiClaw** is a Java 21 / Spring Boot 3.5 / Spring AI personal AI assistant framework. It's an embeddable library with a gateway for multi-channel messaging (Telegram, Slack, Discord, Email, SMS, Signal, Teams), a plugin system, tool execution, skills, memory, document processing, audit logging, and MCP server hosting.

### Tech Stack

| Technology | Version | Role |
|---|---|---|
| Java | 21 | Language (records, sealed interfaces, pattern matching, virtual threads) |
| Spring Boot | 3.5.6 | Application framework |
| Spring AI | 1.1.1 | LLM integration (Anthropic, OpenAI, Gemini, Ollama) |
| Spring Shell | 3.4.0 | Interactive CLI |
| Embabel Agent | 0.3.5 | Goal-oriented agent orchestration |
| Spock | 2.4-M4 | Testing framework (Groovy 4.0) |

### Directory Layout

```
jaiclaw/
  pom.xml                         (root aggregator)
  jaiclaw-bom/                      (standalone BOM)
  jaiclaw-spring-boot-starter/      (central auto-config)

  core/                           # 10 modules — essential JaiClaw runtime
    jaiclaw-core/  jaiclaw-channel-api/  jaiclaw-config/  jaiclaw-tools/
    jaiclaw-agent/  jaiclaw-skills/  jaiclaw-plugin-sdk/  jaiclaw-memory/
    jaiclaw-security/  jaiclaw-gateway/

  channels/                       # 7 modules — one per messaging platform
    jaiclaw-channel-telegram/  jaiclaw-channel-slack/  jaiclaw-channel-discord/
    jaiclaw-channel-email/  jaiclaw-channel-sms/  jaiclaw-channel-signal/
    jaiclaw-channel-teams/

  extensions/                     # 19 modules — optional add-on capabilities
    jaiclaw-documents/  jaiclaw-media/  jaiclaw-audit/  jaiclaw-compaction/
    jaiclaw-browser/  jaiclaw-code/  jaiclaw-cron/  jaiclaw-cron-manager/
    jaiclaw-voice/  jaiclaw-identity/  jaiclaw-canvas/  jaiclaw-docstore/
    jaiclaw-docstore-telegram/  jaiclaw-subscription/  jaiclaw-subscription-telegram/
    jaiclaw-tools-k8s/  jaiclaw-tools-security/  jaiclaw-calendar/
    jaiclaw-messaging/

  tools/                          # 4 modules — standalone CLI tools
    jaiclaw-perplexity/  jaiclaw-rest-cli-architect/
    jaiclaw-skill-creator/  jaiclaw-prompt-analyzer/

  apps/                           # 3 modules — runnable Spring Boot apps
    jaiclaw-gateway-app/  jaiclaw-shell/  jaiclaw-cron-manager-app/

  jaiclaw-maven-plugin/             # jaiclaw:analyze goal — CI token budget enforcement
  jaiclaw-starters/                 # 10 starters
  jaiclaw-examples/                 # 16 examples
```

---

## Architecture

### High-Level Layered Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                    RUNNABLE APPS (Layer 7)                      │
│  jaiclaw-gateway-app    jaiclaw-shell    jaiclaw-cron-manager-app    │
├────────────────────────────────────────────────────────────────┤
│                    STARTERS (Layer 6)                           │
│  starter-gateway  starter-shell  starter-anthropic  ...        │
├────────────────────────────────────────────────────────────────┤
│                    AUTO-CONFIG (Layer 5)                        │
│  jaiclaw-spring-boot-starter (Phases 1-4)                        │
├────────────────────────────────────────────────────────────────┤
│                    GATEWAY + CHANNELS (Layer 4)                 │
│  jaiclaw-gateway        Telegram  Slack  Discord  Email  SMS     │
├────────────────────────────────────────────────────────────────┤
│                    FEATURE MODULES (Layer 3)                    │
│  jaiclaw-agent   jaiclaw-skills   jaiclaw-plugin-sdk   jaiclaw-memory  │
│  jaiclaw-security  jaiclaw-documents  jaiclaw-media  jaiclaw-audit     │
│  jaiclaw-compaction  jaiclaw-browser  jaiclaw-cron  jaiclaw-voice      │
│  jaiclaw-identity  jaiclaw-canvas  jaiclaw-code  jaiclaw-docstore      │
├────────────────────────────────────────────────────────────────┤
│                    TOOL LAYER (Layer 2)                         │
│  jaiclaw-tools   jaiclaw-tools-k8s   jaiclaw-tools-security          │
├────────────────────────────────────────────────────────────────┤
│                    CHANNEL SPI (Layer 1)                        │
│  jaiclaw-channel-api   jaiclaw-config                              │
├────────────────────────────────────────────────────────────────┤
│                    CORE (Layer 0) — Pure Java                   │
│  jaiclaw-core                                                    │
└────────────────────────────────────────────────────────────────┘
```

### Module Dependency Graph

```
jaiclaw-core (pure Java — NO Spring)
  ├──→ jaiclaw-channel-api (ChannelAdapter SPI, CliProcessBridge)
  │      ├──→ jaiclaw-channel-telegram, slack, discord, email, sms, signal, teams
  │      └──→ jaiclaw-tools
  ├──→ jaiclaw-config (@ConfigurationProperties records)
  ├──→ jaiclaw-tools (ToolRegistry, SpringAiToolBridge, built-in tools)
  │      ├──→ jaiclaw-tools-k8s, jaiclaw-tools-security
  │      └──→ jaiclaw-agent (AgentRuntime, SessionManager)
  ├──→ jaiclaw-skills, jaiclaw-plugin-sdk, jaiclaw-memory, jaiclaw-security
  ├──→ jaiclaw-documents, jaiclaw-media, jaiclaw-audit, jaiclaw-compaction
  ├──→ jaiclaw-browser, jaiclaw-cron, jaiclaw-voice, jaiclaw-identity, jaiclaw-canvas
  └──→ jaiclaw-gateway → jaiclaw-spring-boot-starter → apps
```

### Message Flow

```
1. Platform sends webhook/event to ChannelAdapter
2. Adapter normalizes → ChannelMessage
3. Session Router computes key: {agentId}:{channel}:{accountId}:{peerId}
4. SessionManager finds/creates session
5. AgentRuntime.run(message, context)
6. SystemPromptBuilder builds prompt (identity + skills + tools)
7. Spring AI ChatClient sends to LLM
8. LLM may invoke tools → SpringAiToolBridge → ToolRegistry
9. LLM returns response
10. AgentRuntime wraps as AssistantMessage, appends to session
11. Gateway routes response to originating ChannelAdapter
12. Adapter sends via platform API
```

### Auto-Configuration Phases

```
Phase 1: Spring AI providers → ChatModel → ChatClient.Builder
Phase 2: JaiClawAutoConfiguration → ToolRegistry, AgentRuntime, SessionManager
Phase 3: JaiClawGatewayAutoConfiguration → GatewayService, Controllers, MCP
Phase 4: JaiClawChannelAutoConfiguration → Channel adapters
```

See [dev-guide/starter.md](dev-guide/starter.md) for full auto-configuration reference.

---

## Class Index

Every class in JaiClaw, alphabetically. Click **Details** to jump to the module satellite file.

| Class | Module | Type | Description | Details |
|---|---|---|---|---|
| AbstractBuiltinTool | jaiclaw-tools | abstract class | Base class for built-in tools | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| AbstractK8sTool | jaiclaw-tools-k8s | abstract class | Base class for Kubernetes tools | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| AbstractSecurityTool | jaiclaw-tools-security | abstract class | Base class for security tools | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| AccessChange | jaiclaw-subscription | record | Change in access rights | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| AccessChangeType | jaiclaw-subscription | enum | GRANT, REVOKE | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| AddRequest | jaiclaw-docstore | record | Request to add entry to DocStore | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| AdvertiseCapabilitiesTool | jaiclaw-tools-security | class | Advertises client capabilities | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| AgentHookDispatcher | jaiclaw-core | interface | SPI for dispatching lifecycle hooks | [View](dev-guide/core-modules.md#jaiclaw-core) |
| AgentIdentity | jaiclaw-core | record | Agent metadata with name and description | [View](dev-guide/core-modules.md#jaiclaw-core) |
| AgentOrchestrationPort | jaiclaw-tools | interface | SPI for external orchestration | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| AgentProperties | jaiclaw-config | record | Agent configuration map | [View](dev-guide/core-modules.md#jaiclaw-config) |
| AgentRequest | jaiclaw-perplexity | record | Perplexity Agent API request | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| AgentResponse | jaiclaw-perplexity | record | Perplexity Agent API response | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| AgentRuntime | jaiclaw-agent | class | Orchestrates execution lifecycle | [View](dev-guide/core-modules.md#jaiclaw-agent) |
| AgentRuntimeContext | jaiclaw-agent | record | Context for single execution | [View](dev-guide/core-modules.md#jaiclaw-agent) |
| AgentStep | jaiclaw-perplexity | record | Research step in agent response | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| AnalysisReport | jaiclaw-prompt-analyzer | record | Token usage analysis report | [View](dev-guide/cli-tools.md#jaiclaw-prompt-analyzer) |
| AnalysisResult | jaiclaw-docstore | record | Document analysis result | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| ApiKeyAuthenticationFilter | jaiclaw-security | class | API key auth filter | [View](dev-guide/core-modules.md#jaiclaw-security) |
| ApiKeyBootstrapValidator | jaiclaw-tools-security | class | API key bootstrap validation | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| ApiKeyProvider | jaiclaw-security | class | API key lifecycle management | [View](dev-guide/core-modules.md#jaiclaw-security) |
| AssistantMessage | jaiclaw-core | record | LLM-generated response | [View](dev-guide/core-modules.md#jaiclaw-core) |
| AttachmentHandler | jaiclaw-channel-api | interface | SPI for extracting attachments | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| AttachmentPayload | jaiclaw-channel-api | record | Normalized attachment payload | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| AttachmentRouter | jaiclaw-gateway | interface | SPI for routing attachments | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| AttachmentType | jaiclaw-channel-api | enum | PDF, IMAGE, VIDEO, AUDIO, DOCUMENT | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| ArtifactStatus | jaiclaw-core | enum | PENDING, PROCESSING, COMPLETED, FAILED | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ArtifactStore | jaiclaw-core | interface | SPI for binary artifact persistence | [View](dev-guide/core-modules.md#jaiclaw-core) |
| AuditEvent | jaiclaw-audit | record | Immutable audit event | [View](dev-guide/extensions.md#jaiclaw-audit) |
| AuditLogger | jaiclaw-audit | interface | SPI for audit logging | [View](dev-guide/extensions.md#jaiclaw-audit) |
| AudioResult | jaiclaw-core | record | TTS audio result | [View](dev-guide/core-modules.md#jaiclaw-core) |
| AuthConfig | jaiclaw-rest-cli-architect | record | API authentication config | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| AuthorizationCodeFlow | jaiclaw-identity | class | PKCE auth code flow: URL, exchange, userinfo | [View](dev-guide/extensions.md#jaiclaw-identity) |
| AuthProfileFileLock | jaiclaw-identity | class | File locking with exponential backoff | [View](dev-guide/extensions.md#jaiclaw-identity) |
| AuthProfileStoreManager | jaiclaw-identity | class | Credential store file I/O, locking, merge | [View](dev-guide/extensions.md#jaiclaw-identity) |
| AuthProfileStoreSerializer | jaiclaw-identity | class | Jackson serializer with OpenClaw compat | [View](dev-guide/extensions.md#jaiclaw-identity) |
| AuthResult | jaiclaw-tools-security | record | Authentication result | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| BasicDocStoreAnalyzer | jaiclaw-docstore | class | Basic analyzer via pipeline | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| BootstrapTrust | jaiclaw-tools-security | enum | Bootstrap trust method | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| BootstrapValidator | jaiclaw-tools-security | interface | SPI for bootstrap auth | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| BotTokenTenantResolver | jaiclaw-gateway | class | Resolves tenant from bot token | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| BrowserConfig | jaiclaw-browser | record | Browser service config | [View](dev-guide/extensions.md#jaiclaw-browser) |
| BrowserService | jaiclaw-browser | class | Playwright lifecycle and tab pool | [View](dev-guide/extensions.md#jaiclaw-browser) |
| BrowserSession | jaiclaw-browser | class | Single browser tab session | [View](dev-guide/extensions.md#jaiclaw-browser) |
| BrowserTools | jaiclaw-browser | class | Factory for browser tools | [View](dev-guide/extensions.md#jaiclaw-browser) |
| BrowserLauncher | jaiclaw-identity | class | Platform-specific browser opening | [View](dev-guide/extensions.md#jaiclaw-identity) |
| BuiltinTools | jaiclaw-tools | class | Factory for all built-in tools | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| CamelPollingAutoConfiguration | jaiclaw-spring-boot-starter | class | Auto-configures CamelTelegramPollingStrategy when Camel is on classpath | [View](dev-guide/starter.md) |
| CamelTelegramPollingStrategy | jaiclaw-channel-telegram | class | Apache Camel-based Telegram polling implementation | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| CanvasAction | jaiclaw-core | record | Action for canvas visual output | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CanvasConfig | jaiclaw-canvas | record | Canvas host config | [View](dev-guide/extensions.md#jaiclaw-canvas) |
| CanvasFileManager | jaiclaw-canvas | class | Manages HTML files | [View](dev-guide/extensions.md#jaiclaw-canvas) |
| CanvasService | jaiclaw-canvas | class | Canvas operations orchestrator | [View](dev-guide/extensions.md#jaiclaw-canvas) |
| CanvasTools | jaiclaw-canvas | class | Factory for canvas tools | [View](dev-guide/extensions.md#jaiclaw-canvas) |
| ChallengeResponseTool | jaiclaw-tools-security | class | Challenge nonce response | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| ChannelAdapter | jaiclaw-channel-api | interface | SPI for messaging adapters | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| ChannelMessage | jaiclaw-channel-api | record | Normalized channel message | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| ChannelMessageHandler | jaiclaw-channel-api | interface | Callback for inbound messages | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| ChannelRegistry | jaiclaw-channel-api | class | Registry of channel adapters | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| ChannelsProperties | jaiclaw-config | record | Channel-specific config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| ChatCommands | jaiclaw-shell | class | Shell chat commands | [View](dev-guide/apps.md#jaiclaw-shell) |
| ChatType | jaiclaw-core | enum | DIRECT, GROUP, CHANNEL, THREAD | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CheckoutResult | jaiclaw-subscription | record | Checkout session result | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| Choice | jaiclaw-perplexity | record | Sonar response choice | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| ChunkingStrategy | jaiclaw-documents | record | Text splitting strategy | [View](dev-guide/extensions.md#jaiclaw-documents) |
| Citation | jaiclaw-perplexity | record | Source citation | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| ClaudeCliTool | jaiclaw-tools | class | Claude CLI invocation tool | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| CliArchitectAutoConfiguration | jaiclaw-rest-cli-architect | class | Auto-configuration for CLI architect | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| CliProcessBridge | jaiclaw-channel-api | class | Manages external CLI process | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| CliProcessConfig | jaiclaw-channel-api | record | CLI process bridge config | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| ClientCertBootstrapValidator | jaiclaw-tools-security | class | Client cert validator | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| CodeTools | jaiclaw-code | class | Factory for code tools | [View](dev-guide/extensions.md#jaiclaw-code) |
| CodeToolsAutoConfiguration | jaiclaw-code | class | Auto-configuration for code tools | [View](dev-guide/extensions.md#jaiclaw-code) |
| CompactionConfig | jaiclaw-core | record | Compaction configuration | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CompactionResult | jaiclaw-core | record | Compaction operation result | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CompactionService | jaiclaw-compaction | class | Compaction orchestrator | [View](dev-guide/extensions.md#jaiclaw-compaction) |
| CompactionServiceAdapter | jaiclaw-compaction | class | Adapts to ContextCompactor SPI | [View](dev-guide/extensions.md#jaiclaw-compaction) |
| CompactionSummarizer | jaiclaw-compaction | class | LLM-based summarization | [View](dev-guide/extensions.md#jaiclaw-compaction) |
| CompositeDocumentParser | jaiclaw-documents | class | Composite document parser | [View](dev-guide/extensions.md#jaiclaw-documents) |
| CompositeMediaAnalyzer | jaiclaw-media | class | Composite media analyzer | [View](dev-guide/extensions.md#jaiclaw-media) |
| CompositeTenantResolver | jaiclaw-gateway | class | Multi-resolver tenant resolution | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| ConfigLocation | jaiclaw-shell | class | Config directory utility | [View](dev-guide/apps.md#jaiclaw-shell) |
| ConfigTemplates | jaiclaw-rest-cli-architect | class | Config file templates | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| ContextCompactor | jaiclaw-core | interface | SPI for context compaction | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CronAgentFactory | jaiclaw-cron-manager (ext) | class | Creates per-execution agent sessions | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronBatchJobFactory | jaiclaw-cron-manager (ext) | class | Spring Batch job factory | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronExecutionRecord | jaiclaw-cron-manager (ext) | record | Execution history record | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronExecutionStore | jaiclaw-cron-manager (ext) | interface | Execution history SPI | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronJob | jaiclaw-core | record | Scheduled cron job definition | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CronJobCommands | jaiclaw-cron-manager-app | class | Shell commands for cron | [View](dev-guide/apps.md#jaiclaw-cron-manager-app) |
| CronJobDefinition | jaiclaw-cron-manager (ext) | record | Extended job definition | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronJobDefinitionStore | jaiclaw-cron-manager (ext) | interface | Job definition SPI | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronJobExecutor | jaiclaw-cron | class | Executes job via agent session | [View](dev-guide/extensions.md#jaiclaw-cron) |
| CronJobManagerService | jaiclaw-cron-manager (ext) | class | Central cron orchestrator | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronJobResult | jaiclaw-core | sealed interface | Cron result: Success or Failure | [View](dev-guide/core-modules.md#jaiclaw-core) |
| CronJobStore | jaiclaw-cron | interface | Cron persistence SPI | [View](dev-guide/extensions.md#jaiclaw-cron) |
| CronJobTasklet | jaiclaw-cron-manager (ext) | class | Spring Batch tasklet | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronManagerApplication | jaiclaw-cron-manager-app | class | Spring Boot entry point | [View](dev-guide/apps.md#jaiclaw-cron-manager-app) |
| CronManagerAutoConfiguration | jaiclaw-cron-manager (ext) | class | Auto-configuration | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronManagerLifecycle | jaiclaw-cron-manager (ext) | class | SmartLifecycle | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronManagerMcpToolProvider | jaiclaw-cron-manager (ext) | class | MCP tool provider for cron | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| CronScheduleComputer | jaiclaw-cron | class | Cron expression parser | [View](dev-guide/extensions.md#jaiclaw-cron) |
| CronService | jaiclaw-cron | class | Job scheduling service | [View](dev-guide/extensions.md#jaiclaw-cron) |
| CredentialStateEvaluator | jaiclaw-identity | class | Token expiry state and eligibility | [View](dev-guide/extensions.md#jaiclaw-identity) |
| CryptoService | jaiclaw-tools-security | class | Cryptographic operations | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| DailyLogAppender | jaiclaw-memory | class | Daily markdown log appender | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| DefaultTelegramHttpClient | jaiclaw-channel-telegram | class | Default JDK HttpClient-based Telegram HTTP transport | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| DefaultTenantContext | jaiclaw-core | record | TenantContext implementation | [View](dev-guide/core-modules.md#jaiclaw-core) |
| DeliveryResult | jaiclaw-channel-api | sealed interface | Delivery: Success or Failure | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| DescribeResourceTool | jaiclaw-tools-k8s | class | K8s resource describer | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| DeviceCodeFlow | jaiclaw-identity | class | Device code flow (RFC 8628) | [View](dev-guide/extensions.md#jaiclaw-identity) |
| DiscordAdapter | jaiclaw-channel-discord | class | Discord channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-discord) |
| DiscordConfig | jaiclaw-channel-discord | record | Discord config | [View](dev-guide/channels.md#jaiclaw-channel-discord) |
| DocStoreAnalyzer | jaiclaw-docstore | interface | SPI for document analysis | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreEntry | jaiclaw-docstore | record | Indexed document entry | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreRepository | jaiclaw-docstore | interface | SPI for DocStore persistence | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreSearchOptions | jaiclaw-docstore | record | Search filter options | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreSearchProvider | jaiclaw-docstore | interface | SPI for DocStore search | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreSearchResult | jaiclaw-docstore | record | Search result | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreService | jaiclaw-docstore | class | Central DocStore orchestrator | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocStoreToolProvider | jaiclaw-docstore | class | DocStore agent tools | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| DocumentIngestionPipeline | jaiclaw-documents | class | Parse, extract, chunk pipeline | [View](dev-guide/extensions.md#jaiclaw-documents) |
| DocumentIngestionResult | jaiclaw-documents | record | Ingestion result | [View](dev-guide/extensions.md#jaiclaw-documents) |
| DocumentParseException | jaiclaw-documents | class | Parse failure exception | [View](dev-guide/extensions.md#jaiclaw-documents) |
| DocumentParser | jaiclaw-documents | interface | SPI for document parsing | [View](dev-guide/extensions.md#jaiclaw-documents) |
| EmailAdapter | jaiclaw-channel-email | class | Email channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-email) |
| EmailConfig | jaiclaw-channel-email | record | Email config | [View](dev-guide/channels.md#jaiclaw-channel-email) |
| EndpointSpec | jaiclaw-rest-cli-architect | record | API endpoint spec | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| EnvFileWriter | jaiclaw-shell | class | .env file generator | [View](dev-guide/apps.md#jaiclaw-shell) |
| EstablishContextTool | jaiclaw-tools-security | class | Handshake context tool | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| ExplicitToolLoop | jaiclaw-agent | class | Tool call loop with approval | [View](dev-guide/core-modules.md#jaiclaw-agent) |
| FileEditTool | jaiclaw-code | class | Surgical string replacement | [View](dev-guide/extensions.md#jaiclaw-code) |
| FileReadTool | jaiclaw-tools | class | File content reader | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| FileWriteTool | jaiclaw-tools | class | File content writer | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| FullTextDocStoreSearch | jaiclaw-docstore | class | Full-text inverted index search | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| GatewayController | jaiclaw-gateway | class | REST controller | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| GatewayHealthIndicator | jaiclaw-gateway | class | Health indicator | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| GatewayLifecycle | jaiclaw-gateway | class | SmartLifecycle | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| FilteredGatewayLifecycle | jaiclaw-gateway | class | Filtered SmartLifecycle | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| GatewayMessageFilter | jaiclaw-gateway | interface | Message filter SPI | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| GatewayMetrics | jaiclaw-gateway | class | Metrics collector | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| GatewayService | jaiclaw-gateway | class | Core gateway service | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| GenerateKeyPairTool | jaiclaw-tools-security | class | Key pair generation | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| GetPodLogsTool | jaiclaw-tools-k8s | class | K8s pod log retriever | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| GetResourceUsageTool | jaiclaw-tools-k8s | class | K8s resource usage | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| GlobTool | jaiclaw-code | class | File pattern matching | [View](dev-guide/extensions.md#jaiclaw-code) |
| GrepTool | jaiclaw-code | class | Content search | [View](dev-guide/extensions.md#jaiclaw-code) |
| H2CronExecutionStore | jaiclaw-cron-manager (ext) | class | H2 execution store | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| H2CronJobDefinitionStore | jaiclaw-cron-manager (ext) | class | H2 job definition store | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| H2CronJobStore | jaiclaw-cron-manager (ext) | class | H2 bridge to CronJobStore | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| H2PersistenceAutoConfiguration | jaiclaw-cron-manager (ext) | class | H2 auto-configuration | [View](dev-guide/extensions.md#jaiclaw-cron-manager) |
| HandshakeHttpClient | jaiclaw-tools-security | class | Remote handshake HTTP client | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HttpProperties | jaiclaw-config | record | HTTP client configuration wrapper | [View](dev-guide/core-modules.md#jaiclaw-config) |
| HttpProxyProperties | jaiclaw-config | record | HTTP proxy configuration | [View](dev-guide/core-modules.md#jaiclaw-config) |
| HandshakeMode | jaiclaw-tools-security | enum | LOCAL, HTTP_CLIENT, ORCHESTRATED | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HandshakeRequest | jaiclaw-tools-security | record | Client handshake request | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HandshakeServerEndpoint | jaiclaw-tools-security | interface | Server-side protocol | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HandshakeSession | jaiclaw-tools-security | class | Active handshake state | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HandshakeSessionStore | jaiclaw-tools-security | class | Session store | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HandshakeTokenFilter | jaiclaw-tools-security | class | Bearer token filter | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| HookHandler | jaiclaw-core | interface | Lifecycle hook handler | [View](dev-guide/core-modules.md#jaiclaw-core) |
| HookName | jaiclaw-core | enum | Lifecycle hook names | [View](dev-guide/core-modules.md#jaiclaw-core) |
| HookRegistration | jaiclaw-core | record | Registered hook with priority | [View](dev-guide/core-modules.md#jaiclaw-core) |
| HookRunner | jaiclaw-plugin-sdk | class | Hook execution engine | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| HookRunnerAdapter | jaiclaw-plugin-sdk | class | Adapts to AgentHookDispatcher | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| HtmlDocumentParser | jaiclaw-documents | class | HTML parser via Jsoup | [View](dev-guide/extensions.md#jaiclaw-documents) |
| HttpMcpToolProvider | jaiclaw-gateway | class | MCP via HTTP | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| HybridDocStoreSearch | jaiclaw-docstore | class | Hybrid RRF search | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| HybridSearchManager | jaiclaw-memory | class | Keyword + temporal decay | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| IdentifierPreserver | jaiclaw-compaction | class | Identifier extraction guard | [View](dev-guide/extensions.md#jaiclaw-compaction) |
| IdentityLink | jaiclaw-core | record | Platform-to-canonical user link | [View](dev-guide/core-modules.md#jaiclaw-core) |
| IdentityLinkService | jaiclaw-identity | class | Identity link management | [View](dev-guide/extensions.md#jaiclaw-identity) |
| IdentityLinkStore | jaiclaw-identity | class | JSON file persistence | [View](dev-guide/extensions.md#jaiclaw-identity) |
| IdentityProperties | jaiclaw-config | record | Agent identity config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| IdentityResolver | jaiclaw-identity | class | Canonical user resolution | [View](dev-guide/extensions.md#jaiclaw-identity) |
| InMemoryArtifactStore | jaiclaw-core | class | ConcurrentHashMap-based ArtifactStore | [View](dev-guide/core-modules.md#jaiclaw-core) |
| InMemoryAuditLogger | jaiclaw-audit | class | Bounded in-memory logger | [View](dev-guide/extensions.md#jaiclaw-audit) |
| InMemoryDocStoreRepository | jaiclaw-docstore | class | In-memory DocStore | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| InMemorySearchManager | jaiclaw-memory | class | Keyword-based search | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| JavaTemplates | jaiclaw-rest-cli-architect | class | Java source templates | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| JaiClawAgent | jaiclaw-agent | class | Embabel-based agent | [View](dev-guide/core-modules.md#jaiclaw-agent) |
| JaiClawAutoConfiguration | jaiclaw-spring-boot-starter | class | Phase 2 auto-config | [View](dev-guide/starter.md) |
| JaiClawChannelAutoConfiguration | jaiclaw-spring-boot-starter | class | Phase 4 auto-config | [View](dev-guide/starter.md) |
| JaiClawGatewayApplication | jaiclaw-gateway-app | class | Gateway entry point | [View](dev-guide/apps.md#jaiclaw-gateway-app) |
| JaiClawGatewayAutoConfiguration | jaiclaw-spring-boot-starter | class | Phase 3 auto-config | [View](dev-guide/starter.md) |
| JaiClawPlugin | jaiclaw-plugin-sdk | interface | SPI for plugins | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| JaiClawProperties | jaiclaw-config | record | Root configuration properties | [View](dev-guide/core-modules.md#jaiclaw-config) |
| JaiClawSecurityAutoConfiguration | jaiclaw-security | class | Security auto-config | [View](dev-guide/core-modules.md#jaiclaw-security) |
| JaiClawSecurityProperties | jaiclaw-security | record | Security config properties | [View](dev-guide/core-modules.md#jaiclaw-security) |
| JaiClawShellApplication | jaiclaw-shell | class | Shell entry point | [View](dev-guide/apps.md#jaiclaw-shell) |
| JRestCliApplication | jaiclaw-rest-cli-architect | class | REST CLI entry point | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| JRestCliCommands | jaiclaw-rest-cli-architect | class | Shell commands | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| JsonFileCronJobStore | jaiclaw-cron | class | JSON persistence for cron | [View](dev-guide/extensions.md#jaiclaw-cron) |
| JsonFileDocStoreRepository | jaiclaw-docstore | class | JSON file DocStore | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| JsonFileSubscriptionRepository | jaiclaw-subscription | class | JSON subscription store | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| JsonRpcClient | jaiclaw-channel-api | class | JSON-RPC 2.0 client | [View](dev-guide/core-modules.md#jaiclaw-channel-api) |
| JwtAuthenticationFilter | jaiclaw-security | class | JWT auth filter | [View](dev-guide/core-modules.md#jaiclaw-security) |
| JwtTenantResolver | jaiclaw-gateway | class | JWT-based tenant resolution | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| JwtTokenValidator | jaiclaw-security | class | JWT token validation | [View](dev-guide/core-modules.md#jaiclaw-security) |
| KeyExchangeResult | jaiclaw-tools-security | record | Key exchange result | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| KubectlExecTool | jaiclaw-tools-k8s | class | kubectl execution | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| KubernetesClientProvider | jaiclaw-tools-k8s | class | K8s client factory | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| KubernetesTools | jaiclaw-tools-k8s | class | K8s tools factory | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| KubernetesToolsAutoConfiguration | jaiclaw-tools-k8s | class | K8s auto-config | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| ListDeploymentsTool | jaiclaw-tools-k8s | class | K8s deployments lister | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| ListEventsTool | jaiclaw-tools-k8s | class | K8s events lister | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| ListNamespacesTool | jaiclaw-tools-k8s | class | K8s namespaces lister | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| ListNodesTool | jaiclaw-tools-k8s | class | K8s nodes lister | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| ListPodsTool | jaiclaw-tools-k8s | class | K8s pods lister | [View](dev-guide/extensions.md#jaiclaw-tools-k8s) |
| LlmDocStoreAnalyzer | jaiclaw-docstore | class | LLM-powered analyzer | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| LoggingAttachmentRouter | jaiclaw-gateway | class | Default no-op attachment router | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| McpController | jaiclaw-gateway | class | MCP REST controller | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| McpServerConfigBootstrap | jaiclaw-gateway | class | Config-driven MCP bootstrap | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| McpServerProperties | jaiclaw-config | record | MCP server config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| McpServerRegistry | jaiclaw-gateway | class | MCP provider registry | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| McpToolDefinition | jaiclaw-core | record | MCP tool definition | [View](dev-guide/core-modules.md#jaiclaw-core) |
| McpToolProvider | jaiclaw-core | interface | SPI for MCP tools | [View](dev-guide/core-modules.md#jaiclaw-core) |
| McpToolResult | jaiclaw-core | record | MCP tool result | [View](dev-guide/core-modules.md#jaiclaw-core) |
| McpTransportFactory | jaiclaw-gateway | class | MCP transport factory | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| MediaAnalysisProvider | jaiclaw-media | interface | SPI for media analysis | [View](dev-guide/extensions.md#jaiclaw-media) |
| MediaAnalysisResult | jaiclaw-media | record | Analysis result | [View](dev-guide/extensions.md#jaiclaw-media) |
| MediaInput | jaiclaw-media | record | Media analysis input | [View](dev-guide/extensions.md#jaiclaw-media) |
| MemoryProperties | jaiclaw-config | record | Memory backend config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| MemoryProvider | jaiclaw-core | interface | SPI for workspace memory | [View](dev-guide/core-modules.md#jaiclaw-core) |
| MemorySaveTool | jaiclaw-memory | class | Agent memory save tool | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| MemorySearchManager | jaiclaw-memory | interface | SPI for semantic search | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| MemorySearchOptions | jaiclaw-memory | record | Search options | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| MemorySearchResult | jaiclaw-memory | record | Search result | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| MemorySource | jaiclaw-memory | enum | MEMORY, SESSIONS, WORKSPACE, etc. | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| MentionParser | jaiclaw-gateway | class | @mention extraction | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| Message | jaiclaw-core | sealed interface | All message types | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ModelsProperties | jaiclaw-config | record | Model provider config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| MutualBootstrapValidator | jaiclaw-tools-security | class | Mutual TLS validator | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| NegotiateSessionTool | jaiclaw-tools-security | class | Session negotiation | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| NoOpOrchestrationPort | jaiclaw-tools | class | No-op orchestration | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| OAuthCallbackServer | jaiclaw-identity | class | Loopback HTTP server for OAuth redirects | [View](dev-guide/extensions.md#jaiclaw-identity) |
| OAuthFlowException | jaiclaw-identity | class | Thrown when an OAuth flow fails | [View](dev-guide/extensions.md#jaiclaw-identity) |
| OAuthFlowManager | jaiclaw-identity | class | Orchestrates OAuth flows + credential storage | [View](dev-guide/extensions.md#jaiclaw-identity) |
| OAuthFlowResult | jaiclaw-identity | record | Access/refresh tokens, email, expiry | [View](dev-guide/extensions.md#jaiclaw-identity) |
| OAuthFlowType | jaiclaw-identity | enum | AUTHORIZATION_CODE, DEVICE_CODE | [View](dev-guide/extensions.md#jaiclaw-identity) |
| OAuthProviderConfig | jaiclaw-identity | record | Provider endpoints, client credentials | [View](dev-guide/extensions.md#jaiclaw-identity) |
| OnboardCommands | jaiclaw-shell | class | Onboard shell command | [View](dev-guide/apps.md#jaiclaw-shell) |
| OnboardConfig | jaiclaw-shell | class | Onboard RestTemplate config | [View](dev-guide/apps.md#jaiclaw-shell) |
| OnboardResult | jaiclaw-shell | class | Onboard wizard result | [View](dev-guide/apps.md#jaiclaw-shell) |
| OnboardWizardOrchestrator | jaiclaw-shell | class | Onboard wizard orchestrator | [View](dev-guide/apps.md#jaiclaw-shell) |
| OpenAiSttProvider | jaiclaw-voice | class | OpenAI Whisper STT | [View](dev-guide/extensions.md#jaiclaw-voice) |
| OpenAiTtsProvider | jaiclaw-voice | class | OpenAI TTS | [View](dev-guide/extensions.md#jaiclaw-voice) |
| OpenApiParser | jaiclaw-rest-cli-architect | class | OpenAPI spec parser | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| OrchestrationResult | jaiclaw-tools | record | Orchestration result | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| PageSnapshot | jaiclaw-browser | record | Page accessibility tree | [View](dev-guide/extensions.md#jaiclaw-browser) |
| ParsedDocument | jaiclaw-documents | record | Document parse result | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PaymentEvent | jaiclaw-subscription | record | Payment provider event | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| PaymentEventType | jaiclaw-subscription | enum | Payment event types | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| PaymentProvider | jaiclaw-subscription | interface | SPI for payment providers | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| PaymentVerification | jaiclaw-subscription | record | Payment verification result | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| PayPalPaymentProvider | jaiclaw-subscription | class | PayPal integration | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| PdfDocumentParser | jaiclaw-documents | class | PDF parser via PDFBox | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PdfFormField | jaiclaw-documents | record | PDF form field: name, type, value, options | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PdfFormFiller | jaiclaw-documents | class | Fills AcroForm fields from a map | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PdfFormReader | jaiclaw-documents | class | Reads AcroForm fields from PDF template | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PdfFormResult | jaiclaw-documents | sealed interface | Success(pdfBytes, fieldsSet) / Failure(reason) | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PerplexityApiException | jaiclaw-perplexity | class | API error exception | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| PerplexityApplication | jaiclaw-perplexity | class | Perplexity entry point | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| PerplexityAutoConfiguration | jaiclaw-perplexity | class | Perplexity auto-config | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| PerplexityClient | jaiclaw-perplexity | class | Perplexity HTTP client | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| PerplexityCommands | jaiclaw-perplexity | class | Shell commands | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| PerplexityProperties | jaiclaw-perplexity | record | Perplexity config | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| PkceGenerator | jaiclaw-identity | class | PKCE verifier + S256 challenge generation | [View](dev-guide/extensions.md#jaiclaw-identity) |
| PlainTextDocumentParser | jaiclaw-documents | class | Plain text passthrough | [View](dev-guide/extensions.md#jaiclaw-documents) |
| PluginApi | jaiclaw-plugin-sdk | interface | API surface for plugins | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| PluginApiImpl | jaiclaw-plugin-sdk | class | Default PluginApi | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| ProxyAwareHttpClientFactory | jaiclaw-core | class | Proxy-aware HttpClient factory | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ProxyConfig | jaiclaw-core | record | Proxy configuration (inner record) | [View](dev-guide/core-modules.md#jaiclaw-core) |
| PluginDefinition | jaiclaw-core | record | Plugin metadata | [View](dev-guide/core-modules.md#jaiclaw-core) |
| PluginDiscovery | jaiclaw-plugin-sdk | class | Multi-source discovery | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| PluginKind | jaiclaw-core | enum | Plugin classification | [View](dev-guide/core-modules.md#jaiclaw-core) |
| PluginLifecycle | jaiclaw-core | interface | Plugin lifecycle SPI | [View](dev-guide/core-modules.md#jaiclaw-core) |
| PluginOrigin | jaiclaw-plugin-sdk | enum | BUNDLED, CLASSPATH, WORKSPACE | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| PluginRecord | jaiclaw-plugin-sdk | record | Plugin metadata record | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| PluginRegistry | jaiclaw-plugin-sdk | class | Plugin registration aggregator | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| PluginsProperties | jaiclaw-config | record | Plugin config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| PluginStatus | jaiclaw-plugin-sdk | enum | LOADED, DISABLED, ERROR | [View](dev-guide/core-modules.md#jaiclaw-plugin-sdk) |
| PomTemplates | jaiclaw-rest-cli-architect | class | pom.xml templates | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| ProjectGenerator | jaiclaw-rest-cli-architect | class | Project file generator | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| ProjectMode | jaiclaw-rest-cli-architect | enum | Project structure modes | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| ProjectSpec | jaiclaw-rest-cli-architect | record | Full project specification | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| PromptAnalyzerApplication | jaiclaw-prompt-analyzer | class | Standalone Spring Boot entry point | [View](dev-guide/cli-tools.md#jaiclaw-prompt-analyzer) |
| PromptAnalyzerAutoConfiguration | jaiclaw-prompt-analyzer | class | Auto-config registering prompt_analyze tool | [View](dev-guide/cli-tools.md#jaiclaw-prompt-analyzer) |
| PromptAnalyzerCommands | jaiclaw-prompt-analyzer | class | Shell commands (prompt-analyze, prompt-check) | [View](dev-guide/cli-tools.md#jaiclaw-prompt-analyzer) |
| ProjectScanner | jaiclaw-prompt-analyzer | class | Core analysis engine for token estimation | [View](dev-guide/cli-tools.md#jaiclaw-prompt-analyzer) |
| ProviderTokenRefresherRegistry | jaiclaw-identity | class | Registry of per-provider token refreshers | [View](dev-guide/extensions.md#jaiclaw-identity) |
| RateLimitFilter | jaiclaw-security | class | Per-sender rate limiting | [View](dev-guide/core-modules.md#jaiclaw-security) |
| RemoteEnvironmentDetector | jaiclaw-identity | class | Detects SSH/headless/Codespaces | [View](dev-guide/extensions.md#jaiclaw-identity) |
| RoleToolProfileResolver | jaiclaw-security | class | JWT role to ToolProfile | [View](dev-guide/core-modules.md#jaiclaw-security) |
| RoutingBinding | jaiclaw-core | record | Channel/peer to agent binding | [View](dev-guide/core-modules.md#jaiclaw-core) |
| RoutingService | jaiclaw-gateway | class | Message routing | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| SearchApiRequest | jaiclaw-perplexity | record | Raw web search request | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| SearchApiResponse | jaiclaw-perplexity | record | Raw web search response | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| SearchResult | jaiclaw-perplexity | record | Single search result | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| SecretRefResolver | jaiclaw-identity | class | Resolves SecretRef (env, file, exec) | [View](dev-guide/extensions.md#jaiclaw-identity) |
| SecurityHandshakeAgent | jaiclaw-tools-security | class | Handshake agent facade | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SecurityHandshakeAutoConfiguration | jaiclaw-tools-security | class | Handshake auto-config | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SecurityHandshakeMcpProvider | jaiclaw-tools-security | class | MCP handshake provider | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SecurityHandshakeProperties | jaiclaw-tools-security | record | Handshake config | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SecurityHandshakeTool | jaiclaw-tools-security | class | Complete handshake orchestrator | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SecurityModeLogger | jaiclaw-security | class | Logs security mode | [View](dev-guide/core-modules.md#jaiclaw-security) |
| SecurityTools | jaiclaw-tools-security | class | Security tools factory | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SecurityToolsAutoConfiguration | jaiclaw-tools-security | class | Security tools auto-config | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SendTelegramTool | jaiclaw-channel-telegram | class | Reusable tool for sending messages to a Telegram chat ID | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| ServerHello | jaiclaw-tools-security | record | Server hello response | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| Session | jaiclaw-core | record | Conversation session | [View](dev-guide/core-modules.md#jaiclaw-core) |
| SessionAuthProfileResolver | jaiclaw-identity | class | Round-robin rotation, cooldown, user-pin | [View](dev-guide/extensions.md#jaiclaw-identity) |
| SessionEstablished | jaiclaw-tools-security | record | Session confirmation | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| SessionManager | jaiclaw-agent | class | In-memory session manager | [View](dev-guide/core-modules.md#jaiclaw-agent) |
| SessionProperties | jaiclaw-config | record | Session config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| SessionState | jaiclaw-core | enum | ACTIVE, IDLE, COMPACTING, CLOSED | [View](dev-guide/core-modules.md#jaiclaw-core) |
| SessionTranscriptStore | jaiclaw-memory | class | JSONL transcript persistence | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| ShellExecTool | jaiclaw-tools | class | Shell command execution | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| SignalAdapter | jaiclaw-channel-signal | class | Signal channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-signal) |
| SignalConfig | jaiclaw-channel-signal | record | Signal config | [View](dev-guide/channels.md#jaiclaw-channel-signal) |
| SignalMode | jaiclaw-channel-signal | enum | EMBEDDED, HTTP_CLIENT | [View](dev-guide/channels.md#jaiclaw-channel-signal) |
| SkillCreatorApplication | jaiclaw-skill-creator | class | Skill Creator entry point | [View](dev-guide/cli-tools.md#jaiclaw-skill-creator) |
| SkillCreatorCommands | jaiclaw-skill-creator | class | Shell commands | [View](dev-guide/cli-tools.md#jaiclaw-skill-creator) |
| SkillDefinition | jaiclaw-core | record | Skill definition | [View](dev-guide/core-modules.md#jaiclaw-core) |
| SkillEligibilityChecker | jaiclaw-skills | class | Platform eligibility | [View](dev-guide/core-modules.md#jaiclaw-skills) |
| SkillLoader | jaiclaw-skills | class | Loads skills from classpath/workspace | [View](dev-guide/core-modules.md#jaiclaw-skills) |
| SkillMarkdownParser | jaiclaw-skills | class | SKILL.md parser | [View](dev-guide/core-modules.md#jaiclaw-skills) |
| SkillMetadata | jaiclaw-core | record | Skill metadata | [View](dev-guide/core-modules.md#jaiclaw-core) |
| SkillPromptBuilder | jaiclaw-skills | class | Skill prompt builder | [View](dev-guide/core-modules.md#jaiclaw-skills) |
| SkillsProperties | jaiclaw-config | record | Skills config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| SkillSpec | jaiclaw-skill-creator | record | Parsed skill spec | [View](dev-guide/cli-tools.md#jaiclaw-skill-creator) |
| SlackAdapter | jaiclaw-channel-slack | class | Slack channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-slack) |
| SlackConfig | jaiclaw-channel-slack | record | Slack config | [View](dev-guide/channels.md#jaiclaw-channel-slack) |
| SmsAdapter | jaiclaw-channel-sms | class | SMS channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-sms) |
| SmsConfig | jaiclaw-channel-sms | record | SMS config | [View](dev-guide/channels.md#jaiclaw-channel-sms) |
| SonarRequest | jaiclaw-perplexity | record | Sonar API request | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| SonarResponse | jaiclaw-perplexity | record | Sonar API response | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| SpecValidator | jaiclaw-rest-cli-architect | class | Project spec validator | [View](dev-guide/cli-tools.md#jaiclaw-rest-cli-architect) |
| SpringAiToolBridge | jaiclaw-tools | class | JaiClaw to Spring AI bridge | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| StoredArtifact | jaiclaw-core | record | Binary artifact with status and metadata | [View](dev-guide/core-modules.md#jaiclaw-core) |
| SsrfGuard | jaiclaw-tools | class | SSRF protection — blocks requests to private/internal IPs | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| SseMcpToolProvider | jaiclaw-gateway | class | MCP via SSE | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| StandaloneCronManagerConfiguration | jaiclaw-cron-manager-app | class | Standalone MCP hosting config | [View](dev-guide/apps.md#jaiclaw-cron-manager-app) |
| StatusCommands | jaiclaw-shell | class | Status shell commands | [View](dev-guide/apps.md#jaiclaw-shell) |
| StdioMcpToolProvider | jaiclaw-gateway | class | MCP via stdio | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| SttProvider | jaiclaw-voice | interface | SPI for speech-to-text | [View](dev-guide/extensions.md#jaiclaw-voice) |
| StripePaymentProvider | jaiclaw-subscription | class | Stripe integration | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| Subscription | jaiclaw-subscription | record | User subscription | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SubscriptionExpiryScheduler | jaiclaw-subscription | class | Expiry checker | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SubscriptionLifecycleListener | jaiclaw-subscription | interface | Lifecycle SPI | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SubscriptionPlan | jaiclaw-subscription | record | Plan with pricing | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SubscriptionRepository | jaiclaw-subscription | interface | Persistence SPI | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SubscriptionService | jaiclaw-subscription | class | Central service | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SubscriptionStatus | jaiclaw-subscription | enum | ACTIVE, PAST_DUE, etc. | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| SystemMessage | jaiclaw-core | record | System message | [View](dev-guide/core-modules.md#jaiclaw-core) |
| SystemPromptBuilder | jaiclaw-agent | class | System prompt construction | [View](dev-guide/core-modules.md#jaiclaw-agent) |
| TeamsAdapter | jaiclaw-channel-teams | class | Teams channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-teams) |
| TeamsConfig | jaiclaw-channel-teams | record | Teams config | [View](dev-guide/channels.md#jaiclaw-channel-teams) |
| TeamsJwtValidator | jaiclaw-channel-teams | class | Bot Framework JWT validator | [View](dev-guide/channels.md#jaiclaw-channel-teams) |
| TeamsTokenManager | jaiclaw-channel-teams | class | Azure AD token manager | [View](dev-guide/channels.md#jaiclaw-channel-teams) |
| TelegramAccessController | jaiclaw-subscription-telegram | class | Telegram access control | [View](dev-guide/extensions.md#jaiclaw-subscription-telegram) |
| TelegramAdapter | jaiclaw-channel-telegram | class | Telegram channel adapter | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| TelegramConfig | jaiclaw-channel-telegram | record | Telegram config | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| TelegramHttpClient | jaiclaw-channel-telegram | interface | Pluggable HTTP transport for Telegram Bot API calls | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| TelegramDocStorePlugin | jaiclaw-docstore-telegram | class | Telegram DocStore plugin | [View](dev-guide/extensions.md#jaiclaw-docstore-telegram) |
| TelegramGroupManager | jaiclaw-subscription-telegram | class | Telegram admin operations | [View](dev-guide/extensions.md#jaiclaw-subscription-telegram) |
| TelegramPollingStrategy | jaiclaw-channel-telegram | interface | Pluggable polling strategy for Telegram getUpdates replacement | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| TelegramPaymentProvider | jaiclaw-subscription | class | Telegram Payments | [View](dev-guide/extensions.md#jaiclaw-subscription) |
| TelegramSubscriptionPlugin | jaiclaw-subscription-telegram | class | Telegram subscription plugin | [View](dev-guide/extensions.md#jaiclaw-subscription-telegram) |
| TelegramUserIdFilter | jaiclaw-channel-telegram | class | Gateway filter for Telegram user authorization and rate limiting | [View](dev-guide/channels.md#jaiclaw-channel-telegram) |
| TenantContext | jaiclaw-core | interface | Multi-tenant context | [View](dev-guide/core-modules.md#jaiclaw-core) |
| TenantContextHolder | jaiclaw-core | class | ThreadLocal tenant holder | [View](dev-guide/core-modules.md#jaiclaw-core) |
| TenantResolver | jaiclaw-gateway | interface | SPI for tenant resolution | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| TenantSkillRegistry | jaiclaw-skills | class | Tenant-aware skill registry | [View](dev-guide/core-modules.md#jaiclaw-skills) |
| TerminalImageRenderer | jaiclaw-perplexity | class | Terminal image rendering | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| TextChunker | jaiclaw-documents | class | Text chunking utility | [View](dev-guide/extensions.md#jaiclaw-documents) |
| TokenEstimator | jaiclaw-compaction | class | Token count estimation | [View](dev-guide/extensions.md#jaiclaw-compaction) |
| TokenUsage | jaiclaw-core | record | Token usage metrics | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolApprovalDecision | jaiclaw-core | sealed interface | Approved, Denied, Modified | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolApprovalHandler | jaiclaw-core | interface | Human-in-the-loop SPI | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolCallback | jaiclaw-core | interface | SPI for implementing tools | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolCallEvent | jaiclaw-core | record | Tool call lifecycle event | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolCatalog | jaiclaw-tools | class | Tool catalog constants | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| ToolContext | jaiclaw-core | record | Runtime tool context | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolDefinition | jaiclaw-core | record | Tool metadata for LLM | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolLoopConfig | jaiclaw-core | record | Tool loop configuration | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolLoopProperties | jaiclaw-config | record | Tool loop config properties | [View](dev-guide/core-modules.md#jaiclaw-config) |
| ToolProfile | jaiclaw-core | enum | MINIMAL, CODING, MESSAGING, FULL | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolProfileHolder | jaiclaw-core | class | ThreadLocal ToolProfile | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolRegistry | jaiclaw-tools | class | Central tool registry | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| ToolResult | jaiclaw-core | sealed interface | Success or Error | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolResultMessage | jaiclaw-core | record | Tool result message | [View](dev-guide/core-modules.md#jaiclaw-core) |
| ToolsProperties | jaiclaw-config | record | Tool visibility, SSRF protection, workspace boundary config | [View](dev-guide/core-modules.md#jaiclaw-config) |
| TranscriptionResult | jaiclaw-core | record | STT transcription result | [View](dev-guide/core-modules.md#jaiclaw-core) |
| TtsDirectiveParser | jaiclaw-voice | class | TTS directive parser | [View](dev-guide/extensions.md#jaiclaw-voice) |
| TtsProvider | jaiclaw-voice | interface | SPI for text-to-speech | [View](dev-guide/extensions.md#jaiclaw-voice) |
| Usage | jaiclaw-perplexity | record | Token usage metrics | [View](dev-guide/cli-tools.md#jaiclaw-perplexity) |
| UserMessage | jaiclaw-core | record | User-initiated message | [View](dev-guide/core-modules.md#jaiclaw-core) |
| VectorDocStoreSearch | jaiclaw-docstore | class | Semantic vector search | [View](dev-guide/extensions.md#jaiclaw-docstore) |
| VectorStoreSearchManager | jaiclaw-memory | class | VectorStore-backed search | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| VerifyIdentityTool | jaiclaw-tools-security | class | Identity verification | [View](dev-guide/extensions.md#jaiclaw-tools-security) |
| VoiceConfig | jaiclaw-voice | record | Voice service config | [View](dev-guide/extensions.md#jaiclaw-voice) |
| VoiceService | jaiclaw-voice | class | TTS/STT orchestrator | [View](dev-guide/extensions.md#jaiclaw-voice) |
| WebFetchTool | jaiclaw-tools | class | URL content fetcher with optional SSRF protection | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| WebhookDispatcher | jaiclaw-gateway | class | Webhook dispatcher | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| WebSearchTool | jaiclaw-tools | class | DuckDuckGo web search | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| WebSocketSessionHandler | jaiclaw-gateway | class | WebSocket handler | [View](dev-guide/core-modules.md#jaiclaw-gateway) |
| WizardStep | jaiclaw-shell | interface | Onboard wizard step | [View](dev-guide/apps.md#jaiclaw-shell) |
| WorkflowDescriptor | jaiclaw-tools | record | Orchestration workflow | [View](dev-guide/core-modules.md#jaiclaw-tools) |
| WorkspaceMemoryManager | jaiclaw-memory | class | MEMORY.md manager | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| WorkspaceMemoryProvider | jaiclaw-memory | class | MemoryProvider adapter | [View](dev-guide/core-modules.md#jaiclaw-memory) |
| YamlConfigWriter | jaiclaw-shell | class | YAML config generator | [View](dev-guide/apps.md#jaiclaw-shell) |

---

## Module Quick Reference

### Core (10 modules) — [Full Reference](dev-guide/core-modules.md)

| Module | Purpose |
|---|---|
| jaiclaw-core | Pure Java domain model — records, sealed interfaces, enums |
| jaiclaw-channel-api | Channel adapter SPI, ChannelMessage, CLI process bridge |
| jaiclaw-config | @ConfigurationProperties records for all config |
| jaiclaw-tools | ToolRegistry, built-in tools, Spring AI bridge |
| jaiclaw-agent | AgentRuntime, SessionManager, SystemPromptBuilder |
| jaiclaw-skills | SkillLoader, SKILL.md parser, tenant-aware registry |
| jaiclaw-plugin-sdk | Plugin SPI, multi-source discovery, hook execution |
| jaiclaw-memory | Semantic search (keyword, vector, hybrid), workspace memory |
| jaiclaw-security | JWT auth, API key auth, rate limiting, 3 security modes |
| jaiclaw-gateway | REST API, WebSocket, webhooks, MCP hosting, observability |

### Channels (7 modules) — [Full Reference](dev-guide/channels.md)

| Module | Purpose |
|---|---|
| jaiclaw-channel-telegram | Telegram Bot API (polling + webhook) |
| jaiclaw-channel-slack | Slack (Socket Mode + Events API) |
| jaiclaw-channel-discord | Discord (Gateway WebSocket + Interactions) |
| jaiclaw-channel-email | Email (IMAP polling + SMTP) |
| jaiclaw-channel-sms | SMS (Twilio REST + webhook) |
| jaiclaw-channel-signal | Signal (embedded daemon + REST sidecar) |
| jaiclaw-channel-teams | Microsoft Teams (Bot Framework) |

### Extensions (19 modules) — [Full Reference](dev-guide/extensions.md)

| Module | Purpose |
|---|---|
| jaiclaw-documents | PDF/HTML/text parsing, chunking pipeline |
| jaiclaw-media | Async media analysis SPI |
| jaiclaw-audit | Audit logging SPI |
| jaiclaw-compaction | Context window compaction |
| jaiclaw-browser | Playwright browser automation |
| jaiclaw-code | File editing, grep, glob tools |
| jaiclaw-cron | Cron job scheduling |
| jaiclaw-cron-manager | Cron job management, H2 persistence, MCP tools, Spring Batch |
| jaiclaw-voice | TTS/STT SPI with OpenAI |
| jaiclaw-identity | Cross-channel identity, OAuth flows, credential management |
| jaiclaw-canvas | A2UI artifact rendering |
| jaiclaw-docstore | Document store with search + analysis |
| jaiclaw-docstore-telegram | Telegram DocStore integration |
| jaiclaw-subscription | Subscription management |
| jaiclaw-subscription-telegram | Telegram subscription access |
| jaiclaw-tools-k8s | Kubernetes tools (Fabric8) |
| jaiclaw-tools-security | Security handshake protocol |
| jaiclaw-calendar | Calendar event CRUD, scheduling, MCP hosting |
| jaiclaw-messaging | MCP server: channel messaging tools (send, broadcast, sessions, agent chat) |

### CLI Tools (4 modules) — [Full Reference](dev-guide/cli-tools.md)

| Module | Purpose |
|---|---|
| jaiclaw-perplexity | Perplexity AI search + research |
| jaiclaw-rest-cli-architect | REST CLI project scaffolding |
| jaiclaw-skill-creator | Interactive skill creator |
| jaiclaw-prompt-analyzer | Prompt token usage analyzer + CI check |

### Apps (3 modules) — [Full Reference](dev-guide/apps.md)

| Module | Purpose |
|---|---|
| jaiclaw-gateway-app | Standalone gateway server |
| jaiclaw-shell | Spring Shell CLI with onboarding wizard |
| jaiclaw-cron-manager-app | Cron job manager app (thin launcher for jaiclaw-cron-manager extension) |

### Auto-Configuration — [Full Reference](dev-guide/starter.md)

4-phase auto-configuration: Spring AI providers → Core beans → Gateway → Channels

---

## Operations Quick Start

```bash
# Prerequisites
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Build
./mvnw compile                                    # compile all
./mvnw test -o                                    # test all (offline)
./mvnw test -pl :jaiclaw-tools -o                   # test one module
./mvnw install -DskipTests                        # install to local repo

# Run
./start.sh                                        # gateway (local)
./start.sh shell                                  # CLI (local Java)
./start.sh cli                                    # CLI (Docker)
./start.sh docker                                 # gateway (Docker)

# Docker images
./mvnw package k8s:build -pl :jaiclaw-gateway-app,:jaiclaw-shell -am -Pk8s -DskipTests
```

See [dev-guide/operations.md](dev-guide/operations.md) for the complete guide covering all environments, security, LLM providers, channels, and troubleshooting.

---

## Design Patterns

| Pattern | Description | Details |
|---|---|---|
| Sealed Interfaces | Type-safe algebraic data types | [View](dev-guide/design-patterns.md#sealed-interfaces) |
| Java Records | Immutable value types everywhere | [View](dev-guide/design-patterns.md#java-records-for-value-types) |
| SPI Pattern | Interface → default impl → pluggable override | [View](dev-guide/design-patterns.md#spi-pattern) |
| ThreadLocal Context | TenantContext, SecurityContext on ThreadLocal | [View](dev-guide/design-patterns.md#threadlocal-context-holders) |
| Tool Registration | ToolCallback → ToolRegistry → SpringAiToolBridge | [View](dev-guide/design-patterns.md#tool-registration--bridging) |
| Plugin Discovery | Spring scan + ServiceLoader + explicit | [View](dev-guide/design-patterns.md#plugin-discovery--hooks) |
| Multi-Tenancy | JWT/bot-token tenant resolution, per-tenant isolation | [View](dev-guide/design-patterns.md#multi-tenancy-pattern) |
| Channel Dual-Mode | Polling/WebSocket (dev) vs webhook (production) | [View](dev-guide/design-patterns.md#channel-adapter-dual-mode) |
| Session Keys | `{agentId}:{channel}:{accountId}:{peerId}` | [View](dev-guide/design-patterns.md#session-key-convention) |
| Auto-Config Ordering | 4-phase @AutoConfiguration with @AutoConfigureAfter | [View](dev-guide/design-patterns.md#auto-configuration-ordering) |
| Dual-Mode CLI Build | Library JAR + standalone fat JAR | [View](dev-guide/design-patterns.md#dual-mode-cli-build) |
| Composable App Assembly | Extension + starter + thin app, embeddable into gateway | [View](dev-guide/design-patterns.md#composable-app-assembly) |
| Mock HTTP Server Integration Testing | Local mock server for outbound HTTP integration tests | [View](dev-guide/design-patterns.md#mock-http-server-integration-testing) |

See [dev-guide/design-patterns.md](dev-guide/design-patterns.md) for the full patterns reference.
