# Utilize Existing Libraries — Build vs Leverage Analysis

**Created:** 2026-05-28
**Purpose:** Before implementing any roadmap item, identify what existing Java/Spring/Camel libraries already provide so we avoid rebuilding the wheel.

## Executive Summary

Of the 21 roadmap items across all 6 phases, **11 are primarily "add dependency + thin wrapper"** and **only 6 require meaningful custom implementation**. The JaiClaw ecosystem already includes Apache Camel 4.18.1 (390+ connectors), Spring AI 1.1.7 (image/audio models), Spring Boot 3.5.14 (actuator, cache, i18n), Resilience4j (via Spring Cloud), and Project Reactor — covering the majority of planned features.

### Effort Classification

| Category | Count | Items |
|----------|-------|-------|
| **Just add dependency** (zero/trivial code) | 7 | Image Gen, Observability, Plugin State, Token Counting, ElevenLabs, i18n, Secrets |
| **Thin wrapper** (~50-150 lines) | 4 | Web Readability, WhatsApp, Enhanced Memory, Transcript Storage |
| **SPI + provider impls** (~200-400 lines) | 4 | Web Search, Webhook Routing, Deepgram, Trajectory |
| **Medium custom work** (~300-500 lines) | 4 | Auto-Reply Chunking, Memory Wiki, Model Catalog, Thread Ownership |
| **Significant custom work** | 2 | Task Executor, Video Generation |

**Total estimated new code (excluding tests):** ~4,000-5,500 lines — roughly 60% less than the original roadmap assumed.

---

## Detailed Analysis by Roadmap Item

---

### 1.1 Auto-Reply Chunking

| | |
|---|---|
| **Library coverage** | None — domain-specific |
| **Verdict** | **Custom implementation** |

**Why no library helps:** Camel's Splitter EIP and Spring Integration's Splitter handle generic data pipeline decomposition, not platform-aware text chunking that preserves Markdown formatting, code blocks, and respects per-platform character limits (Telegram: 4096, Discord: 2000, Slack: 40000, SMS: 160).

**What to build:** A `MessageChunker` utility in `jaiclaw-channel-api` that splits text at paragraph/sentence boundaries while preserving formatting. Each `ChannelAdapter` declares its `PlatformLimits`. Pure string manipulation — no external dependencies.

**Estimated code:** ~200-250 lines (chunker + strategy implementations)

---

### 1.2 Web Readability Extraction

| | |
|---|---|
| **Library** | **Readability4J** (`net.dankito.readability4j:readability4j:1.0.8`) |
| **Coverage** | ~95% |
| **Verdict** | **Just wrap the library** |

**What it provides:** Direct port of Mozilla's Readability.js (Firefox Reader View). Extracts article title, byline, content (HTML + plain text), and excerpt. Uses Jsoup internally.

**What to build:** ~20 lines. Call `Readability4J(url, html).parse()`, return `article.getTextContent()`. Add as optional post-processing in `WebFetchTool` with an `extractArticle` parameter.

```xml
<dependency>
    <groupId>net.dankito.readability4j</groupId>
    <artifactId>readability4j</artifactId>
    <version>1.0.8</version>
</dependency>
```

---

### 1.3 WhatsApp Channel

| | |
|---|---|
| **Library** | **camel-whatsapp** (`org.apache.camel.springboot:camel-whatsapp-starter:4.18.1`) |
| **Coverage** | ~75% |
| **Verdict** | **Camel does the heavy lifting** |

**What it provides:** The Camel WhatsApp component supports WhatsApp Cloud API:
- **Producer:** Send text, template, media, location, contact messages
- **Consumer (webhook):** Receive inbound messages via `camel-webhook`

**What to build:** JaiClaw already has `CamelChannelAdapter` that bridges any Camel route to a JaiClaw channel via SEDA queues. For WhatsApp:
1. Add `camel-whatsapp-starter` dependency
2. Configure a Camel route: `from("whatsapp:phoneNumberId")` → SEDA inbound queue
3. Create outbound bridge route
4. Extend `CamelMessageConverter` for WhatsApp-specific message types (~100-150 lines)
5. Add YAML configuration

**No new module needed.** This can be a configuration + converter extension within the existing `jaiclaw-camel` module, or a thin `jaiclaw-channel-whatsapp` that declares the Camel route.

```xml
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-whatsapp-starter</artifactId>
</dependency>
```

---

### 1.4 Image Generation

