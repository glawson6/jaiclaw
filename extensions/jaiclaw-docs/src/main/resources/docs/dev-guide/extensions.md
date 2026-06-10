# JaiClaw Extension Modules Reference

[Back to Developer Guide](../JAICLAW-DEVELOPER-GUIDE.md)

---

## Table of Contents

1. [jaiclaw-documents](#jaiclaw-documents)
2. [jaiclaw-media](#jaiclaw-media)
3. [jaiclaw-audit](#jaiclaw-audit)
4. [jaiclaw-compaction](#jaiclaw-compaction)
5. [jaiclaw-browser](#jaiclaw-browser)
6. [jaiclaw-code](#jaiclaw-code)
7. [jaiclaw-cron](#jaiclaw-cron)
8. [jaiclaw-cron-manager](#jaiclaw-cron-manager)
9. [jaiclaw-voice](#jaiclaw-voice)
10. [jaiclaw-identity](#jaiclaw-identity)
11. [jaiclaw-canvas](#jaiclaw-canvas)
12. [jaiclaw-docstore](#jaiclaw-docstore)
13. [jaiclaw-docstore-telegram](#jaiclaw-docstore-telegram)
14. [jaiclaw-subscription](#jaiclaw-subscription)
15. [jaiclaw-subscription-telegram](#jaiclaw-subscription-telegram)
16. [jaiclaw-tools-k8s](#jaiclaw-tools-k8s)
17. [jaiclaw-tools-security](#jaiclaw-tools-security)
18. [jaiclaw-calendar](#jaiclaw-calendar)
19. [jaiclaw-messaging](#jaiclaw-messaging)

---

## jaiclaw-documents

**Purpose**: PDF/HTML/text parsing and chunking pipeline for document ingestion into memory or vector stores.

**Package**: `io.jaiclaw.documents`
**Directory**: `extensions/jaiclaw-documents`
**Dependencies**: `jaiclaw-core`, Apache PDFBox, Jsoup

### Class Reference

| Class | Type | Description |
|---|---|---|
| DocumentParser | interface | SPI for extracting text from documents |
| ParsedDocument | record | Parse result with text and metadata |
| DocumentParseException | class | Thrown when parsing fails |
| PdfDocumentParser | class | PDF parsing via Apache PDFBox |
| HtmlDocumentParser | class | HTML parsing via Jsoup |
| PlainTextDocumentParser | class | Passthrough for plain text |
| CompositeDocumentParser | class | Delegates to first supporting parser |
| ChunkingStrategy | record | Strategy for splitting text (inner enum Mode) |
| DocumentIngestionResult | record | Ingestion result with text, chunks, metadata |
| DocumentIngestionPipeline | class | End-to-end: parse → extract → chunk |
| TextChunker | class | Splits text based on ChunkingStrategy |

### Class Relationships

```
DocumentParser (SPI)
  ├── PdfDocumentParser (PDFBox)
  ├── HtmlDocumentParser (Jsoup)
  ├── PlainTextDocumentParser
  └── CompositeDocumentParser (delegates to first match)

DocumentIngestionPipeline
  ├── CompositeDocumentParser → ParsedDocument
  └── TextChunker ← ChunkingStrategy
        └── DocumentIngestionResult
```

---

## jaiclaw-media

**Purpose**: Async media analysis SPI for vision LLMs and audio services with composite delegation.

**Package**: `io.jaiclaw.media`
**Directory**: `extensions/jaiclaw-media`
**Dependencies**: `jaiclaw-core`

### Class Reference

| Class | Type | Description |
|---|---|---|
| MediaInput | record | Input with binary payload and metadata |
| MediaAnalysisResult | record | Result with description, tags, confidence |
| MediaAnalysisProvider | interface | SPI for async media analysis |
| CompositeMediaAnalyzer | class | Delegates to first provider supporting MIME type |

### Class Relationships

```
MediaAnalysisProvider (SPI)
  └── CompositeMediaAnalyzer
        └── routes by MIME type to first matching provider
```

---

## jaiclaw-audit

**Purpose**: Audit logging SPI with bounded in-memory default for development.

**Package**: `io.jaiclaw.audit`
**Directory**: `extensions/jaiclaw-audit`
**Dependencies**: `jaiclaw-core`

### Class Reference

| Class | Type | Description |
|---|---|---|
| AuditEvent | record | Immutable audit event (inner enum Outcome) |
| AuditLogger | interface | SPI for audit logging |
| InMemoryAuditLogger | class | Bounded in-memory implementation |

### Class Relationships

```
AuditLogger (SPI)
  └── InMemoryAuditLogger (default)
        └── AuditEvent (immutable records)
```

---

## jaiclaw-compaction

**Purpose**: Context window compaction via LLM summarization to stay within token budgets.

**Package**: `io.jaiclaw.compaction`
**Directory**: `extensions/jaiclaw-compaction`
**Dependencies**: `jaiclaw-core`, Spring AI

### Class Reference

| Class | Type | Description |
|---|---|---|
| TokenEstimator | class | Estimates tokens via character-based approximation |
| IdentifierPreserver | class | Extracts identifiers to verify they survive summarization |
| CompactionSummarizer | class | Summarizes conversation chunks using LLM |
| CompactionService | class | Orchestrates: check budget → chunk → summarize |
| CompactionServiceAdapter | class | Adapts to ContextCompactor SPI |

### Class Relationships

```
ContextCompactor (SPI from jaiclaw-core)
  └── CompactionServiceAdapter
        └── CompactionService
              ├── TokenEstimator (budget check)
              ├── IdentifierPreserver (quality guard)
              └── CompactionSummarizer (LLM calls)
```

---

## jaiclaw-browser

**Purpose**: Playwright-based browser automation with tab pooling and accessibility tree snapshots.

**Package**: `io.jaiclaw.browser`
**Directory**: `extensions/jaiclaw-browser`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`, Playwright

### Class Reference

| Class | Type | Description |
|---|---|---|
| PageSnapshot | record | Page as accessibility tree (inner record PageElement) |
| BrowserConfig | record | Browser service configuration |
| BrowserService | class | Playwright lifecycle, tab pool, profile loading |
| BrowserSession | class | Single tab with navigation and interaction |
| BrowserTools | class | Factory for 8 browser tools (inner static classes) |

### Class Relationships

```
BrowserTools (factory, 8 inner tool classes)
  └── BrowserService
        ├── BrowserConfig
        ├── BrowserSession (per-tab)
        │     └── PageSnapshot → PageElement
        └── Playwright (managed lifecycle)
```

---

## jaiclaw-code

**Purpose**: File editing and code search tools inspired by Claude Code's Edit, Grep, and Glob.

**Package**: `io.jaiclaw.code`
**Directory**: `extensions/jaiclaw-code`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`

### Class Reference

| Class | Type | Description |
|---|---|---|
| FileEditTool | class | Surgical string replacement; optional workspace boundary enforcement |
| GrepTool | class | Content search with regex; optional workspace boundary enforcement |
| GlobTool | class | File pattern matching; optional workspace boundary enforcement |
| CodeTools | class | Factory for code tools |
| CodeToolsAutoConfiguration | class | Auto-configuration; reads `jaiclaw.tools.code.workspace-boundary` |

### Class Relationships

```
CodeToolsAutoConfiguration → ToolRegistry
  └── CodeTools (factory)
        ├── FileEditTool ─── extends AbstractBuiltinTool
        ├── GrepTool ──────── extends AbstractBuiltinTool
        └── GlobTool ──────── extends AbstractBuiltinTool
              └── all use WorkspaceBoundary.resolve() when enforceWorkspaceBoundary=true
```

### Security Hardening (Opt-In)

| Property | Default | Description |
|---|---|---|
| `jaiclaw.tools.code.workspace-boundary` | `false` | Enforce path traversal protection via `WorkspaceBoundary.resolve()` |

When enabled, all three code tools (`FileEditTool`, `GrepTool`, `GlobTool`) validate that resolved paths stay within the workspace directory. Attempts to access files outside the workspace (e.g., `../../etc/passwd`) return a `ToolResult.Error` with a "Path traversal blocked" message.

---

## jaiclaw-cron

**Purpose**: Cron job scheduling with virtual threads and JSON file persistence.

**Package**: `io.jaiclaw.cron`
**Directory**: `extensions/jaiclaw-cron`
**Dependencies**: `jaiclaw-core`

### Class Reference

| Class | Type | Description |
|---|---|---|
| CronJobStore | interface | Persistence abstraction for cron jobs |
| CronScheduleComputer | class | Parses cron expressions, computes next fire times |
| CronJobExecutor | class | Executes job via isolated agent session |
| CronService | class | Job scheduling: start/stop/pause |
| JsonFileCronJobStore | class | JSON file persistence |

### Class Relationships

```
CronService (scheduler)
  ├── CronScheduleComputer (next fire time)
  ├── CronJobExecutor (runs agent session)
  └── CronJobStore (SPI)
        └── JsonFileCronJobStore (default)
```

---

## jaiclaw-cron-manager

**Purpose**: Reusable cron job management extension with scheduling, Spring Batch execution, H2 persistence, and MCP tool exposure. Activates only when `jaiclaw.cron.manager.enabled=true`.

**Package**: `io.jaiclaw.cronmanager`
**Directory**: `extensions/jaiclaw-cron-manager`
**Dependencies**: `jaiclaw-cron`, `jaiclaw-agent`, `jaiclaw-gateway`, Spring Batch, H2

### Class Reference

| Class | Type | Description |
|---|---|---|
| CronManagerAutoConfiguration | class | `@AutoConfiguration` gated on `jaiclaw.cron.manager.enabled=true` |
| CronJobManagerService | class | Central orchestrator for CRUD, scheduling, execution, history |
| CronAgentFactory | class | Creates per-execution agent sessions |
| CronBatchJobFactory | class | Factory for Spring Batch Job instances |
| CronJobTasklet | class | Spring Batch tasklet wrapping single job execution |
| CronManagerMcpToolProvider | class | MCP tool provider exposing 8 cron management tools |
| CronManagerLifecycle | class | SmartLifecycle for init and graceful shutdown |
| CronJobDefinition | record | Extended job def wrapping CronJob with agent config |
| CronExecutionRecord | record | Persistent record of single execution |
| CronJobDefinitionStore | interface | Persistence SPI for job definitions |
| CronExecutionStore | interface | Persistence SPI for execution history |
| H2PersistenceAutoConfiguration | class | Auto-configuration for H2 persistence stores |
| H2CronJobDefinitionStore | class | H2-backed job definition store |
| H2CronExecutionStore | class | H2-backed execution history store |
| H2CronJobStore | class | Bridges CronJobStore to H2-backed store |

### Class Relationships

```
CronManagerAutoConfiguration
  @ConditionalOnProperty(name = "jaiclaw.cron.manager.enabled", havingValue = "true")
  │
  ├── CronJobManagerService (central orchestrator)
  │     ├── CronAgentFactory (creates agent sessions)
  │     ├── CronBatchJobFactory → CronJobTasklet (Spring Batch)
  │     ├── CronJobDefinitionStore (SPI)
  │     └── CronExecutionStore (SPI)
  │
  ├── CronManagerMcpToolProvider (implements McpToolProvider)
  │     └── CronJobManagerService
  │
  └── CronManagerLifecycle (SmartLifecycle)

H2PersistenceAutoConfiguration
  ├── H2CronJobDefinitionStore → implements CronJobDefinitionStore
  ├── H2CronExecutionStore → implements CronExecutionStore
  └── H2CronJobStore → implements CronJobStore
```

### Auto-Configuration Registration

The `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file registers both:
- `io.jaiclaw.cronmanager.CronManagerAutoConfiguration`
- `io.jaiclaw.cronmanager.persistence.h2.H2PersistenceAutoConfiguration`

### MCP Tools Exposed

| Tool | Description |
|---|---|
| `cron-list` | List all cron jobs |
| `cron-create` | Create a new cron job |
| `cron-update` | Update an existing job |
| `cron-delete` | Delete a job |
| `cron-run` | Manually trigger a job |
| `cron-pause` | Pause a job |
| `cron-resume` | Resume a paused job |
| `cron-history` | View execution history |

### Embedding in Gateway

Add `jaiclaw-starter-cron` to the gateway's classpath and set `jaiclaw.cron.manager.enabled=true`. The extension auto-activates and its `CronManagerMcpToolProvider` is collected by the gateway's existing `McpServerRegistry`. The gateway-app provides a `-Pcron` Maven profile for this.

---

## jaiclaw-voice

**Purpose**: Text-to-speech and speech-to-text SPI with OpenAI provider and directive parsing.

**Package**: `io.jaiclaw.voice`
**Directory**: `extensions/jaiclaw-voice`
**Dependencies**: `jaiclaw-core`

### Class Reference

| Class | Type | Description |
|---|---|---|
| TtsProvider | interface | SPI for text-to-speech |
| SttProvider | interface | SPI for speech-to-text |
| OpenAiTtsProvider | class | OpenAI TTS via /v1/audio/speech |
| OpenAiSttProvider | class | OpenAI Whisper STT via /v1/audio/transcriptions |
| TtsDirectiveParser | class | Parses [[tts:...]] directives (inner record TtsSegment) |
| VoiceConfig | record | Voice service configuration |
| VoiceService | class | Orchestrates TTS/STT with provider fallback |

### Class Relationships

```
TtsProvider (SPI)           SttProvider (SPI)
  └── OpenAiTtsProvider       └── OpenAiSttProvider

VoiceService
  ├── TtsProvider chain (fallback)
  ├── SttProvider chain (fallback)
  ├── TtsDirectiveParser → TtsSegment
  └── VoiceConfig
```

---

## jaiclaw-identity

**Purpose**: Cross-channel identity linking, OAuth credential management (PKCE auth code + device code flows), token refresh, CLI sync, and session rotation.

**Packages**: `io.jaiclaw.identity`, `io.jaiclaw.identity.auth`, `io.jaiclaw.identity.oauth`, `io.jaiclaw.identity.secret`, `io.jaiclaw.identity.sync`
**Directory**: `extensions/jaiclaw-identity`
**Dependencies**: `jaiclaw-core`, Jackson

### Class Reference

#### Identity Linking (`io.jaiclaw.identity`)

| Class | Type | Description |
|---|---|---|
| IdentityLinkStore | class | JSON file persistence, thread-safe |
| IdentityResolver | class | Resolves canonical user ID from channel-specific ID |
| IdentityLinkService | class | Service with validation |

#### Auth Profile Management (`io.jaiclaw.identity.auth`)

| Class | Type | Description |
|---|---|---|
| AuthProfileStoreManager | class | File I/O, locking, merge, multi-agent credential store |
| AuthProfileStoreSerializer | class | Custom Jackson serializer with OpenClaw compat aliases |
| AuthProfileFileLock | class | File locking with exponential backoff |
| CredentialStateEvaluator | class | Evaluates token expiry state and credential eligibility |
| SessionAuthProfileResolver | class | Round-robin rotation, cooldown, user-pin sticky |
| ProviderTokenRefresherRegistry | class | Registry of per-provider token refreshers |

#### OAuth Flows (`io.jaiclaw.identity.oauth`)

| Class | Type | Description |
|---|---|---|
| OAuthFlowManager | class | Orchestrates auth code + device code flows, credential storage |
| AuthorizationCodeFlow | class | PKCE auth code flow: URL construction, code exchange, userinfo |
| DeviceCodeFlow | class | Device code flow (RFC 8628): request + polling |
| OAuthCallbackServer | class | Loopback HTTP server for OAuth redirect callbacks |
| OAuthProviderConfig | record | Provider endpoints, client credentials, scopes, flow type |
| OAuthFlowResult | record | Access token, refresh token, email, expiry |
| OAuthFlowType | enum | AUTHORIZATION_CODE, DEVICE_CODE |
| OAuthFlowException | class | Thrown when an OAuth flow fails |
| PkceGenerator | class | PKCE verifier + S256 challenge generation |
| RemoteEnvironmentDetector | class | Detects SSH/headless/Codespaces environments |
| BrowserLauncher | class | Platform-specific browser opening |

#### Secret Resolution (`io.jaiclaw.identity.secret`)

| Class | Type | Description |
|---|---|---|
| SecretRefResolver | class | Resolves SecretRef via env, file, or exec backends |

### Class Relationships

```
OAuthFlowManager (orchestrator)
  ├── AuthorizationCodeFlow (PKCE auth code)
  │     └── HttpClient → provider /token + /userinfo
  ├── DeviceCodeFlow (RFC 8628)
  │     └── HttpClient → provider /device/code + /token
  ├── OAuthCallbackServer (loopback redirect)
  └── AuthProfileStoreManager (credential persistence)
        └── AuthProfileFileLock (file locking)

IdentityLinkService
  ├── IdentityResolver (lookup)
  └── IdentityLinkStore (JSON persistence)
        └── IdentityLink (record from jaiclaw-core)
```

### Integration Tests

18 integration tests run under the `integration-test` Maven profile via `maven-failsafe-plugin`. They use `MockOAuthServer` (a local `com.sun.net.httpserver.HttpServer` on a random port) to simulate provider endpoints with real HTTP calls.

| Spec | Tests | Flow |
|------|-------|------|
| `AuthorizationCodeFlowIT` | 6 | Auth code + PKCE + userinfo + credential storage |
| `DeviceCodeFlowIT` | 7 | Device code request + polling (pending/slow_down/success/denied) |
| `OAuthCallbackServerIT` | 5 | Loopback callback + CSRF + error + timeout + e2e |

See [OAuth Integration Tests Architecture](../../docs/dev/OAUTH-INTEGRATION-TESTS.md) for full details.

---

## jaiclaw-canvas

**Purpose**: A2UI artifact rendering and HTML file management for rich visual agent output.

**Package**: `io.jaiclaw.canvas`
**Directory**: `extensions/jaiclaw-canvas`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`

### Class Reference

| Class | Type | Description |
|---|---|---|
| CanvasConfig | record | Canvas host server config |
| CanvasFileManager | class | Manages agent-generated HTML files |
| CanvasService | class | Orchestrates canvas operations |
| CanvasTools | class | Factory for 3 canvas tools (inner static classes) |

### Class Relationships

```
CanvasTools (factory, 3 inner tool classes)
  └── CanvasService
        ├── CanvasFileManager (HTML file management)
        └── CanvasConfig
```

---

## jaiclaw-docstore

**Purpose**: Document store with indexing, full-text/vector/hybrid search, basic and LLM-powered analysis.

**Package**: `io.jaiclaw.docstore`
**Directory**: `extensions/jaiclaw-docstore`
**Dependencies**: `jaiclaw-core`, `jaiclaw-documents`, Spring AI (optional for vector search)

### Class Reference

| Class | Type | Description |
|---|---|---|
| DocStoreEntry | record | Indexed document (inner enum EntryType) |
| AnalysisResult | record | Analysis result (inner enum AnalysisLevel) |
| AddRequest | record | Request to add entry |
| DocStoreRepository | interface | SPI for persistence |
| InMemoryDocStoreRepository | class | In-memory persistence |
| JsonFileDocStoreRepository | class | JSON file-backed persistence |
| DocStoreSearchProvider | interface | SPI for search |
| DocStoreSearchResult | record | Search result with score, snippet |
| DocStoreSearchOptions | record | Filtering and limiting options |
| FullTextDocStoreSearch | class | Full-text with inverted index |
| VectorDocStoreSearch | class | Semantic search via VectorStore |
| HybridDocStoreSearch | class | Hybrid RRF combining both |
| DocStoreAnalyzer | interface | SPI for document analysis |
| BasicDocStoreAnalyzer | class | Basic via DocumentIngestionPipeline |
| LlmDocStoreAnalyzer | class | LLM-powered with summarization |
| DocStoreService | class | Central orchestrator |
| DocStoreToolProvider | class | 8 JaiClaw tools (inner classes) |

### Class Relationships

```
DocStoreService (orchestrator)
  ├── DocStoreRepository (SPI)
  │     ├── InMemoryDocStoreRepository
  │     └── JsonFileDocStoreRepository
  ├── DocStoreSearchProvider (SPI)
  │     ├── FullTextDocStoreSearch
  │     ├── VectorDocStoreSearch
  │     └── HybridDocStoreSearch (RRF merge)
  ├── DocStoreAnalyzer (SPI)
  │     ├── BasicDocStoreAnalyzer
  │     └── LlmDocStoreAnalyzer
  └── DocStoreToolProvider (8 agent tools)
```

---

## jaiclaw-docstore-telegram

**Purpose**: Telegram-specific DocStore integration with auto-indexing of forwarded files and bot commands.

**Package**: `io.jaiclaw.docstore.telegram`
**Directory**: `extensions/jaiclaw-docstore-telegram`
**Dependencies**: `jaiclaw-docstore`, `jaiclaw-plugin-sdk`, `jaiclaw-channel-telegram`

### Class Reference

| Class | Type | Description |
|---|---|---|
| TelegramDocStorePlugin | class | Auto-indexing and commands plugin |

### Class Relationships

```
JaiClawPlugin (SPI)
  └── TelegramDocStorePlugin
        ├── DocStoreService (from jaiclaw-docstore)
        └── hooks into Telegram message events
```

---

## jaiclaw-subscription

**Purpose**: Subscription management with multi-provider payment integration (Stripe, PayPal, Telegram Payments).

**Package**: `io.jaiclaw.subscription`
**Directory**: `extensions/jaiclaw-subscription`
**Dependencies**: `jaiclaw-core`

### Class Reference

| Class | Type | Description |
|---|---|---|
| Subscription | record | User's subscription to a plan |
| SubscriptionPlan | record | Plan with pricing and duration |
| PaymentEvent | record | Event from payment provider |
| AccessChange | record | Change in access rights |
| CheckoutResult | record | Checkout session result |
| PaymentVerification | record | Payment verification result |
| SubscriptionStatus | enum | ACTIVE, PAST_DUE, CANCELLED, EXPIRED |
| PaymentEventType | enum | CHECKOUT_COMPLETED, PAYMENT_SUCCEEDED, etc. |
| AccessChangeType | enum | GRANT, REVOKE |
| PaymentProvider | interface | SPI for payment providers |
| SubscriptionRepository | interface | SPI for persistence |
| SubscriptionLifecycleListener | interface | SPI for lifecycle events |
| SubscriptionService | class | Central subscription management |
| SubscriptionExpiryScheduler | class | Checks for expired subscriptions |
| JsonFileSubscriptionRepository | class | JSON file persistence |
| StripePaymentProvider | class | Stripe Checkout Sessions |
| TelegramPaymentProvider | class | Telegram Payments |
| PayPalPaymentProvider | class | PayPal integration with optional webhook signature verification |

### Class Relationships

```
SubscriptionService (central)
  ├── SubscriptionRepository (SPI)
  │     └── JsonFileSubscriptionRepository
  ├── PaymentProvider (SPI)
  │     ├── StripePaymentProvider
  │     ├── TelegramPaymentProvider
  │     └── PayPalPaymentProvider
  ├── SubscriptionLifecycleListener (SPI)
  │     └── (implemented by channel-specific modules)
  └── SubscriptionExpiryScheduler
```

---

## jaiclaw-subscription-telegram

**Purpose**: Telegram-specific subscription access control with group membership management.

**Package**: `io.jaiclaw.subscription.telegram`
**Directory**: `extensions/jaiclaw-subscription-telegram`
**Dependencies**: `jaiclaw-subscription`, `jaiclaw-plugin-sdk`, `jaiclaw-channel-telegram`

### Class Reference

| Class | Type | Description |
|---|---|---|
| TelegramGroupManager | class | Telegram Bot API admin operations |
| TelegramAccessController | class | Subscription lifecycle access controller |
| TelegramSubscriptionPlugin | class | Telegram integration plugin |

### Class Relationships

```
JaiClawPlugin (SPI)
  └── TelegramSubscriptionPlugin
        ├── TelegramAccessController
        │     └── implements SubscriptionLifecycleListener
        └── TelegramGroupManager
              └── Telegram Bot API (admin operations)
```

---

## jaiclaw-tools-k8s

**Purpose**: Kubernetes monitoring and management tools using Fabric8 client.

**Package**: `io.jaiclaw.tools.k8s`
**Directory**: `extensions/jaiclaw-tools-k8s`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`, Fabric8 Kubernetes Client

### Class Reference

| Class | Type | Description |
|---|---|---|
| KubernetesClientProvider | class | Creates/caches KubernetesClient |
| AbstractK8sTool | abstract class | Base for K8s tools |
| ListNamespacesTool | class | Lists namespaces |
| ListNodesTool | class | Lists nodes |
| ListPodsTool | class | Lists pods |
| ListDeploymentsTool | class | Lists deployments |
| ListEventsTool | class | Lists events |
| GetPodLogsTool | class | Gets pod logs |
| DescribeResourceTool | class | Describes a resource |
| GetResourceUsageTool | class | Gets resource usage metrics |
| KubectlExecTool | class | Executes kubectl commands |
| KubernetesTools | class | Factory for all K8s tools |
| KubernetesToolsAutoConfiguration | class | Auto-configuration |

### Class Relationships

```
KubernetesToolsAutoConfiguration → ToolRegistry
  └── KubernetesTools (factory)
        └── AbstractK8sTool (base)
              ├── ListNamespacesTool
              ├── ListNodesTool
              ├── ListPodsTool
              ├── ListDeploymentsTool
              ├── ListEventsTool
              ├── GetPodLogsTool
              ├── DescribeResourceTool
              ├── GetResourceUsageTool
              └── KubectlExecTool
                    └── KubernetesClientProvider (shared)
```

---

## jaiclaw-tools-security

**Purpose**: Cryptographic security handshake protocol for secure agent-to-agent communication with MCP server exposure.

**Package**: `io.jaiclaw.tools.security`
**Directory**: `extensions/jaiclaw-tools-security`
**Dependencies**: `jaiclaw-core`, `jaiclaw-tools`, `jaiclaw-gateway`

### Class Reference

| Class | Type | Description |
|---|---|---|
| CryptoService | class | Cryptographic operations for handshake |
| HandshakeMode | enum | LOCAL, HTTP_CLIENT, ORCHESTRATED |
| HandshakeSession | class | Active handshake session state |
| HandshakeRequest | record | Client handshake request |
| ServerHello | record | Server hello response |
| KeyExchangeResult | record | Key exchange result |
| AuthResult | record | Authentication result |
| SessionEstablished | record | Session establishment confirmation |
| AbstractSecurityTool | abstract class | Base for security tools |
| AdvertiseCapabilitiesTool | class | Advertises client capabilities |
| EstablishContextTool | class | Establishes handshake context |
| GenerateKeyPairTool | class | Generates key pair |
| NegotiateSessionTool | class | Negotiates session parameters |
| ChallengeResponseTool | class | Responds to challenge nonce |
| VerifyIdentityTool | class | Verifies identity via signature |
| SecurityHandshakeTool | class | Orchestrates complete handshake |
| HandshakeSessionStore | class | Stores active sessions |
| BootstrapValidator | interface | SPI for bootstrap auth |
| ApiKeyBootstrapValidator | class | API key bootstrap validation |
| ClientCertBootstrapValidator | class | Client cert bootstrap validation |
| MutualBootstrapValidator | class | Mutual TLS bootstrap validation |
| HandshakeHttpClient | class | HTTP client for remote handshake |
| SecurityHandshakeProperties | record | Configuration |
| BootstrapTrust | enum | Bootstrap trust method |
| SecurityHandshakeAutoConfiguration | class | Auto-configuration |
| HandshakeTokenFilter | class | Servlet filter for Bearer token |
| SecurityTools | class | Factory for security tools |
| SecurityToolsAutoConfiguration | class | Auto-configuration for tools |
| SecurityHandshakeAgent | class | Agent facade |
| HandshakeServerEndpoint | interface | Server-side protocol interface |
| SecurityHandshakeMcpProvider | class | MCP tool provider for server-side handshake |

### Class Relationships

```
SecurityToolsAutoConfiguration → ToolRegistry
  └── SecurityTools (factory)
        └── AbstractSecurityTool (base)
              ├── AdvertiseCapabilitiesTool ─┐
              ├── EstablishContextTool ──────│
              ├── GenerateKeyPairTool ───────│── handshake steps
              ├── NegotiateSessionTool ──────│
              ├── ChallengeResponseTool ─────│
              ├── VerifyIdentityTool ────────┘
              └── SecurityHandshakeTool (orchestrator)
                    ├── CryptoService
                    ├── HandshakeSessionStore
                    └── BootstrapValidator (SPI)
                          ├── ApiKeyBootstrapValidator
                          ├── ClientCertBootstrapValidator
                          └── MutualBootstrapValidator

SecurityHandshakeMcpProvider → McpToolProvider (server-side)
  └── HandshakeServerEndpoint (interface)
```

---

## jaiclaw-calendar

**Purpose**: Calendar management extension — event CRUD, auto-scheduling, multi-tenancy, and MCP hosting.

**Package**: `io.jaiclaw.calendar`
**Directory**: `extensions/jaiclaw-calendar`
**Dependencies**: `jaiclaw-tools`, `jackson-databind`, `jackson-datatype-jsr310`, `reactor-core`, Redis (optional)

### Class Reference

| Class | Type | Description |
|---|---|---|
| `CalendarService` | Service | Event CRUD, scheduling, calendar management |
| `CalendarProvider` | SPI | Pluggable storage backend (in-memory, Redis) |
| `InMemoryCalendarProvider` | Provider | Default in-memory storage with tenant isolation |
| `RedisCalendarProvider` | Provider | Redis-backed persistent storage |
| `CalendarMcpToolProvider` | McpToolProvider | 8 MCP tools: create/list/update/delete events, available slots, calendars, time lookup |
| `CalendarEventValidator` | Utility | Validates event timing, duration, conflict detection |
| `CalendarProperties` | Record | Configuration properties for calendar module |
| `CalendarEvent` | Record | Calendar event domain model |
| `CalendarInfo` | Record | Calendar metadata |
| `TimeSlot` | Record | Available time slot for scheduling |
| `AppointmentRequest` | Record | Auto-scheduling request with duration and time bounds |
| `JaiClawCalendarAutoConfiguration` | Auto-config | Conditional on `jaiclaw.calendar.enabled=true` |

---

## jaiclaw-messaging

**Purpose**: MCP server exposing messaging channel tools — send/receive messages, manage sessions, agent-routed chat across all configured channels. Supports HTTP REST, SSE, and stdio transports.

**Package**: `io.jaiclaw.messaging`
**Directory**: `extensions/jaiclaw-messaging`
**Dependencies**: `jaiclaw-core`, `jaiclaw-channel-api`, `jaiclaw-gateway`, `jaiclaw-agent`, `jackson-databind`, `jackson-datatype-jsr310`

### Class Reference

| Class | Type | Description |
|---|---|---|
| `MessagingMcpToolProvider` | McpToolProvider | 8 MCP tools for channel messaging and agent-routed chat |
| `MessagingMcpProperties` | Record | Configuration: enabled, allowedChannels whitelist, maxRecipientsPerBroadcast |
| `JaiClawMessagingAutoConfiguration` | Auto-config | Conditional on `jaiclaw.messaging.enabled=true` |
| `MessagingServerApplication` | App | Standalone entry point (`--stdio` for stdio, default for web) |

### MCP Tools (8)

| Tool | Required Args | Description |
|---|---|---|
| `list_channels` | _(none)_ | List registered channels with running status |
| `send_message` | `channelId`, `peerId`, `content` | Send message through a channel adapter |
| `get_channel_status` | `channelId` | Get adapter status for a channel |
| `list_sessions` | _(optional `activeOnly`, `limit`)_ | List conversation sessions |
| `get_conversation` | `sessionKey` (optional `limit`) | Get message history for a session |
| `broadcast_message` | `recipients[]`, `content` | Send to multiple recipients |
| `agent_chat` | `content` (optional `channelId`, `peerId`, `deliverToChannel`) | Sync: routes through agent runtime, returns response |
| `agent_chat_async` | `content`, `channelId`, `peerId` | Fire-and-forget: agent processes + delivers to channel |

### Configuration

```yaml
jaiclaw:
  messaging:
    enabled: true
    allowed-channels: [telegram, slack, email, sms]
    max-recipients-per-broadcast: 100
```

### Transport Support

The messaging MCP server is accessible via three transports:

1. **HTTP REST** — automatic via `McpController` at `/mcp/messaging/tools/*`
2. **SSE** — via `McpSseServerController` at `GET /mcp/messaging/sse` + `POST /mcp/messaging/jsonrpc`
3. **Stdio** — via standalone JAR: `java -jar jaiclaw-messaging.jar --stdio`

### Architecture

```
MessagingMcpToolProvider
  ├── list_channels ──────→ ChannelRegistry.all()
  ├── send_message ───────→ ChannelAdapter.sendMessage()
  ├── get_channel_status ─→ ChannelAdapter.isRunning()
  ├── list_sessions ──────→ SessionManager.listSessions()
  ├── get_conversation ───→ SessionManager.get() → session.messages()
  ├── broadcast_message ──→ iterate recipients → ChannelAdapter.sendMessage()
  ├── agent_chat ─────────→ GatewayService.handleSync()
  └── agent_chat_async ───→ GatewayService.handleAsync() + channel delivery
```

---

## Cross-Module Dependency Summary

| Module | Depends On |
|---|---|
| jaiclaw-documents | jaiclaw-core |
| jaiclaw-media | jaiclaw-core |
| jaiclaw-audit | jaiclaw-core |
| jaiclaw-compaction | jaiclaw-core, Spring AI |
| jaiclaw-browser | jaiclaw-core, jaiclaw-tools, Playwright |
| jaiclaw-code | jaiclaw-core, jaiclaw-tools |
| jaiclaw-cron | jaiclaw-core |
| jaiclaw-cron-manager | jaiclaw-cron, jaiclaw-agent, jaiclaw-gateway, Spring Batch, H2 |
| jaiclaw-voice | jaiclaw-core |
| jaiclaw-identity | jaiclaw-core |
| jaiclaw-canvas | jaiclaw-core, jaiclaw-tools |
| jaiclaw-docstore | jaiclaw-core, jaiclaw-documents, Spring AI (optional) |
| jaiclaw-docstore-telegram | jaiclaw-docstore, jaiclaw-plugin-sdk, jaiclaw-channel-telegram |
| jaiclaw-subscription | jaiclaw-core |
| jaiclaw-subscription-telegram | jaiclaw-subscription, jaiclaw-plugin-sdk, jaiclaw-channel-telegram |
| jaiclaw-tools-k8s | jaiclaw-core, jaiclaw-tools, Fabric8 |
| jaiclaw-tools-security | jaiclaw-core, jaiclaw-tools, jaiclaw-gateway |
| jaiclaw-calendar | jaiclaw-tools, jackson, reactor-core, Redis (optional) |
| jaiclaw-messaging | jaiclaw-core, jaiclaw-channel-api, jaiclaw-gateway, jaiclaw-agent, jackson |
