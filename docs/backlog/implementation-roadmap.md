# JaiClaw Feature Parity — Implementation Roadmap

**Created:** 2026-05-28
**Baseline:** OpenClaw commit `5869131eea` (main)
**Scope:** All P1 + P2 items from feature-parity-report.md

This document provides detailed per-module specifications for each work item, including package structure, key classes, SPI designs, dependencies, and integration patterns. All designs follow JaiClaw's established conventions (see CLAUDE.md § Architecture).

---

## Table of Contents

- [Phase 1 — P1 Quick Wins](#phase-1--p1-quick-wins)
  - [1.1 Auto-Reply Chunking](#11-auto-reply-chunking)
  - [1.2 Web Readability Extraction](#12-web-readability-extraction)
  - [1.3 WhatsApp Channel](#13-whatsapp-channel)
  - [1.4 Image Generation](#14-image-generation)
  - [1.5 Observability Starter](#15-observability-starter)
- [Phase 2 — P2 Core Infrastructure](#phase-2--p2-core-infrastructure)
  - [2.1 Web Search Provider Registry](#21-web-search-provider-registry)
  - [2.2 Enhanced Memory](#22-enhanced-memory)
  - [2.3 Memory Wiki](#23-memory-wiki)
  - [2.4 Model Catalog](#24-model-catalog)
  - [2.5 Plugin State Store](#25-plugin-state-store)
  - [2.6 Token Compression](#26-token-compression)
- [Phase 3 — P2 Agent & Orchestration](#phase-3--p2-agent--orchestration)
  - [3.1 Task Executor & Flow Engine](#31-task-executor--flow-engine)
  - [3.2 Thread Ownership](#32-thread-ownership)
  - [3.3 Webhook Event Routing](#33-webhook-event-routing)
  - [3.4 Admin HTTP RPC](#34-admin-http-rpc)
- [Phase 4 — P2 Channels](#phase-4--p2-channels)
  - [4.1 Line Channel](#41-line-channel)
  - [4.2 Matrix Channel](#42-matrix-channel)
  - [4.3 Google Chat Channel](#43-google-chat-channel)
- [Phase 5 — P2 Media & Voice](#phase-5--p2-media--voice)
  - [5.1 Video Generation](#51-video-generation)
  - [5.2 ElevenLabs TTS Provider](#52-elevenlabs-tts-provider)
  - [5.3 Deepgram STT Provider](#53-deepgram-stt-provider)
- [Phase 6 — P2 Observability & Knowledge](#phase-6--p2-observability--knowledge)
  - [6.1 Trajectory Tracking](#61-trajectory-tracking)
  - [6.2 Transcript Storage](#62-transcript-storage)
  - [6.3 Internationalization (i18n)](#63-internationalization-i18n)
  - [6.4 Secrets Management](#64-secrets-management)
- [Dependency Graph](#dependency-graph)

---

## Phase 1 — P1 Quick Wins

### 1.1 Auto-Reply Chunking

**Location:** Enhance `core/jaiclaw-channel-api/`
**Effort:** Low (1–3 days)

#### Problem
Outbound messages exceeding platform character limits are silently truncated or rejected. OpenClaw splits long messages into platform-aware chunks before delivery.

#### Design

Add a `MessageChunker` utility to the channel API module. Each `ChannelAdapter` can declare its platform limits. The `GatewayService` applies chunking before calling `sendMessage()`.

#### Package Structure
```
core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/
  chunking/
    MessageChunker.java           # Stateless utility
    ChunkingStrategy.java         # Sealed interface
    LengthChunkingStrategy.java   # Split by max length
    NewlineChunkingStrategy.java  # Split on newlines within limit
    PlatformLimits.java           # Record: maxTextLength, chunkMode
```

#### Key Classes

```java
// Sealed interface for chunking strategies
public sealed interface ChunkingStrategy
    permits LengthChunkingStrategy, NewlineChunkingStrategy {
    List<String> chunk(String text, int maxLength);
}

// Platform limits declared per channel
public record PlatformLimits(
    int maxTextLength,
    ChunkMode chunkMode
) {
    public enum ChunkMode { LENGTH, NEWLINE }

    // Known defaults
    public static final PlatformLimits TELEGRAM = new PlatformLimits(4096, ChunkMode.LENGTH);
    public static final PlatformLimits SLACK    = new PlatformLimits(40000, ChunkMode.NEWLINE);
    public static final PlatformLimits DISCORD  = new PlatformLimits(2000, ChunkMode.LENGTH);
    public static final PlatformLimits WHATSAPP = new PlatformLimits(65536, ChunkMode.LENGTH);
    public static final PlatformLimits DEFAULT  = new PlatformLimits(4096, ChunkMode.LENGTH);
}

// Stateless chunker
public final class MessageChunker {
    public static List<String> chunk(String text, PlatformLimits limits) { ... }
}
```

#### ChannelAdapter Enhancement

```java
public interface ChannelAdapter {
    // ... existing methods ...

    /** Platform-specific message size limits. Default: 4096 chars, LENGTH mode. */
    default PlatformLimits platformLimits() {
        return PlatformLimits.DEFAULT;
    }
}
```

#### Integration Point
`GatewayService.sendResponse()` calls `MessageChunker.chunk()` before iterating over chunks and calling `adapter.sendMessage()` for each.

#### Dependencies
None new — pure Java utility within existing module.

---

### 1.2 Web Readability Extraction

**Location:** Enhance `core/jaiclaw-tools/` (WebFetchTool)
**Effort:** Low (1–3 days)

#### Problem
`WebFetchTool` returns raw HTML content. Users need readable article text extracted from web pages.

#### Design

Add a `ReadabilityExtractor` that uses Jsoup to extract article content from HTML, similar to Mozilla's Readability algorithm. Wire it into `WebFetchTool` as a post-processing step.

#### Package Structure
```
core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/web/
  ReadabilityExtractor.java       # Jsoup-based article extraction
  ReadabilityResult.java          # Record: title, content, excerpt, byline
```

#### Key Classes

```java
public record ReadabilityResult(
    String title,
    String content,       // Cleaned text content
    String excerpt,       // First ~200 chars
    String byline,        // Author if found
    String siteName       // Site name if found
) {}

public final class ReadabilityExtractor {
    /** Extract readable article content from HTML. */
    public static ReadabilityResult extract(String html, String url) { ... }
}
```

#### Integration
`WebFetchTool.execute()` gains a `readability` boolean parameter (default `true`). When enabled, HTML responses are processed through `ReadabilityExtractor` before returning to the LLM.

#### Dependencies
- `org.jsoup:jsoup` (already a dependency of `jaiclaw-documents`)
- Add to `jaiclaw-tools` POM

---

### 1.3 WhatsApp Channel

**Location:** New module `channels/jaiclaw-channel-whatsapp/`
**Effort:** Medium (1–2 weeks)

#### Problem
WhatsApp is the most-requested missing channel. OpenClaw supports it via the Baileys library; JaiClaw needs a Java-native implementation using the WhatsApp Cloud API (Meta Business Platform).

#### Design

Implement `WhatsAppAdapter` using the WhatsApp Cloud API (not Baileys, which is Node.js-specific). This uses REST webhooks for inbound and the Graph API for outbound. Follows the same `ChannelAdapter` SPI pattern as other JaiClaw channels.

#### Package Structure
```
channels/jaiclaw-channel-whatsapp/
  pom.xml
  src/main/java/io/jaiclaw/channel/whatsapp/
    WhatsAppAdapter.java              # ChannelAdapter implementation
    WhatsAppConfig.java               # Configuration record
    WhatsAppWebhookController.java    # Spring MVC webhook endpoint
    WhatsAppCloudApiClient.java       # Graph API REST client
    WhatsAppMessageMapper.java        # Platform ↔ ChannelMessage mapping
    WhatsAppMediaHandler.java         # Media upload/download
    model/
      WhatsAppInboundMessage.java     # Webhook payload record
      WhatsAppOutboundMessage.java    # Send API request record
      WhatsAppMediaMessage.java       # Media message types
      WhatsAppInteractiveMessage.java # Buttons, lists
      WhatsAppTemplateMessage.java    # Template messages
  src/main/resources/
    META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/groovy/io/jaiclaw/channel/whatsapp/
    WhatsAppAdapterSpec.groovy
    WhatsAppMessageMapperSpec.groovy
    WhatsAppWebhookControllerSpec.groovy
```

#### Key Classes

```java
public class WhatsAppAdapter implements ChannelAdapter {
    @Override public String channelId() { return "whatsapp"; }
    @Override public String displayName() { return "WhatsApp"; }
    @Override public PlatformLimits platformLimits() {
        return PlatformLimits.WHATSAPP;
    }
    // Webhook-driven inbound, Graph API outbound
}

public record WhatsAppConfig(
    String phoneNumberId,        // WhatsApp Business phone number ID
    String accessToken,          // Graph API access token
    String verifyToken,          // Webhook verification token
    String webhookPath,          // Default: /webhook/whatsapp
    String apiVersion,           // Default: v21.0
    String appSecret,            // For webhook signature verification
    boolean verifyWebhook        // Enable HMAC verification
) {}

public class WhatsAppCloudApiClient {
    public DeliveryResult sendText(String to, String text) { ... }
    public DeliveryResult sendMedia(String to, String mediaUrl, String mediaType) { ... }
    public DeliveryResult sendTemplate(String to, String templateName, Map<String, String> params) { ... }
    public DeliveryResult sendInteractive(String to, WhatsAppInteractiveMessage msg) { ... }
    public byte[] downloadMedia(String mediaId) { ... }
}
```

#### Configuration (application.yml)
```yaml
jaiclaw:
  channels:
    whatsapp:
      phone-number-id: ${WHATSAPP_PHONE_NUMBER_ID}
      access-token: ${WHATSAPP_ACCESS_TOKEN}
      verify-token: ${WHATSAPP_VERIFY_TOKEN}
      app-secret: ${WHATSAPP_APP_SECRET}
      verify-webhook: true
```

#### Dependencies
```xml
<parent>
    <artifactId>jaiclaw-channels</artifactId>
</parent>
<dependencies>
    <dependency><artifactId>jaiclaw-channel-api</artifactId></dependency>
    <dependency><artifactId>jaiclaw-gateway</artifactId></dependency>
    <dependency><artifactId>jaiclaw-security</artifactId></dependency>
    <dependency><artifactId>spring-boot-autoconfigure</artifactId><optional>true</optional></dependency>
    <dependency><artifactId>spring-web</artifactId></dependency>
    <dependency><artifactId>jackson-databind</artifactId></dependency>
</dependencies>
```

#### Starter
New `jaiclaw-starters/jaiclaw-starter-whatsapp/pom.xml` aggregating the channel + config.

---

### 1.4 Image Generation

**Location:** New module `extensions/jaiclaw-image-generation/`
**Effort:** Medium (1–2 weeks)

#### Problem
OpenClaw has a full image generation provider registry with capability matching (size, aspect ratio, resolution, quality). JaiClaw has no image generation support.

#### Design

Create an image generation SPI with provider registry, capability matching, and a built-in OpenAI DALL-E provider. Use Spring AI's `ImageModel` where available, with a JaiClaw-native SPI for non-Spring AI providers (fal.ai, etc.).

#### Package Structure
```
extensions/jaiclaw-image-generation/
  pom.xml
  src/main/java/io/jaiclaw/image/
    ImageGenerationProvider.java        # SPI interface
    ImageGenerationRegistry.java        # Provider registry
    ImageGenerationService.java         # Facade with fallback
    ImageGenerationRequest.java         # Request record
    ImageGenerationResult.java          # Result record
    ImageCapabilities.java              # Capability descriptor
    config/
      ImageGenerationConfig.java        # Configuration record
      ImageGenerationAutoConfiguration.java
    provider/
      OpenAiImageProvider.java          # OpenAI DALL-E provider
      SpringAiImageBridge.java          # Bridge: Spring AI ImageModel → SPI
    tool/
      ImageGenerationTool.java          # ToolCallback for LLM tool use
```

#### Key Classes

```java
public interface ImageGenerationProvider {
    String providerId();
    List<String> supportedModels();
    ImageCapabilities capabilities();
    CompletableFuture<ImageGenerationResult> generate(ImageGenerationRequest request);
    default boolean isConfigured() { return true; }
}

public record ImageCapabilities(
    boolean supportsSize,
    boolean supportsAspectRatio,
    List<String> sizes,              // e.g., ["1024x1024", "1024x1536"]
    List<String> aspectRatios,       // e.g., ["1:1", "16:9"]
    List<String> qualities,          // e.g., ["low", "medium", "high"]
    List<String> formats,            // e.g., ["png", "jpeg", "webp"]
    boolean supportsEdit,
    int maxCount
) {}

public record ImageGenerationRequest(
    String prompt,
    String model,                    // Optional override
    String size,                     // e.g., "1024x1024"
    String aspectRatio,              // e.g., "16:9"
    String quality,                  // e.g., "high"
    String format,                   // e.g., "png"
    int count                        // Number of images
) {}

public record ImageGenerationResult(
    List<GeneratedImage> images,
    String provider,
    String model,
    Map<String, Object> metadata
) {
    public record GeneratedImage(
        byte[] data,
        String url,
        String mimeType,
        String revisedPrompt
    ) {}
}

public class ImageGenerationRegistry {
    public void register(ImageGenerationProvider provider) { ... }
    public ImageGenerationProvider resolve(String model) { ... }
    public ImageGenerationProvider resolveWithFallback(String model) { ... }
    public List<ImageGenerationProvider> configuredProviders() { ... }
}
```

#### Tool Integration
```java
public class ImageGenerationTool implements ToolCallback {
    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
            .name("generate_image")
            .description("Generate an image from a text prompt")
            .section("media")
            .profiles(Set.of(ToolProfile.FULL))
            .inputSchema(/* JSON Schema for prompt, size, quality, count */)
            .build();
    }
}
```

#### Dependencies
```xml
<dependency><artifactId>jaiclaw-core</artifactId></dependency>
<dependency><artifactId>jaiclaw-tools</artifactId></dependency>
<dependency><artifactId>spring-ai-model</artifactId><optional>true</optional></dependency>
<dependency><artifactId>spring-boot-autoconfigure</artifactId><optional>true</optional></dependency>
```

---

### 1.5 Observability Starter

**Location:** New `jaiclaw-starters/jaiclaw-starter-observability/`
**Effort:** Low (1–3 days)

#### Problem
Spring Boot already has Micrometer + Actuator, but JaiClaw doesn't provide pre-configured observability. OpenClaw has dedicated OTel and Prometheus extensions.

#### Design

Create a starter POM that pulls in the right dependencies and provides auto-configuration for JaiClaw-specific metrics (agent invocations, tool calls, session counts, channel message rates).

#### Package Structure
```
jaiclaw-starters/jaiclaw-starter-observability/
  pom.xml                               # Dependency aggregator

extensions/jaiclaw-observability/
  pom.xml
  src/main/java/io/jaiclaw/observability/
    JaiClawMetrics.java                  # Custom Micrometer metrics
    AgentRuntimeObserver.java            # Hook into AgentRuntime events
    ToolCallObserver.java                # Meter tool call durations
    ChannelMessageObserver.java          # Meter inbound/outbound messages
    config/
      ObservabilityAutoConfiguration.java
      ObservabilityProperties.java
```

#### Key Classes

```java
public class JaiClawMetrics {
    private final MeterRegistry registry;

    // Counters
    public void recordAgentInvocation(String agentId, String channel) { ... }
    public void recordToolCall(String toolName, boolean success, Duration duration) { ... }
    public void recordChannelMessage(String channelId, String direction) { ... }
    public void recordSessionCreated(String agentId) { ... }
    public void recordTokenUsage(String agentId, String model, int inputTokens, int outputTokens) { ... }

    // Gauges
    public void setActiveSessions(String agentId, int count) { ... }
}
```

#### Starter POM Dependencies
```xml
<!-- jaiclaw-starter-observability/pom.xml -->
<dependencies>
    <dependency><artifactId>jaiclaw-observability</artifactId></dependency>
    <dependency><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><artifactId>micrometer-registry-prometheus</artifactId></dependency>
    <dependency><artifactId>micrometer-tracing-bridge-otel</artifactId><optional>true</optional></dependency>
    <dependency><artifactId>opentelemetry-exporter-otlp</artifactId><optional>true</optional></dependency>
</dependencies>
```

#### Integration
Hooks into `HookRunner` events via the plugin system. Registers as a `JaiClawPlugin` that listens to conversation, tool, and channel hooks.

---

## Phase 2 — P2 Core Infrastructure

### 2.1 Web Search Provider Registry

**Location:** New module `extensions/jaiclaw-web-search/`
**Effort:** Medium (1–2 weeks)

#### Problem
JaiClaw has `WebFetchTool` for fetching URLs but no pluggable web search. OpenClaw has 6+ search providers (Brave, DuckDuckGo, Exa, Firecrawl, Tavily, SearXNG).

#### Design

Create a `WebSearchProvider` SPI with a registry, plus initial implementations for Brave and Tavily (highest-value providers). The registry exposes a `web_search` tool to the LLM.

#### Package Structure
```
extensions/jaiclaw-web-search/
  pom.xml
  src/main/java/io/jaiclaw/search/
    WebSearchProvider.java              # SPI interface
    WebSearchRegistry.java             # Provider registry
    WebSearchRequest.java              # Request record
    WebSearchResult.java               # Result record
    WebSearchService.java              # Facade
    provider/
      BraveSearchProvider.java         # Brave Search API
      TavilySearchProvider.java        # Tavily Search API
      DuckDuckGoSearchProvider.java    # DuckDuckGo (no API key needed)
    tool/
      WebSearchTool.java               # ToolCallback
    config/
      WebSearchConfig.java
      WebSearchAutoConfiguration.java
  src/test/groovy/io/jaiclaw/search/
    WebSearchRegistrySpec.groovy
    BraveSearchProviderSpec.groovy
    TavilySearchProviderSpec.groovy
```

#### Key Classes

```java
public interface WebSearchProvider {
    String providerId();
    boolean isConfigured();
    List<WebSearchResult> search(WebSearchRequest request);
}

public record WebSearchRequest(
    String query,
    int maxResults,            // Default: 5
    String language,           // Optional: "en"
    String region,             // Optional: "us"
    Map<String, String> options
) {}

public record WebSearchResult(
    String title,
    String url,
    String snippet,
    String content,            // Full text if available
    double score,              // Relevance score if available
    Map<String, Object> metadata
) {}

public class WebSearchRegistry {
    public void register(WebSearchProvider provider) { ... }
    public WebSearchProvider resolve(String providerId) { ... }
    public WebSearchProvider resolveDefault() { ... }     // First configured provider
    public List<WebSearchProvider> configuredProviders() { ... }
}
```

#### Configuration
```yaml
jaiclaw:
  search:
    default-provider: brave
    brave:
      api-key: ${BRAVE_SEARCH_API_KEY:}
    tavily:
      api-key: ${TAVILY_API_KEY:}
```

#### Dependencies
```xml
<dependency><artifactId>jaiclaw-core</artifactId></dependency>
<dependency><artifactId>jaiclaw-tools</artifactId></dependency>
<dependency><artifactId>spring-web</artifactId></dependency>
<dependency><artifactId>jackson-databind</artifactId></dependency>
```

---

### 2.2 Enhanced Memory

**Location:** Enhance existing `core/jaiclaw-memory/`
**Effort:** Medium (1–2 weeks)

#### Problem
JaiClaw's memory module has basic in-memory and vector store search. OpenClaw adds circuit breakers, multiple search modes (QMD), session-level toggles, and query scoping.

#### Design

Enhance `jaiclaw-memory` with three capabilities: (1) circuit breaker wrapping search calls, (2) configurable search modes, (3) per-session enable/disable toggles.

#### New Classes
```
core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/
  # Existing files enhanced:
  MemorySearchManager.java           # Add search mode parameter
  MemorySearchOptions.java           # Add searchMode, queryScope fields

  # New files:
  circuit/
    MemoryCircuitBreaker.java        # Circuit breaker wrapper
    CircuitBreakerConfig.java        # Config record
    CircuitBreakerState.java         # Enum: CLOSED, OPEN, HALF_OPEN
  mode/
    SearchMode.java                  # Enum: TEXT, VECTOR, SEMANTIC, HYBRID
    QueryScope.java                  # Enum: MESSAGE, RECENT, FULL
  toggle/
    MemoryToggleStore.java           # SPI for per-session enable/disable
    InMemoryToggleStore.java         # In-memory implementation
```

#### Key Classes

```java
public enum SearchMode {
    TEXT,       // Keyword-based (InMemorySearchManager)
    VECTOR,    // Embedding-based (VectorStoreSearchManager)
    SEMANTIC,  // LLM-rewritten query + vector search
    HYBRID     // Text + vector with score fusion
}

public enum QueryScope {
    MESSAGE,   // Current turn only
    RECENT,    // Last N turns (configurable)
    FULL       // Entire conversation
}

public record MemorySearchOptions(
    double minScore,
    int maxResults,
    SearchMode searchMode,     // NEW
    QueryScope queryScope,     // NEW
    String sessionKey          // NEW: for session-scoped toggles
) {
    public static final MemorySearchOptions DEFAULT =
        new MemorySearchOptions(0.0, 10, SearchMode.TEXT, QueryScope.RECENT, null);
}

public class MemoryCircuitBreaker implements MemorySearchManager {
    private final MemorySearchManager delegate;
    private final CircuitBreakerConfig config;
    private final Map<String, CircuitBreakerState> states;  // Keyed by provider+model

    @Override
    public List<MemorySearchResult> search(String query, MemorySearchOptions options) {
        // Check circuit state; if OPEN, return empty; if CLOSED/HALF_OPEN, delegate
    }
}

public record CircuitBreakerConfig(
    int maxConsecutiveFailures,     // Default: 3
    Duration cooldownDuration,      // Default: 60s
    Duration timeoutDuration        // Default: 5s
) {}

public interface MemoryToggleStore {
    boolean isEnabled(String sessionKey);
    void setEnabled(String sessionKey, boolean enabled);
}
```

#### Configuration
```yaml
jaiclaw:
  memory:
    default-search-mode: hybrid
    default-query-scope: recent
    recent-turns: 10
    circuit-breaker:
      enabled: true
      max-failures: 3
      cooldown: 60s
```

---

### 2.3 Memory Wiki

**Location:** New module `extensions/jaiclaw-memory-wiki/`
**Effort:** Medium (1–2 weeks)

#### Problem
No persistent, structured knowledge store. OpenClaw's memory-wiki provides an Obsidian-compatible wiki with categorized pages (synthesis, entity, concept, source, report).

#### Package Structure
```
extensions/jaiclaw-memory-wiki/
  pom.xml
  src/main/java/io/jaiclaw/memory/wiki/
    WikiPage.java                    # Record: id, kind, title, content, claims, metadata
    WikiPageKind.java                # Enum: SYNTHESIS, ENTITY, CONCEPT, SOURCE, REPORT
    WikiStore.java                   # SPI for persistence
    WikiService.java                 # Facade: CRUD, search, compile
    WikiCompiler.java                # Compile wiki into LLM context supplement
    WikiCorpusBuilder.java           # Build corpus for system prompt
    store/
      FileSystemWikiStore.java       # Markdown file storage (Obsidian-compatible)
    tool/
      WikiSearchTool.java            # search_wiki tool
      WikiGetTool.java               # get_wiki_page tool
      WikiUpdateTool.java            # update_wiki_page tool
      WikiStatusTool.java            # wiki_status tool
    config/
      WikiConfig.java
      WikiAutoConfiguration.java
  src/test/groovy/io/jaiclaw/memory/wiki/
    WikiServiceSpec.groovy
    FileSystemWikiStoreSpec.groovy
    WikiCompilerSpec.groovy
```

#### Key Classes

```java
public record WikiPage(
    String id,
    WikiPageKind kind,
    String title,
    String content,
    List<String> claims,
    List<String> questions,
    List<String> contradictions,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}

public enum WikiPageKind {
    SYNTHESIS, ENTITY, CONCEPT, SOURCE, REPORT
}

public interface WikiStore {
    WikiPage save(WikiPage page);
    Optional<WikiPage> findById(String id);
    List<WikiPage> findByKind(WikiPageKind kind);
    List<WikiPage> search(String query, int maxResults);
    WikiPalaceStatus status();
    void delete(String id);
}

public record WikiPalaceStatus(
    int totalPages,
    Map<WikiPageKind, Integer> pageCounts,
    int totalClaims,
    int totalQuestions,
    int totalContradictions
) {}
```

#### Storage Layout (Obsidian-compatible)
```
{vault-path}/
  synthesis/
    topic-name.md
  entity/
    person-name.md
  concept/
    concept-name.md
  source/
    source-ref.md
  report/
    report-name.md
```

Each `.md` file has YAML frontmatter:
```yaml
---
id: unique-id
kind: entity
claims: [...]
questions: [...]
contradictions: [...]
created: 2026-05-28T10:00:00Z
updated: 2026-05-28T12:00:00Z
---
# Title
Content here...
```

#### Configuration
```yaml
jaiclaw:
  memory:
    wiki:
      vault-path: ${JAICLAW_WIKI_VAULT:./wiki}
      max-corpus-tokens: 2000
```

---

### 2.4 Model Catalog

**Location:** New module `core/jaiclaw-model-catalog/` (or `extensions/jaiclaw-model-catalog/`)
**Effort:** Medium (1–2 weeks)

#### Problem
No centralized model metadata registry. OpenClaw tracks model capabilities, costs, context windows, status, and supports alias resolution.

#### Package Structure
```
extensions/jaiclaw-model-catalog/
  pom.xml
  src/main/java/io/jaiclaw/model/catalog/
    ModelCatalogEntry.java           # Record: full model metadata
    ModelCatalog.java                # Registry + lookup
    ModelKind.java                   # Enum: TEXT, IMAGE, VIDEO, MUSIC, EMBEDDING
    ModelStatus.java                 # Enum: AVAILABLE, PREVIEW, DEPRECATED, DISABLED
    ModelCost.java                   # Record: input/output per-token costs
    ModelCapabilities.java           # Record: capability flags
    ModelSource.java                 # Enum: STATIC, CONFIGURED, RUNTIME
    loader/
      StaticModelLoader.java         # Load from YAML/JSON resource files
      SpringAiModelLoader.java       # Discover from Spring AI ChatModel beans
    tool/
      ModelListTool.java             # list_models tool
      ModelInfoTool.java             # model_info tool
    config/
      ModelCatalogConfig.java
      ModelCatalogAutoConfiguration.java
  src/main/resources/
    models/
      anthropic.yml                  # Static model definitions
      openai.yml
      google.yml
      ollama.yml
```

#### Key Classes

```java
public record ModelCatalogEntry(
    String id,                       // e.g., "claude-sonnet-4-5"
    String provider,                 // e.g., "anthropic"
    String displayName,              // e.g., "Claude Sonnet 4.5"
    ModelKind kind,
    ModelStatus status,
    ModelCost cost,
    ModelCapabilities capabilities,
    int contextWindow,
    int maxOutputTokens,
    List<String> aliases,            // e.g., ["claude-3-5-sonnet", "sonnet"]
    List<String> tags,
    ModelSource source,
    Map<String, Object> metadata
) {}

public record ModelCost(
    double inputPerMillion,          // USD per 1M input tokens
    double outputPerMillion,         // USD per 1M output tokens
    double cacheReadPerMillion,
    double cacheWritePerMillion
) {}

public record ModelCapabilities(
    boolean supportsToolUse,
    boolean supportsVision,
    boolean supportsStreaming,
    boolean supportsThinking,
    List<String> inputTypes          // e.g., ["text", "image", "document"]
) {}

public class ModelCatalog {
    public void register(ModelCatalogEntry entry) { ... }
    public Optional<ModelCatalogEntry> resolve(String modelIdOrAlias) { ... }
    public List<ModelCatalogEntry> byProvider(String provider) { ... }
    public List<ModelCatalogEntry> byKind(ModelKind kind) { ... }
    public List<ModelCatalogEntry> available() { ... }
}
```

#### Dependencies
```xml
<dependency><artifactId>jaiclaw-core</artifactId></dependency>
<dependency><artifactId>jaiclaw-tools</artifactId><optional>true</optional></dependency>
<dependency><artifactId>snakeyaml</artifactId></dependency>
```

---

### 2.5 Plugin State Store

**Location:** Enhance `core/jaiclaw-plugin-sdk/`
**Effort:** Low (1–3 days)

#### Problem
Plugins have no persistent key-value storage. OpenClaw provides a TTL-based SQLite store with namespaced entries.

#### Design

Add a `PluginStateStore` SPI to the plugin SDK with an in-memory default and a JPA-backed implementation in a new extension.

#### New Classes
```
core/jaiclaw-plugin-sdk/src/main/java/io/jaiclaw/plugin/state/
  PluginStateStore.java              # SPI interface
  PluginStateEntry.java              # Record: key, value, createdAt, expiresAt
  InMemoryPluginStateStore.java      # Default in-memory implementation
```

#### Key Classes

```java
public interface PluginStateStore {
    <T> void put(String namespace, String key, T value, Duration ttl);
    <T> void put(String namespace, String key, T value);  // No TTL
    <T> Optional<T> get(String namespace, String key, Class<T> type);
    <T> Optional<T> consume(String namespace, String key, Class<T> type); // Atomic get+delete
    boolean delete(String namespace, String key);
    List<PluginStateEntry<?>> entries(String namespace);
    void clear(String namespace);
}

public record PluginStateEntry<T>(
    String key,
    T value,
    Instant createdAt,
    Instant expiresAt      // Nullable if no TTL
) {}
```

#### PluginApi Enhancement
```java
public interface PluginApi {
    // ... existing methods ...
    PluginStateStore stateStore();  // NEW: access namespaced state store
}
```

The `PluginApi` implementation auto-namespaces by plugin ID.

---

### 2.6 Token Compression

**Location:** New module `extensions/jaiclaw-token-compression/`
**Effort:** Medium (1–2 weeks)

#### Problem
Tool results (especially shell execution, web fetch, code output) can be very large, consuming excessive context window budget. OpenClaw's tokenjuice compacts these results.

#### Package Structure
```
extensions/jaiclaw-token-compression/
  pom.xml
  src/main/java/io/jaiclaw/compression/
    ToolResultCompressor.java           # SPI interface
    CompositeCompressor.java            # Delegates to per-tool compressors
    CompressorRegistry.java             # Registry
    compressor/
      ShellResultCompressor.java        # Compress shell/exec output
      WebFetchResultCompressor.java     # Compress web fetch results
      CodeResultCompressor.java         # Compress code execution output
      GenericTruncationCompressor.java  # Fallback: truncate with summary
    config/
      TokenCompressionConfig.java
      TokenCompressionAutoConfiguration.java
```

#### Key Classes

```java
public interface ToolResultCompressor {
    /** Which tool names this compressor handles. */
    Set<String> supportedTools();

    /** Compress a tool result, returning the compressed version. */
    ToolResult compress(String toolName, Map<String, Object> args,
                        ToolResult result, CompressionContext context);
}

public record CompressionContext(
    int maxTokenBudget,            // Target token count for result
    int estimatedCurrentTokens,    // Estimated current token count
    String agentId
) {}

public class CompressorRegistry {
    public void register(ToolResultCompressor compressor) { ... }
    public ToolResult compress(String toolName, Map<String, Object> args,
                               ToolResult result, CompressionContext context) { ... }
}
```

#### Integration
Wire into `AgentRuntime` as a post-processing step after tool execution and before adding the result to the conversation. Register via `HookRunner` on a `TOOL_RESULT_RECEIVED` hook.

---

## Phase 3 — P2 Agent & Orchestration

### 3.1 Task Executor & Flow Engine

**Location:** New module `extensions/jaiclaw-tasks/`
**Effort:** High (2+ weeks)

#### Problem
No task lifecycle management. OpenClaw has SQLite-backed task execution with flow registration, ownership tracking, and delivery state.

#### Package Structure
```
extensions/jaiclaw-tasks/
  pom.xml
  src/main/java/io/jaiclaw/tasks/
    TaskRecord.java                  # Record: id, flowId, ownerKey, status, timestamps
    TaskStatus.java                  # Enum: QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED
    TaskDeliveryState.java           # Enum: PENDING, SENT, ACKNOWLEDGED
    TaskFlow.java                    # Record: flowId, status, ownerKey, linkedTaskIds
    TaskFlowStatus.java              # Enum: ACTIVE, BLOCKED, RUNNING, SUCCEEDED, FAILED, CANCELLED
    TaskExecutor.java                # Execute tasks with lifecycle management
    TaskRegistry.java                # SPI for task persistence
    FlowRegistry.java               # SPI for flow persistence
    store/
      InMemoryTaskRegistry.java      # In-memory implementation
      JpaTaskRegistry.java           # JPA/H2 implementation
      TaskEntity.java                # JPA entity
      TaskFlowEntity.java            # JPA entity
      TaskRepository.java            # Spring Data JPA repository
    tool/
      CreateTaskTool.java            # LLM tool: create task
      ListTasksTool.java             # LLM tool: list tasks
      UpdateTaskTool.java            # LLM tool: update task status
      TaskFlowTool.java              # LLM tool: manage flows
    config/
      TasksConfig.java
      TasksAutoConfiguration.java
```

#### Key Classes

```java
public record TaskRecord(
    String id,
    String flowId,
    String parentFlowId,
    String ownerKey,               // Session key: {agentId}:{channel}:{accountId}:{peerId}
    TaskStatus status,
    TaskDeliveryState deliveryState,
    String description,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}

public interface TaskRegistry {
    TaskRecord create(TaskRecord task);
    Optional<TaskRecord> findById(String id);
    List<TaskRecord> findByOwnerKey(String ownerKey);
    List<TaskRecord> findByFlowId(String flowId);
    List<TaskRecord> findByStatus(TaskStatus status);
    TaskRecord updateStatus(String id, TaskStatus status);
    void delete(String id);
}

public class TaskExecutor {
    /** Execute a task, updating status through its lifecycle. */
    public CompletableFuture<TaskRecord> execute(String taskId, Runnable work) { ... }

    /** Cancel a running task. */
    public boolean cancel(String taskId) { ... }
}
```

#### Multi-Tenancy
- `TaskRegistry` implementations filter by tenant when `TenantGuard.isMultiTenant()`
- `ownerKey` includes session key which embeds tenant context
- JPA entity includes `tenantId` column

---

### 3.2 Thread Ownership

**Location:** Enhance `core/jaiclaw-agent/`
**Effort:** Medium (1–2 weeks)

#### Problem
No @-mention tracking. When multiple agents share a channel, there's no routing based on which agent was mentioned.

#### New Classes
```
core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/ownership/
  ThreadOwnershipTracker.java       # Track @-mentions per thread
  MentionDetector.java              # Detect agent name mentions in text
  OwnershipEntry.java               # Record: threadKey, agentId, timestamp, ttl
  ThreadOwnershipConfig.java        # Configuration record
```

#### Key Classes

```java
public class ThreadOwnershipTracker {
    private final Map<String, OwnershipEntry> ownership = new ConcurrentHashMap<>();
    private final Duration mentionTtl;     // Default: 5 minutes

    /** Record a mention of an agent in a thread. */
    public void recordMention(String threadKey, String agentId) { ... }

    /** Check if a specific agent owns a thread. */
    public boolean isOwner(String threadKey, String agentId) { ... }

    /** Resolve which agent should handle a message in a thread. */
    public Optional<String> resolveOwner(String threadKey) { ... }
}

public final class MentionDetector {
    /** Check if text contains @agentName mention. */
    public static boolean containsMention(String text, String agentName) {
        // Pattern: (^|[^\w])@{name}($|[^\w]) — case-insensitive
    }

    /** Extract all @-mentions from text. */
    public static List<String> extractMentions(String text) { ... }
}
```

#### Integration
Hook into `GatewayService` message routing. Before dispatching to an agent, check `ThreadOwnershipTracker`. If a mention is detected, route to the mentioned agent and update ownership.

---

### 3.3 Webhook Event Routing

**Location:** New module `extensions/jaiclaw-webhooks/`
**Effort:** Medium (1–2 weeks)

#### Package Structure
```
extensions/jaiclaw-webhooks/
  pom.xml
  src/main/java/io/jaiclaw/webhooks/
    WebhookRoute.java                # Record: routeId, path, secret, targetFlow
    WebhookRegistry.java             # Route registry
    WebhookController.java           # Spring MVC dynamic endpoint
    WebhookAuthenticator.java        # HMAC signature verification
    config/
      WebhookConfig.java
      WebhookAutoConfiguration.java
```

#### Key Classes

```java
public record WebhookRoute(
    String routeId,
    String path,                     // e.g., "/webhooks/github"
    String secretConfigPath,         // Path to secret in config
    String targetFlowId,             // Optional: route to specific TaskFlow
    String targetAgentId,            // Optional: route to specific agent
    WebhookAuthType authType         // NONE, HMAC_SHA256, BEARER_TOKEN
) {}

public enum WebhookAuthType {
    NONE, HMAC_SHA256, BEARER_TOKEN
}

public class WebhookRegistry {
    public void register(WebhookRoute route) { ... }
    public Optional<WebhookRoute> resolve(String path) { ... }
    public List<WebhookRoute> allRoutes() { ... }
}
```

#### Configuration
```yaml
jaiclaw:
  webhooks:
    routes:
      - route-id: github-events
        path: /webhooks/github
        auth-type: hmac-sha256
        secret: ${GITHUB_WEBHOOK_SECRET}
        target-agent-id: default
```

#### Dependencies
Optionally depends on `jaiclaw-tasks` for flow routing.

---

### 3.4 Admin HTTP RPC

**Location:** Enhance `core/jaiclaw-gateway/`
**Effort:** Low (1–3 days)

#### Problem
No admin API for runtime inspection/management. OpenClaw exposes gateway RPC over HTTP for trusted operators.

#### New Classes
```
core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/admin/
  AdminController.java              # Spring MVC REST controller
  AdminSecurity.java                # API key or JWT auth for admin
```

#### Key Endpoints

```java
@RestController
@RequestMapping("/admin")
public class AdminController {
    @GetMapping("/sessions")          // List active sessions
    @GetMapping("/sessions/{key}")    // Session details
    @DeleteMapping("/sessions/{key}") // Terminate session
    @GetMapping("/channels")          // Channel adapter status
    @PostMapping("/channels/{id}/restart")  // Restart channel
    @GetMapping("/agents")            // List agents
    @GetMapping("/tools")             // List registered tools
    @GetMapping("/plugins")           // List plugins
    @GetMapping("/metrics")           // Runtime metrics summary
    @PostMapping("/broadcast")        // Send message to all sessions
}
```

#### Security
Protected by `jaiclaw.admin.api-key` or JWT with admin role. Uses `@ConditionalOnProperty("jaiclaw.admin.enabled", havingValue = "true")`.

---

## Phase 4 — P2 Channels

All new channels follow the same pattern: implement `ChannelAdapter`, create a `*Config` record, add auto-configuration, create a starter POM.

### 4.1 Line Channel

**Location:** New module `channels/jaiclaw-channel-line/`
**Effort:** Medium (1–2 weeks)

#### Package Structure
```
channels/jaiclaw-channel-line/
  pom.xml
  src/main/java/io/jaiclaw/channel/line/
    LineAdapter.java                 # ChannelAdapter implementation
    LineConfig.java                  # Configuration record
    LineWebhookController.java       # Webhook endpoint
    LineApiClient.java               # LINE Messaging API client
    LineMessageMapper.java           # Platform ↔ ChannelMessage mapping
```

#### Key Config
```java
public record LineConfig(
    String channelAccessToken,
    String channelSecret,
    String webhookPath               // Default: /webhook/line
) {}
```

#### LINE Messaging API
- Webhook verification via `X-Line-Signature` (HMAC-SHA256)
- Reply API: `POST https://api.line.me/v2/bot/message/reply`
- Push API: `POST https://api.line.me/v2/bot/message/push`
- Supports text, image, video, audio, sticker, location message types

---

### 4.2 Matrix Channel

**Location:** New module `channels/jaiclaw-channel-matrix/`
**Effort:** Medium (1–2 weeks)

#### Package Structure
```
channels/jaiclaw-channel-matrix/
  pom.xml
  src/main/java/io/jaiclaw/channel/matrix/
    MatrixAdapter.java               # ChannelAdapter implementation
    MatrixConfig.java                # Configuration record
    MatrixSyncClient.java            # Matrix Client-Server API (long-poll sync)
    MatrixMessageMapper.java
```

#### Key Config
```java
public record MatrixConfig(
    String homeserverUrl,            // e.g., https://matrix.org
    String accessToken,
    String userId,                   // e.g., @bot:matrix.org
    String deviceId,
    long syncTimeoutMs               // Default: 30000
) {}
```

#### Matrix Client-Server API
- Long-poll sync: `GET /_matrix/client/v3/sync?timeout=30000`
- Send message: `PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}`
- Supports `m.text`, `m.image`, `m.file`, `m.notice` message types

---

### 4.3 Google Chat Channel

**Location:** New module `channels/jaiclaw-channel-googlechat/`
**Effort:** Medium (1–2 weeks)

#### Package Structure
```
channels/jaiclaw-channel-googlechat/
  pom.xml
  src/main/java/io/jaiclaw/channel/googlechat/
    GoogleChatAdapter.java           # ChannelAdapter implementation
    GoogleChatConfig.java            # Configuration record
    GoogleChatWebhookController.java # Pub/Sub or HTTP webhook
    GoogleChatApiClient.java         # Google Chat API client
    GoogleChatMessageMapper.java
```

#### Key Config
```java
public record GoogleChatConfig(
    String projectId,
    String serviceAccountKeyPath,    // Path to SA key JSON
    String webhookPath               // Default: /webhook/googlechat
) {}
```

---

## Phase 5 — P2 Media & Voice

### 5.1 Video Generation

**Location:** New module `extensions/jaiclaw-video-generation/`
**Effort:** High (2+ weeks)

#### Package Structure
```
extensions/jaiclaw-video-generation/
  pom.xml
  src/main/java/io/jaiclaw/video/
    VideoGenerationProvider.java     # SPI interface
    VideoGenerationRegistry.java     # Provider registry
    VideoGenerationRequest.java      # Request record
    VideoGenerationResult.java       # Result record
    VideoCapabilities.java           # Capability descriptor
    provider/
      RunwayVideoProvider.java       # Runway ML API
    tool/
      VideoGenerationTool.java       # ToolCallback
    config/
      VideoGenerationConfig.java
      VideoGenerationAutoConfiguration.java
```

#### Key Classes

```java
public interface VideoGenerationProvider {
    String providerId();
    List<String> supportedModels();
    VideoCapabilities capabilities();
    CompletableFuture<VideoGenerationResult> generate(VideoGenerationRequest request);
    CompletableFuture<VideoGenerationResult> pollStatus(String jobId);  // Async polling
    boolean isConfigured();
}

public record VideoGenerationRequest(
    String prompt,
    String model,
    int durationSeconds,             // Default: 4
    String aspectRatio,              // e.g., "16:9"
    String resolution,               // e.g., "1080p"
    byte[] inputImage,               // Optional: image-to-video
    Map<String, Object> options
) {}

public record VideoGenerationResult(
    String jobId,
    VideoJobStatus status,           // QUEUED, PROCESSING, COMPLETED, FAILED
    String videoUrl,
    byte[] videoData,
    int durationSeconds,
    String provider,
    String model,
    Map<String, Object> metadata
) {}

public enum VideoJobStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED
}
```

---

### 5.2 ElevenLabs TTS Provider

**Location:** Enhance `extensions/jaiclaw-voice/`
**Effort:** Low (1–3 days)

#### New Classes
```
extensions/jaiclaw-voice/src/main/java/io/jaiclaw/voice/tts/
  ElevenLabsTtsProvider.java         # ElevenLabs REST API client
  ElevenLabsConfig.java              # Configuration record
```

#### Key Class

```java
public class ElevenLabsTtsProvider implements TtsProvider {
    @Override public String providerId() { return "elevenlabs"; }

    @Override
    public AudioResult synthesize(String text, String voice, Map<String, String> options) {
        // POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}
        // Returns audio/mpeg stream
    }
}
```

#### Configuration
```yaml
jaiclaw:
  voice:
    tts:
      provider: elevenlabs
      elevenlabs:
        api-key: ${ELEVENLABS_API_KEY}
        default-voice-id: 21m00Tcm4TlvDq8ikWAM
        model-id: eleven_multilingual_v2
```

---

### 5.3 Deepgram STT Provider

**Location:** Enhance `extensions/jaiclaw-voice/`
**Effort:** Low (1–3 days)

#### New Classes
```
extensions/jaiclaw-voice/src/main/java/io/jaiclaw/voice/stt/
  DeepgramSttProvider.java           # Deepgram REST API client
  DeepgramConfig.java                # Configuration record
```

#### Key Class

```java
public class DeepgramSttProvider implements SttProvider {
    @Override public String providerId() { return "deepgram"; }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String mimeType,
                                          Map<String, String> options) {
        // POST https://api.deepgram.com/v1/listen
        // With audio in request body
    }
}
```

---

## Phase 6 — P2 Observability & Knowledge

### 6.1 Trajectory Tracking

**Location:** New module `extensions/jaiclaw-trajectory/`
**Effort:** Medium (1–2 weeks)

#### Package Structure
```
extensions/jaiclaw-trajectory/
  pom.xml
  src/main/java/io/jaiclaw/trajectory/
    TrajectoryEvent.java             # Record: trace event
    TrajectoryEventType.java         # Enum: AGENT_START, TOOL_CALL, MODEL_REQUEST, etc.
    TrajectoryRecorder.java          # Record events to storage
    TrajectoryStore.java             # SPI for persistence
    TrajectoryBundle.java            # Bundle with manifest
    store/
      FileTrajectoryStore.java       # JSON-lines file storage
    config/
      TrajectoryConfig.java
      TrajectoryAutoConfiguration.java
```

#### Key Classes

```java
public record TrajectoryEvent(
    String traceId,
    int sequence,
    TrajectoryEventType type,
    String sessionId,
    String runId,
    String provider,
    String model,
    Instant timestamp,
    Map<String, Object> data
) {}

public enum TrajectoryEventType {
    AGENT_START, AGENT_END,
    TOOL_CALL, TOOL_RESULT,
    MODEL_REQUEST, MODEL_RESPONSE,
    SESSION_CREATED, SESSION_ENDED,
    ERROR
}

public interface TrajectoryStore {
    void record(TrajectoryEvent event);
    List<TrajectoryEvent> findByTraceId(String traceId);
    List<TrajectoryEvent> findBySessionId(String sessionId);
    TrajectoryBundle bundle(String traceId);
}

public record TrajectoryBundle(
    String traceId,
    String sessionKey,
    int eventCount,
    Instant generatedAt,
    List<TrajectoryEvent> events
) {}
```

#### Integration
Register as a `JaiClawPlugin` hooking into `CONVERSATION_STARTED`, `TOOL_CALLED`, `MODEL_INVOKED`, and `CONVERSATION_ENDED` hooks.

---

### 6.2 Transcript Storage

**Location:** New module `extensions/jaiclaw-transcripts/`
**Effort:** Low (1–3 days)

#### Package Structure
```
extensions/jaiclaw-transcripts/
  pom.xml
  src/main/java/io/jaiclaw/transcripts/
    TranscriptSession.java           # Record: session metadata
    TranscriptUtterance.java         # Record: single utterance
    TranscriptStore.java             # SPI for persistence
    TranscriptSummaryRenderer.java   # Render to Markdown
    store/
      FileTranscriptStore.java       # JSON file storage by date/session
    config/
      TranscriptConfig.java
      TranscriptAutoConfiguration.java
```

#### Key Classes

```java
public record TranscriptSession(
    String sessionId,
    String title,
    String source,
    Instant startedAt,
    Instant stoppedAt
) {}

public record TranscriptUtterance(
    String id,
    String sessionId,
    String speaker,
    String text,
    boolean isFinal,
    Instant timestamp,
    Map<String, Object> metadata
) {}

public interface TranscriptStore {
    void startSession(TranscriptSession session);
    void addUtterance(TranscriptUtterance utterance);
    void endSession(String sessionId);
    Optional<TranscriptSession> findSession(String sessionId);
    List<TranscriptUtterance> getUtterances(String sessionId);
    String renderMarkdown(String sessionId);
}
```

#### File Storage Layout
```
{transcripts-dir}/
  2026-05-28/
    {sessionId}/
      session.json
      utterances.jsonl
```

---

### 6.3 Internationalization (i18n)

**Location:** Enhance `core/jaiclaw-core/` + new resource bundles
**Effort:** Medium (1–2 weeks)

#### Design

Use Spring's `MessageSource` with resource bundles. System prompts, tool descriptions, error messages, and status text are externalized.

#### New Classes
```
core/jaiclaw-core/src/main/java/io/jaiclaw/core/i18n/
  JaiClawMessages.java              # Typed message accessor
  JaiClawLocale.java                # Supported locale registry

core/jaiclaw-core/src/main/resources/
  i18n/
    messages.properties              # English (default)
    messages_zh_CN.properties        # Chinese Simplified
    messages_es.properties           # Spanish
    messages_pt_BR.properties        # Portuguese (Brazil)
    messages_de.properties           # German
    messages_fr.properties           # French
    messages_ja.properties           # Japanese
    messages_ko.properties           # Korean
    messages_ar.properties           # Arabic
    messages_tr.properties           # Turkish
```

#### Key Classes

```java
public enum JaiClawLocale {
    EN("en", "English"),
    ZH_CN("zh-CN", "Chinese Simplified"),
    ES("es", "Spanish"),
    PT_BR("pt-BR", "Portuguese"),
    DE("de", "German"),
    FR("fr", "French"),
    JA("ja", "Japanese"),
    KO("ko", "Korean"),
    AR("ar", "Arabic"),
    TR("tr", "Turkish");

    private final String code;
    private final String displayName;
}

public class JaiClawMessages {
    private final MessageSource messageSource;

    public String toolDescription(String toolName, Locale locale) { ... }
    public String errorMessage(String errorCode, Locale locale, Object... args) { ... }
    public String statusText(String key, Locale locale) { ... }
    public String systemPromptSection(String section, Locale locale) { ... }
}
```

#### Configuration
```yaml
jaiclaw:
  i18n:
    default-locale: en
    fallback-locale: en
```

---

### 6.4 Secrets Management

**Location:** New module `extensions/jaiclaw-secrets/`
**Effort:** Medium (1–2 weeks)

#### Package Structure
```
extensions/jaiclaw-secrets/
  pom.xml
  src/main/java/io/jaiclaw/secrets/
    SecretStore.java                 # SPI interface
    SecretReference.java             # Record: secret key + metadata
    SecretAuditEntry.java            # Record: who accessed what when
    store/
      EnvironmentSecretStore.java    # Read from env vars (default)
      EncryptedFileSecretStore.java  # AES-encrypted JSON file
      VaultSecretStore.java          # Spring Vault integration
    config/
      SecretsConfig.java
      SecretsAutoConfiguration.java
```

#### Key Classes

```java
public interface SecretStore {
    Optional<String> resolve(String key);
    void store(String key, String value);
    void delete(String key);
    List<SecretReference> list();     // Keys only, no values
    boolean exists(String key);
}

public record SecretReference(
    String key,
    String source,                   // "env", "file", "vault"
    Instant lastAccessed,
    boolean isExpiring
) {}
```

#### Configuration
```yaml
jaiclaw:
  secrets:
    store: env                       # env | file | vault
    file:
      path: ${JAICLAW_SECRETS_FILE:./secrets.enc}
      encryption-key: ${JAICLAW_SECRETS_KEY}
    vault:
      uri: ${VAULT_URI:http://localhost:8200}
      token: ${VAULT_TOKEN}
      path: secret/jaiclaw
```

---

## Dependency Graph

This shows which items should be built in order due to dependencies:

```
Phase 1 (no dependencies, all can start in parallel):
  1.1 Auto-Reply Chunking          ──► used by 1.3 WhatsApp
  1.2 Web Readability
  1.4 Image Generation
  1.5 Observability Starter

Phase 1 (depends on 1.1):
  1.3 WhatsApp Channel             ◄── uses PlatformLimits from 1.1

Phase 2 (depends on Phase 1 completion):
  2.1 Web Search Provider Registry
  2.2 Enhanced Memory
  2.3 Memory Wiki                  ◄── builds on 2.2 concepts
  2.4 Model Catalog
  2.5 Plugin State Store
  2.6 Token Compression

Phase 3 (depends on Phase 2):
  3.1 Task Executor                ◄── foundation for 3.3
  3.2 Thread Ownership
  3.3 Webhook Event Routing        ◄── optionally routes to TaskFlow (3.1)
  3.4 Admin HTTP RPC

Phase 4 (independent, can overlap with Phase 3):
  4.1 Line Channel
  4.2 Matrix Channel
  4.3 Google Chat Channel

Phase 5 (independent, can overlap with Phase 3-4):
  5.1 Video Generation             ◄── follows pattern from 1.4 Image Generation
  5.2 ElevenLabs TTS
  5.3 Deepgram STT

Phase 6 (depends on Phase 2 for Plugin State):
  6.1 Trajectory Tracking
  6.2 Transcript Storage
  6.3 i18n
  6.4 Secrets Management
```

### Critical Path
```
1.1 → 1.3 → (WhatsApp operational)
1.4 → 5.1 → (Full media generation)
2.2 → 2.3 → (Full memory system)
2.5 → 3.1 → 3.3 → (Full orchestration)
```

### Module Count Impact
- Current: 10 core + 7 channels + 27 extensions + 5 tools + 3 apps = **52 modules**
- After roadmap: 11 core + 10 channels + 38 extensions + 5 tools + 3 apps = **67 modules** (+15)

### New Starters Needed
```
jaiclaw-starters/
  jaiclaw-starter-whatsapp/
  jaiclaw-starter-line/
  jaiclaw-starter-matrix/
  jaiclaw-starter-googlechat/
  jaiclaw-starter-image-generation/
  jaiclaw-starter-video-generation/
  jaiclaw-starter-web-search/
  jaiclaw-starter-tasks/
  jaiclaw-starter-webhooks/
  jaiclaw-starter-trajectory/
  jaiclaw-starter-transcripts/
  jaiclaw-starter-secrets/
```