| | |
|---|---|
| **Library** | **Spring AI ImageModel** (`spring-ai-starter-model-openai`, `spring-ai-starter-model-stability-ai`) |
| **Coverage** | ~90% |
| **Verdict** | **Just add dependency + tool wrapper** |

**What it provides:** Spring AI's `ImageModel` interface with `call(ImagePrompt)` → `ImageResponse`. Already supports:
- **OpenAI DALL-E** (2 and 3) with quality, size, style options
- **Stability AI** with style, size, cfg_scale options
- Auto-configuration — add starter + API key, model bean is created

**What to build:** An `ImageGenerationTool` (JaiClaw `ToolCallback`) wrapping Spring AI's `ImageModel`. ~50-80 lines:

```java
public class ImageGenerationTool implements ToolCallback {
    private final ImageModel imageModel;

    @Override
    public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
        var prompt = new ImagePrompt((String) params.get("prompt"),
            ImageOptionsBuilder.builder()
                .model((String) params.getOrDefault("model", "dall-e-3"))
                .width((Integer) params.getOrDefault("width", 1024))
                .height((Integer) params.getOrDefault("height", 1024))
                .build());
        var response = imageModel.call(prompt);
        return ToolResult.success(response.getResult().getOutput().getUrl());
    }
}
```

**No new module needed.** Register the tool via auto-configuration when `ImageModel` bean is present (`@ConditionalOnBean(ImageModel.class)`).

```xml
<!-- Already in Spring AI starters — user adds to their app -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

---

### 1.5 Observability

| | |
|---|---|
| **Library** | **Spring Boot Actuator** + **Micrometer** + **OTel Bridge** |
| **Coverage** | ~90% |
| **Verdict** | **Just add dependencies + YAML config** |

**What it provides out of the box (zero code):**
- `spring-boot-starter-actuator` → `/actuator/health`, `/actuator/info`, `/actuator/metrics`
- `micrometer-registry-prometheus` → `/actuator/prometheus` scrape endpoint
- `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` → Distributed tracing to Jaeger/Tempo/etc.
- Spring Boot auto-configures everything: trace context propagation, MDC correlation, metric registries

**What to build:** ~100-200 lines of custom `Observation` instrumentation at key points:
- `AgentRuntime.processMessage()` — agent invocation timing
- `ToolRegistry.execute()` — tool call counters/timers
- `GatewayService.routeMessage()` — message routing metrics
- Custom Micrometer counters for token usage

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  otlp.tracing.endpoint: http://localhost:4318/v1/traces
```

---

### 2.1 Web Search Provider Registry

| | |
|---|---|
| **Library coverage** | Partial — Tavily MCP server available, REST APIs are simple |
| **Verdict** | **Custom SPI, but providers are thin REST clients** |

**Options:**
1. **MCP approach (zero code):** Tavily provides an MCP server. JaiClaw already supports MCP tool providers via `McpToolProvider` SPI. Configure the Tavily MCP server as an external MCP endpoint → search tools appear automatically.
2. **SPI approach:** Create a `WebSearchProvider` interface with ~50-line REST client implementations per provider.

**Recommendation:** Start with MCP for Tavily (zero code). Build the SPI only if we need tighter integration or providers that don't have MCP servers.

**Estimated code:** 0 lines for MCP approach; ~200-300 lines for custom SPI with 2-3 providers.

---

### 2.2 Enhanced Memory (Circuit Breakers, Search Modes)

| | |
|---|---|
| **Library** | **Resilience4j** (`spring-cloud-starter-circuitbreaker-resilience4j`) |
| **Coverage** | ~95% for circuit breakers |
| **Verdict** | **Just add dependency + wrap existing calls** |

**What it provides:**
- `CircuitBreakerFactory.create("vectorStore").run(supplier, fallback)` — wraps any method call
- Configurable thresholds, timeouts, cooldown periods via YAML
- Auto-integrates with Micrometer metrics
- Spring Boot auto-configuration

