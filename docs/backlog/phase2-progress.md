# Phase 2 — P2 Core Infrastructure — Progress Tracker

**Started:** —
**Status:** Not Started
**Depends on:** Phase 1 complete

## Work Items

### 2.1 Web Search Provider Registry
- **Location:** New module `extensions/jaiclaw-web-search/` OR MCP-first approach (zero code)
- **Status:** NOT STARTED
- **Revised estimate:** 0 days (MCP) or 3-5 days (custom SPI)
- **Approach — Option A (recommended): MCP-first**
  - Tavily provides an MCP server. JaiClaw already supports MCP tool providers via `McpToolProvider` SPI.
  - Configure Tavily MCP server as an external MCP endpoint → search tools appear automatically.
  - **Zero code.** Just MCP server configuration.
- **Approach — Option B: Custom SPI**
  - Create `WebSearchProvider` SPI with thin REST client implementations per provider (~50 lines each).
  - Providers: `DuckDuckGoProvider` (extract from existing `WebSearchTool`), `TavilyProvider`, `BraveProvider`.
- **Decision needed:** MCP-first vs custom SPI. MCP is simpler but requires running an external MCP server process.
- **Files to create (Option B only):**
  - [ ] `extensions/jaiclaw-web-search/pom.xml`
  - [ ] `WebSearchProvider.java` (SPI)
  - [ ] `WebSearchRegistry.java`
  - [ ] `BraveSearchProvider.java` (~50 lines REST client)
  - [ ] `TavilySearchProvider.java` (~50 lines REST client)
  - [ ] `WebSearchTool.java` (ToolCallback)
  - [ ] Config + auto-configuration
  - [ ] Spock specs
- **Notes:** Each provider is a thin REST wrapper. No complex library integration needed.

### 2.2 Enhanced Memory
- **Location:** Enhance existing `core/jaiclaw-memory/`
- **Status:** NOT STARTED
- **Revised estimate:** 1-2 days (down from 1-2 weeks)
- **Approach:** Use **Resilience4j** (`spring-cloud-starter-circuitbreaker-resilience4j`) for circuit breakers. Spring AI VectorStore already covers multi-backend search. JaiClaw's `HybridSearchManager` already exists for hybrid search modes.
- **Library:** `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j`
- **What Resilience4j provides:**
  - `CircuitBreakerFactory.create("vectorStore").run(supplier, fallback)` — wrap any method
  - Configurable thresholds, timeouts, cooldown via YAML
  - Auto-integrates with Micrometer metrics
  - Spring Boot auto-configuration
- **Files to create/modify:**
  - [ ] Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency
  - [ ] Wrap `VectorStoreSearchManager.search()` with circuit breaker (~30-50 lines)
  - [ ] Add fallback to `InMemorySearchManager` when vector store is unavailable
  - [ ] Add `SearchMode` enum and `QueryScope` enum to `MemorySearchOptions`
  - [ ] Extend `HybridSearchManager` with mode selection
  - [ ] Add `MemoryToggleStore` interface + `InMemoryToggleStore` for per-session toggles
  - [ ] Spock specs
- **No need to build:** Custom `MemoryCircuitBreaker`, `CircuitBreakerConfig`, `CircuitBreakerState` — Resilience4j handles all of this.
- **Configuration:**
  ```yaml
  resilience4j:
    circuitbreaker:
      instances:
        vectorStore:
          failureRateThreshold: 50
          waitDurationInOpenState: 60s
          slidingWindowSize: 10
  ```
- **Notes:** ~80-100 lines total new code. Resilience4j does the heavy lifting.

### 2.3 Memory Wiki
- **Location:** New module `extensions/jaiclaw-memory-wiki/`
- **Status:** NOT STARTED
- **Revised estimate:** 1 week (unchanged — this is genuinely custom)
- **Depends on:** Can build on existing `jaiclaw-docstore` for CRUD and `VectorStoreSearchManager` for semantic search
- **Approach:** Custom implementation, but leverage existing JaiClaw modules:
  - `jaiclaw-docstore` — document CRUD with tenant isolation (already exists)
  - `jaiclaw-memory` / `VectorStoreSearchManager` — semantic search (already exists)
  - `jaiclaw-documents` — Markdown parsing (already exists)
