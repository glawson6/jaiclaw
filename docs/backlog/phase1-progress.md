# Phase 1 — P1 Quick Wins — Progress Tracker

**Started:** 2026-05-28
**Status:** In Progress

## Work Items

### 1.1 Auto-Reply Chunking
- **Location:** `core/jaiclaw-channel-api/`
- **Status:** NOT STARTED
- **Revised estimate:** 1-2 days
- **Approach:** Custom implementation (no library handles platform-aware text chunking with Markdown preservation)
- **Files to create/modify:**
  - [ ] `core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/chunking/ChunkingStrategy.java`
  - [ ] `core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/chunking/LengthChunkingStrategy.java`
  - [ ] `core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/chunking/NewlineChunkingStrategy.java`
  - [ ] `core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/chunking/PlatformLimits.java`
  - [ ] `core/jaiclaw-channel-api/src/main/java/io/jaiclaw/channel/chunking/MessageChunker.java`
  - [ ] `core/jaiclaw-channel-api/src/test/groovy/io/jaiclaw/channel/chunking/MessageChunkerSpec.groovy`
  - [ ] Modify `ChannelAdapter.java` — add `default PlatformLimits platformLimits()`
  - [ ] Modify existing channel adapters to override `platformLimits()`
  - [ ] Integrate chunking into `GatewayService.sendResponse()`
- **Notes:** ~200-250 lines. Pure string manipulation, no external dependencies.

### 1.2 Web Readability Extraction
- **Location:** `core/jaiclaw-tools/` (enhance existing `WebFetchTool`)
- **Status:** NOT STARTED
- **Revised estimate:** 2-3 hours
- **Approach:** Wrap **Readability4J** (`net.dankito.readability4j:readability4j:1.0.8`) — a direct port of Mozilla's Readability.js. ~95% covered out of the box.
- **Library:** `net.dankito.readability4j:readability4j:1.0.8` (uses Jsoup internally)
- **Files to create/modify:**
  - [ ] Add `readability4j` dependency to `core/jaiclaw-tools/pom.xml`
  - [ ] Modify `WebFetchTool` — add `extractArticle` parameter, call `Readability4J(url, html).parse()`
  - [ ] Spock spec for readability extraction
- **No new classes needed** — ~20 lines of integration code in `WebFetchTool`
- **Notes:** Readability4J extracts title, byline, content (HTML + plain text), excerpt. No separate `ReadabilityExtractor` class needed — just call the library directly.

### 1.3 WhatsApp Channel
- **Location:** Extend existing `extensions/jaiclaw-camel/` + config (NOT a new native channel module)
- **Status:** NOT STARTED
- **Revised estimate:** 2-3 days (down from 1-2 weeks)
- **Depends on:** 1.1 (PlatformLimits)
- **Approach:** Use **`camel-whatsapp`** component (`org.apache.camel.springboot:camel-whatsapp-starter:4.18.1`). JaiClaw already has `CamelChannelAdapter` that bridges any Camel route to JaiClaw channels via SEDA queues. WhatsApp becomes a configuration + converter extension, not a ground-up channel module.
- **Library:** `org.apache.camel.springboot:camel-whatsapp-starter` (version managed by Camel BOM 4.18.1)
- **What camel-whatsapp provides:**
  - **Producer:** Send text, template, media, location, contact messages
  - **Consumer (webhook):** Receive inbound messages via `camel-webhook`
  - WhatsApp Cloud API integration