**What to build:** ~30-50 lines. Wrap `VectorStoreSearchManager.search()` with circuit breaker. Fallback to `InMemorySearchManager` when vector store is unavailable.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      vectorStore:
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        slidingWindowSize: 10
```

**Search modes:** JaiClaw already has `HybridSearchManager` — extend with mode selection via `MemorySearchOptions`. No new library needed.

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

---

### 2.3 Memory Wiki

| | |
|---|---|
| **Library coverage** | None — custom domain |
| **Verdict** | **Custom, but builds on existing `jaiclaw-docstore`** |

**What already exists:** `jaiclaw-docstore` handles document CRUD with tenant isolation. `VectorStoreSearchManager` provides semantic search. `jaiclaw-documents` handles Markdown parsing.

**What to build:** A `WikiStore` service layered on top of existing modules. Markdown files in categorized directories (Obsidian-compatible). YAML frontmatter for metadata. ~300-400 lines.

---

### 2.4 Model Catalog

| | |
|---|---|
| **Library coverage** | None — no Java model catalog library exists |
| **Verdict** | **Custom registry backed by static YAML files** |

**What to build:** A `ModelCatalog` registry that loads model metadata (context window, pricing, capabilities) from YAML resource files. Expose via `ModelListTool` and `ModelInfoTool`. Optional REST client to LiteLLM API for dynamic updates. ~200-300 lines.

---

### 2.5 Plugin State Store

| | |
|---|---|
| **Library** | **Caffeine** (`com.github.ben-manes.caffeine:caffeine`) |
| **Coverage** | ~90% |
| **Verdict** | **Just add dependency + thin SPI** |

**What it provides:** High-performance in-memory cache with per-entry variable TTL via `Caffeine.newBuilder().expireAfter(...)`. Already managed by Spring Boot's dependency management.

**What to build:** A `PluginStateStore` interface in `jaiclaw-plugin-sdk` (~30 lines). Default `CaffeinePluginStateStore` implementation (~50-60 lines). Add `stateStore()` to `PluginApi`.

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <!-- Version managed by Spring Boot BOM -->
</dependency>
```

---

### 2.6 Token Compression

| | |
|---|---|
| **Library** | **JTokkit** (`com.knuddels:jtokkit:1.1.0`) |
| **Coverage** | ~85% for token counting |
| **Verdict** | **Just add dependency for counting; compression builds on existing `CompactionService`** |

**What it provides:** Accurate token counting for all OpenAI tokenizer encodings (cl100k_base, o200k_base). Zero dependencies, 2-3x faster than alternatives.

**What to build:**
1. Replace `TokenEstimator`'s `text.length() / 4.0` heuristic with `jtokkit.countTokens(text)` — ~5-line change
2. Add `ToolResultCompressor` SPI that truncates large tool results to a token budget before adding to conversation context — ~100-150 lines, building on existing `CompactionService`

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>1.1.0</version>
</dependency>
```

---

### 3.1 Task Executor & Flow Engine

| | |
|---|---|
| **Library options** | **Camel SEDA routes** (already present) or **Temporal.io** (heavy) |
| **Coverage** | Camel ~40%, Temporal ~80% |
| **Verdict** | **Start with Camel SEDA (already available); add Temporal only for complex durability needs** |

**What Camel already provides (zero new dependencies):**
- SEDA queues for async, multi-consumer task execution
- Content-based routing for flow dispatch
- Error handling with retry, dead-letter channels
- Timer-based polling for scheduled tasks
- Transaction support
- Already wired into JaiClaw via `jaiclaw-camel`

**What to build for basic task execution:**
1. `TaskRecord` / `TaskFlow` records (~50 lines)
2. A Camel route that picks up task requests from a SEDA queue, executes them, and updates status (~100 lines)
3. `CreateTaskTool` / `ListTasksTool` / `UpdateTaskTool` for LLM access (~150 lines)
4. Optional JPA persistence for task state (~100 lines)

**When to add Temporal:** Only if you need durable workflows that survive process restarts, saga compensation patterns, or human-in-the-loop approval steps. This adds a Temporal server dependency (Docker container + PostgreSQL).

**Estimated code:** ~400 lines with Camel SEDA approach; ~200 lines + Temporal server with Temporal approach.

```xml
<!-- Only if graduating to Temporal -->
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-spring-boot-starter</artifactId>
    <version>1.32.0</version>