- **Files to create:** Same as original plan (~300-400 lines)
  - [ ] `extensions/jaiclaw-memory-wiki/pom.xml`
  - [ ] `WikiPage.java`, `WikiPageKind.java`, `WikiPalaceStatus.java`
  - [ ] `WikiStore.java` (SPI)
  - [ ] `WikiService.java`
  - [ ] `WikiCompiler.java`, `WikiCorpusBuilder.java`
  - [ ] `FileSystemWikiStore.java`
  - [ ] `WikiSearchTool.java`, `WikiGetTool.java`, `WikiUpdateTool.java`, `WikiStatusTool.java`
  - [ ] Config + auto-configuration
  - [ ] Spock specs
- **Notes:** No external library handles this use case. Builds on existing JaiClaw infrastructure.

### 2.4 Model Catalog
- **Location:** New module `extensions/jaiclaw-model-catalog/`
- **Status:** NOT STARTED
- **Revised estimate:** 3-5 days (unchanged — custom registry)
- **Approach:** Custom `ModelCatalog` registry backed by static YAML resource files. Optional REST client to LiteLLM API for dynamic updates. Spring AI's `ChatModel` provides basic model info but not pricing/capabilities.
- **Files to create:** Same as original plan (~200-300 lines)
  - [ ] Module POM, records, registry, YAML loaders, tools, auto-config
  - [ ] Static YAML files: `anthropic.yml`, `openai.yml`, `google.yml`, `ollama.yml`
  - [ ] Spock specs
- **Notes:** No Java library exists for this. LiteLLM API is a possible data source but adds external dependency.

### 2.5 Plugin State Store
- **Location:** Enhance `core/jaiclaw-plugin-sdk/`
- **Status:** NOT STARTED
- **Revised estimate:** 2-3 hours (down from 1-3 days)
- **Approach:** Use **Caffeine** (`com.github.ben-manes.caffeine:caffeine`) for high-performance in-memory TTL cache. Already managed by Spring Boot BOM. Thin SPI wrapper.
- **Library:** `com.github.ben-manes.caffeine:caffeine` (version managed by Spring Boot)
- **What Caffeine provides:**
  - Per-entry variable TTL via `Caffeine.newBuilder().expireAfter(...)`
  - High-performance concurrent cache
  - Zero configuration needed
- **Files to create/modify:**
  - [ ] Add `caffeine` dependency to `core/jaiclaw-plugin-sdk/pom.xml`
  - [ ] `PluginStateStore.java` (SPI interface — ~30 lines)
  - [ ] `PluginStateEntry.java` (record)
  - [ ] `CaffeinePluginStateStore.java` (implementation — ~50-60 lines)
  - [ ] Modify `PluginApi.java` — add `stateStore()` method
  - [ ] Spock specs
- **No need to build:** `InMemoryPluginStateStore` with manual TTL management — Caffeine handles it.
- **Notes:** ~80-100 lines total. Caffeine is just-add-dependency.

### 2.6 Token Compression
- **Location:** Enhance existing `extensions/jaiclaw-compaction/`
- **Status:** NOT STARTED
- **Revised estimate:** 1-2 days (down from 1-2 weeks)
- **Approach:** Two changes:
  1. **Token counting:** Replace `TokenEstimator`'s `text.length() / 4.0` heuristic with **JTokkit** (`com.knuddels:jtokkit:1.1.0`) for accurate token counting. ~5-line change.
  2. **Tool result compaction:** Add a `ToolResultCompressor` SPI + a few compressor implementations that truncate large tool results before adding to conversation context. Builds on existing `CompactionService`. ~100-150 lines.
- **Library:** `com.knuddels:jtokkit:1.1.0` — Java tiktoken port, zero dependencies, MIT license
- **Files to create/modify:**
  - [ ] Add `jtokkit` dependency to `extensions/jaiclaw-compaction/pom.xml`
  - [ ] Modify `TokenEstimator` — replace heuristic with `jtokkit.countTokens()` (~5 lines)
  - [ ] `ToolResultCompressor.java` (SPI interface — ~20 lines) in `jaiclaw-compaction`
  - [ ] `ShellResultCompressor.java`, `GenericTruncationCompressor.java` (~50 lines each)
  - [ ] Wire compressor into `AgentRuntime` post-tool-execution hook
  - [ ] Spock specs
- **No need to build:** New `extensions/jaiclaw-token-compression/` module, `CompositeCompressor`, `CompressorRegistry` — keep it simple within existing `jaiclaw-compaction`.
- **Notes:** JTokkit handles counting. Compression logic is custom but small, building on existing infrastructure.

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 2.2 Enhanced Memory: Resilience4j → 1-2 days (was 1-2 weeks)
  - 2.5 Plugin State: Caffeine → 2-3 hours (was 1-3 days)
  - 2.6 Token Compression: JTokkit + extend compaction → 1-2 days (was 1-2 weeks)