- **Files to create/modify:**
  - [ ] Add `camel-whatsapp-starter` dependency to `extensions/jaiclaw-camel/pom.xml` (or a new thin `jaiclaw-channel-whatsapp` module)
  - [ ] `WhatsAppCamelRouteConfig.java` — Camel route: `from("whatsapp:phoneNumberId")` → SEDA inbound
  - [ ] `WhatsAppMessageConverter.java` — Map WhatsApp-specific types (templates, location, media) to `ChannelMessage`
  - [ ] YAML config for WhatsApp credentials
  - [ ] Spock specs
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-whatsapp/pom.xml`
- **No need to build:** `WhatsAppCloudApiClient`, `WhatsAppWebhookController`, `WhatsAppMediaHandler` — Camel handles all of this.
- **Configuration:**
  ```yaml
  jaiclaw:
    channels:
      whatsapp:
        phone-number-id: ${WHATSAPP_PHONE_NUMBER_ID}
        access-token: ${WHATSAPP_ACCESS_TOKEN}
        verify-token: ${WHATSAPP_VERIFY_TOKEN}
  ```
- **Notes:** ~100-150 lines of converter/config code. Camel does all HTTP, webhook, and API handling.

### 1.4 Image Generation
- **Location:** Enhance existing modules (NOT a new extension module)
- **Status:** NOT STARTED
- **Revised estimate:** 1 day (down from 1-2 weeks)
- **Approach:** Use **Spring AI `ImageModel`** — already supports OpenAI DALL-E 2/3 and Stability AI via starters. Just create a thin `ImageGenerationTool` (JaiClaw `ToolCallback`) that wraps Spring AI's `ImageModel`. Auto-configure when `ImageModel` bean is present.
- **Library:** `org.springframework.ai:spring-ai-starter-model-openai` (already available in starter ecosystem)
- **What Spring AI provides:**
  - `ImageModel` interface with `call(ImagePrompt)` → `ImageResponse`
  - OpenAI DALL-E provider with quality, size, style options
  - Stability AI provider with style, size, cfg_scale options
  - Full auto-configuration — add starter + API key, done
- **Files to create/modify:**
  - [ ] `ImageGenerationTool.java` — JaiClaw `ToolCallback` wrapping `ImageModel` (~50-80 lines)
  - [ ] Auto-configuration: register tool `@ConditionalOnBean(ImageModel.class)` in starter or `jaiclaw-tools`
  - [ ] Spock spec
- **No need to build:** `ImageGenerationProvider` SPI, `ImageGenerationRegistry`, `ImageGenerationService`, `ImageCapabilities`, `OpenAiImageProvider`, `SpringAiImageBridge` — Spring AI handles all of this.
- **Notes:** No new module. No new SPI. Just a ~50-80 line tool class + auto-config wiring.

### 1.5 Observability Starter
- **Location:** Starter POM + ~100-200 lines of custom observations in existing modules
- **Status:** NOT STARTED
- **Revised estimate:** 1 day (down from 1-3 days)
- **Approach:** 90% just add dependencies + YAML config. Spring Boot auto-configures Actuator, Prometheus, and OTel tracing. Add small amount of custom `Observation` instrumentation at key code paths.
- **Libraries (all just-add-dependency):**
  - `org.springframework.boot:spring-boot-starter-actuator`
  - `io.micrometer:micrometer-registry-prometheus` → auto-configures `/actuator/prometheus`
  - `io.micrometer:micrometer-tracing-bridge-otel` → auto-configures OTel tracing
  - `io.opentelemetry:opentelemetry-exporter-otlp` → OTLP export to Jaeger/Tempo
- **What Spring Boot auto-configures (zero code):**
  - `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`
  - `OtlpHttpSpanExporter` wired to Micrometer Observation API
  - MDC trace/span ID correlation in logs
- **Files to create/modify:**
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-observability/pom.xml` (dependency aggregator)
  - [ ] Add custom `Observation` points in `AgentRuntime.processMessage()` (~5-10 lines)
  - [ ] Add custom `Observation` points in `ToolRegistry.execute()` (~5-10 lines)
  - [ ] Add custom `Observation` points in `GatewayService.routeMessage()` (~5-10 lines)
  - [ ] Add custom Micrometer counters for token usage, messages processed
  - [ ] Register starter in `jaiclaw-starters/pom.xml`
- **No need to build:** `JaiClawMetrics.java`, `AgentRuntimeObserver.java`, `ToolCallObserver.java`, `ChannelMessageObserver.java`, auto-config class, new extension module — Spring Boot handles the heavy lifting.
- **Configuration:**
  ```yaml
  management:
    endpoints.web.exposure.include: health,info,metrics,prometheus
    otlp.tracing.endpoint: http://localhost:4318/v1/traces
  ```
- **Notes:** No new extension module needed. The starter is just a POM that pulls in the right dependencies. Custom observations are ~5-10 lines each sprinkled into existing code.

## Session Log

### Session 1 — 2026-05-28
- Created feature parity report
- Created implementation roadmap with detailed per-module specs
- Created phase progress trackers
- Completed library analysis (utilize-existing-libraries.md)
- **Revised all phase trackers** to leverage existing libraries
  - 1.2 Web Readability: Readability4J wraps 95% → 2-3 hours
  - 1.3 WhatsApp: camel-whatsapp handles 75% → 2-3 days (was 1-2 weeks)
  - 1.4 Image Generation: Spring AI ImageModel handles 90% → 1 day (was 1-2 weeks)
  - 1.5 Observability: Spring Boot Actuator+Micrometer handles 90% → 1 day