</dependency>
```

---

### 3.2 Thread Ownership

| | |
|---|---|
| **Library coverage** | None — domain-specific routing logic |
| **Verdict** | **Custom implementation** |

**What to build:** A `ThreadOwnershipTracker` with a `ConcurrentHashMap<String, OwnershipEntry>` mapping thread keys to agent IDs with TTL expiry. A `MentionDetector` regex parser. Integration as a filter/decorator in `GatewayService` routing. ~150-200 lines.

---

### 3.3 Webhook Event Routing

| | |
|---|---|
| **Library** | **Spring MVC** (already present) + **Camel Webhook** component |
| **Coverage** | ~70% |
| **Verdict** | **Mostly covered by existing infrastructure** |

**What already exists:**
- Spring MVC `@PostMapping` for HTTP endpoints
- Camel Webhook component auto-registers webhook endpoints for Camel-integrated channels (Telegram, WhatsApp)
- JaiClaw gateway already dispatches webhook payloads to channel adapters

**What to build:** A `WebhookRoute` configuration model and a dynamic `WebhookController` that registers routes at startup from YAML config. HMAC signature verification utility. ~100-150 lines.

---

### 3.4 Admin HTTP RPC

| | |
|---|---|
| **Library** | **Spring Boot Actuator custom endpoints** |
| **Coverage** | ~60% |
| **Verdict** | **Spring MVC controller + Actuator patterns** |

**What Actuator already provides:** `/actuator/health`, `/actuator/info`, `/actuator/env`, `/actuator/beans`, `/actuator/metrics`. Custom Actuator endpoints via `@Endpoint`.

**What to build:** A `@RestController` for JaiClaw-specific admin operations (session list/kill, channel restart, broadcast). ~200-300 lines. Could alternatively use `@Endpoint(id = "jaiclaw")` for Actuator-native integration.

---

### 4.1-4.3 New Channels (Line, Matrix, Google Chat)

| Channel | Library | Coverage | Approach |
|---------|---------|----------|----------|
| **LINE** | `com.linecorp.bot:line-bot-spring-boot-webmvc:7.5.0` | ~85% | Official SDK with Spring Boot starter |
| **Google Chat** | `com.google.cloud:google-cloud-chat:0.55.0` | ~70% | Google Cloud client library |
| **Matrix** | Direct REST via `HttpClient` | ~0% (SDKs are alpha-quality) | Native HTTP implementation |

**LINE — least effort:**
The official LINE Bot SDK for Java includes a Spring Boot starter that auto-configures webhook handling. Create a `LineAdapter implements ChannelAdapter` wrapping the SDK's `MessagingApiClient`. ~200-300 lines.

```xml
<dependency>
    <groupId>com.linecorp.bot</groupId>
    <artifactId>line-bot-spring-boot-webmvc</artifactId>
    <version>7.5.0</version>
</dependency>
```

**Google Chat — medium effort:**
Google Cloud Chat client library handles auth and API calls. ~250-350 lines for the adapter.

**Matrix — most effort:**
Java Matrix SDKs are immature. Use the Matrix Client-Server API directly via `java.net.http.HttpClient`. ~400-500 lines.

---

### 5.1 Video Generation

| | |
|---|---|
| **Library coverage** | None — no Java video generation libraries exist |
| **Verdict** | **100% custom REST clients** |

Spring AI has no video model support. Each provider (Runway, Sora, Veo) has a different REST API. Create a `VideoGenerationProvider` SPI + per-provider REST client implementations. ~200 lines SPI + ~100-150 lines per provider.

---

### 5.2 ElevenLabs TTS

| | |
|---|---|
| **Library** | **Spring AI ElevenLabs** (`spring-ai-starter-model-elevenlabs`) |
| **Coverage** | ~90% |
| **Verdict** | **Just add dependency** |

Spring AI 1.1.x includes a full ElevenLabs integration with `ElevenLabsTextToSpeechModel`, streaming support, voice selection, and multiple audio formats. Auto-configured.

**What to build:** An `ElevenLabsTtsProvider implements TtsProvider` wrapping Spring AI's model. ~40-60 lines.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-elevenlabs</artifactId>
</dependency>
```

---

### 5.3 Deepgram STT

| | |
|---|---|
| **Library** | **Deepgram Java SDK** (`com.deepgram:deepgram-java-sdk:0.2.0`) |
| **Coverage** | ~75% |
| **Verdict** | **Wrap the SDK** |

**What to build:** A `DeepgramSttProvider implements SttProvider` wrapping the Deepgram SDK. ~60-80 lines.

```xml
<dependency>
    <groupId>com.deepgram</groupId>
    <artifactId>deepgram-java-sdk</artifactId>
    <version>0.2.0</version>
</dependency>
```

---

### 6.1 Trajectory Tracking

| | |
|---|---|
| **Library** | **Micrometer Tracing** (from item 1.5) + existing `jaiclaw-audit` |
| **Coverage** | ~60% |
| **Verdict** | **Extend existing AuditLogger, not a new module** |

**What already exists:** `AuditLogger` SPI, `AuditEvent` records, `InMemoryAuditLogger` in `jaiclaw-audit`. Micrometer tracing (from 1.5) provides the timing/trace correlation.

**What to build:** Extend `AuditEvent` with trajectory-specific fields (step type, tool name, input/output, token count). Create a `TrajectoryRecorder` that instruments `AgentRuntime` to emit events at each decision point. ~200-300 lines in existing `jaiclaw-audit` module. No new module needed.

---

### 6.2 Transcript Storage

| | |
|---|---|
| **Library coverage** | None needed — Jackson + file I/O |
| **Verdict** | **~100-150 lines of custom code** |

Serialize session messages to JSON files using Jackson (already a dependency). Tenant-scoped file paths. Markdown summary rendering.

---

### 6.3 Internationalization (i18n)

| | |
|---|---|
| **Library** | **Spring `MessageSource`** (built-in) + **`java.util.ResourceBundle`** (JDK) |
| **Coverage** | ~100% for the framework |
| **Verdict** | **Zero library code — the work is string extraction and translation** |

Spring Boot auto-configures `ResourceBundleMessageSource` when `messages.properties` exists. For `jaiclaw-core` (pure Java, no Spring), use `java.util.ResourceBundle.getBundle()` directly.

The actual effort is identifying user-facing strings and creating translation files. No new dependencies.

---

### 6.4 Secrets Management

| | |
|---|---|
| **Library** | **Spring Cloud Vault** or **Camel Vault Components** |
| **Coverage** | ~90% |
| **Verdict** | **Just add dependency + YAML config — zero code** |

**Option A — Spring Cloud Vault:**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```
```yaml
spring.cloud.vault:
  uri: http://localhost:8200
  token: ${VAULT_TOKEN}
  kv.backend: secret
```
Secrets appear as Spring properties. Zero code.

**Option B — Camel Vault (already using Camel 4.18.1):**
```xml
<!-- Pick one or more -->
<dependency><artifactId>camel-hashicorp-vault</artifactId></dependency>
<dependency><artifactId>camel-aws-secrets-manager</artifactId></dependency>
<dependency><artifactId>camel-azure-key-vault</artifactId></dependency>
<dependency><artifactId>camel-google-secret-manager</artifactId></dependency>
```
Use `{{hashicorp:mySecret}}` property placeholder syntax in Camel routes. Auto-refresh on rotation.

---

## Revised Effort Matrix

| # | Feature | Original Estimate | Revised Estimate | Savings |
|---|---------|-------------------|------------------|---------|
| 1.1 | Auto-Reply Chunking | 1–3 days | 1–2 days (custom) | — |
| 1.2 | Web Readability | 1–3 days | **2-3 hours** (wrap Readability4J) | ~80% |
| 1.3 | WhatsApp Channel | 1–2 weeks | **2-3 days** (camel-whatsapp + converter) | ~70% |
| 1.4 | Image Generation | 1–2 weeks | **1 day** (Spring AI ImageModel + tool) | ~85% |
| 1.5 | Observability | 1–3 days | **1 day** (add deps + YAML + custom observations) | ~50% |
| 2.1 | Web Search | 1–2 weeks | **3-5 days** (SPI + REST clients) or **0** (MCP) | ~50% |
| 2.2 | Enhanced Memory | 1–2 weeks | **1-2 days** (Resilience4j wrap) | ~80% |
| 2.3 | Memory Wiki | 1–2 weeks | 1 week (custom, builds on DocStore) | ~30% |
| 2.4 | Model Catalog | 1–2 weeks | 3-5 days (custom registry + YAML) | ~50% |
| 2.5 | Plugin State Store | 1–3 days | **2-3 hours** (Caffeine wrapper) | ~80% |
| 2.6 | Token Compression | 1–2 weeks | **1-2 days** (JTokkit + compactor) | ~75% |
| 3.1 | Task Executor | 2+ weeks | **1 week** (Camel SEDA approach) | ~50% |
| 3.2 | Thread Ownership | 1–2 weeks | 3-5 days (custom) | ~40% |
| 3.3 | Webhook Routing | 1–2 weeks | **1-2 days** (Spring MVC + Camel) | ~75% |
| 3.4 | Admin HTTP RPC | 1–3 days | 1-2 days (Spring MVC) | — |
| 4.1 | LINE Channel | 1–2 weeks | **3-5 days** (official SDK with Spring Boot starter) | ~60% |
| 4.2 | Matrix Channel | 1–2 weeks | 1–2 weeks (weak SDK, native HTTP) | — |
| 4.3 | Google Chat Channel | 1–2 weeks | 1 week (Google Cloud client lib) | ~30% |
| 5.1 | Video Generation | 2+ weeks | 2+ weeks (100% custom) | — |
| 5.2 | ElevenLabs TTS | 1–3 days | **2-3 hours** (Spring AI starter) | ~85% |
| 5.3 | Deepgram STT | 1–3 days | **half day** (SDK wrapper) | ~60% |
| 6.1 | Trajectory Tracking | 1–2 weeks | **3-5 days** (extend existing AuditLogger) | ~50% |
| 6.2 | Transcript Storage | 1–3 days | **half day** (Jackson file I/O) | ~60% |
| 6.3 | i18n | 1–2 weeks | 3-5 days (string extraction work) | ~50% |
| 6.4 | Secrets Management | 1–2 weeks | **1 day** (add Spring Cloud Vault dependency) | ~85% |

**Overall effort reduction: ~55-60%** compared to original roadmap estimates.

---

## Library Dependency Summary

### New dependencies to add (all items)

```xml
<!-- Phase 1 -->
<dependency> <!-- 1.2 Web Readability -->
    <groupId>net.dankito.readability4j</groupId>
    <artifactId>readability4j</artifactId>
    <version>1.0.8</version>
</dependency>
<dependency> <!-- 1.3 WhatsApp -->
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-whatsapp-starter</artifactId>
    <!-- version managed by Camel BOM -->
</dependency>
<!-- 1.4 Image Generation: spring-ai-starter-model-openai already available -->
<dependency> <!-- 1.5 Observability -->
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- Phase 2 -->
<dependency> <!-- 2.2 Circuit Breakers -->
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
<dependency> <!-- 2.5 Plugin State Store -->
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <!-- version managed by Spring Boot BOM -->
</dependency>
<dependency> <!-- 2.6 Token Counting -->
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- Phase 4 -->
<dependency> <!-- 4.1 LINE -->
    <groupId>com.linecorp.bot</groupId>
    <artifactId>line-bot-spring-boot-webmvc</artifactId>
    <version>7.5.0</version>
</dependency>
<dependency> <!-- 4.3 Google Chat -->
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-chat</artifactId>
    <version>0.55.0</version>
</dependency>

<!-- Phase 5 -->
<dependency> <!-- 5.2 ElevenLabs -->
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-elevenlabs</artifactId>
</dependency>
<dependency> <!-- 5.3 Deepgram -->
    <groupId>com.deepgram</groupId>
    <artifactId>deepgram-java-sdk</artifactId>
    <version>0.2.0</version>
</dependency>

<!-- Phase 6 -->
<dependency> <!-- 6.4 Secrets -->
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

---

## Key Architectural Decisions

### 1. Camel-First for Channels
Since `jaiclaw-camel` already bridges Camel routes to JaiClaw channels via `CamelChannelAdapter`, prefer Camel components over native adapters when a Camel component exists. This applies to:
- **WhatsApp** → `camel-whatsapp` (exists)
- **LINE** → no Camel component; use official SDK
- **Matrix** → no Camel component; native HTTP
- **Google Chat** → no Camel component; use Google Cloud client

### 2. Spring AI-First for AI Capabilities
Spring AI already provides model abstractions for image generation, TTS, and STT. Wrap these rather than building provider-specific REST clients:
- **Image Generation** → `ImageModel` (OpenAI, Stability AI)
- **ElevenLabs TTS** → `ElevenLabsTextToSpeechModel`
- **Deepgram STT** → SDK wrapper (Spring AI only has OpenAI Whisper)

### 3. MCP-First for External Tool Integration
JaiClaw already supports MCP tool providers. For services that expose MCP servers (Tavily, various search APIs), configure them as MCP endpoints rather than building custom SPI implementations:
- **Web Search** → Tavily MCP server (zero code)

### 4. Extend Before Creating New Modules
Several features can be added to existing modules rather than creating new ones:
- **Trajectory** → extend `jaiclaw-audit` (not a new module)
- **Image Generation Tool** → add to `jaiclaw-tools` or auto-configure when `ImageModel` is present
- **Plugin State** → add to `jaiclaw-plugin-sdk`
- **Token Counting** → enhance `jaiclaw-compaction`
- **Web Readability** → enhance `jaiclaw-tools` WebFetchTool

### 5. Camel SEDA for Task Execution
Use existing Camel SEDA routes for async task execution instead of building a custom task engine or adding Temporal infrastructure. Graduate to Temporal only when durability requirements justify the operational cost.
