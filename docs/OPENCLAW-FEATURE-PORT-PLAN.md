# OpenClaw Feature Port Plan for JClaw

This document plans the implementation of 10 OpenClaw features currently missing from JClaw. Each feature is designed as a new JClaw module (or enhancement to an existing one) following the project's established patterns: zero-Spring core types, Spring Boot auto-configuration, Spock tests, and lazy tool registration.

---

## Priority Matrix

| # | Feature | New Module(s) | Complexity | Priority | Reason |
|---|---------|--------------|------------|----------|--------|
| 1 | Context Compaction | `jclaw-compaction` | Medium | P0 | Critical for long conversations — without it sessions silently lose context |
| 2 | Workspace Memory | enhance `jclaw-memory` | Medium | P0 | Markdown-first memory is core to OpenClaw UX |
| 3 | Browser Automation | `jclaw-browser` | High | P1 | High-value tool — enables web research, form filling, scraping |
| 4 | Scheduling/Cron | `jclaw-cron` | Medium | P1 | Enables proactive agents (daily briefings, monitoring alerts) |
| 5 | Voice/PTT | `jclaw-voice` | Medium | P1 | Differentiating UX for mobile/desktop |
| 6 | Group Chat Routing | enhance `jclaw-gateway` | Low | P2 | Unlocks team/group use cases |
| 7 | Identity Linking | `jclaw-identity` | Low | P2 | Enables cross-channel user continuity |
| 8 | Canvas/A2UI | `jclaw-canvas` | Medium | P2 | Rich visual output for dashboards/reports |
| 9 | Device Nodes | `jclaw-node-sdk` | High | P3 | Requires companion native apps |
| 10 | Native Apps | separate repos | Very High | P3 | iOS/Android/macOS — long-term investment |

---

## Feature 1: Context Compaction

**Problem**: JClaw sessions grow unbounded. When conversation history exceeds the model's context window, messages are silently dropped or the API call fails.

**OpenClaw approach**: Auto-compaction triggers at ~80% of context window. Old messages are chunked, summarized (preserving identifiers, decisions, TODOs), and replaced with a `[Summary]` entry. Recent messages are kept intact.

### Module: `jclaw-compaction`

**Package**: `io.jclaw.compaction`

**Core Types** (in `jclaw-core`, no Spring dependency):
```java
record CompactionConfig(
    boolean enabled,            // default: true
    double triggerThreshold,    // 0.8 = 80% of context window
    int targetTokenPercent,     // 20 = keep summary to 20% of window
    String summaryModel         // null = use agent's primary model
)

record CompactionResult(
    String summary,
    int originalTokens,
    int compactedTokens,
    int messagesRemoved
)
```

**Key Classes**:

| Class | Responsibility |
|-------|---------------|
| `TokenEstimator` | Estimate token count for messages (cl100k_base approximation: ~4 chars/token) |
| `CompactionService` | Orchestrate: check budget → chunk old messages → summarize → replace |
| `IdentifierPreserver` | Validate that UUIDs, URLs, IPs, file paths survive summarization |
| `CompactionSummarizer` | Call LLM to summarize message chunks with identifier preservation instructions |

**Integration Points**:
- `AgentRuntime.executeSync()` — before building the ChatClient prompt, call `CompactionService.compactIfNeeded(session, contextWindowSize)`
- `SessionManager` — new method `replaceMessages(sessionKey, summary, keepRecent)` that atomically replaces old messages with a SystemMessage summary
- `JClawAutoConfiguration` — wire `CompactionService` bean (conditional on `jclaw.compaction.enabled`)

**Summarization Prompt** (from OpenClaw):
```
Summarize the following conversation chunk. Preserve ALL:
- Active tasks and their status
- Decisions made and constraints agreed upon
- File paths, URLs, UUIDs, IP addresses, and other identifiers (copy verbatim)
- User preferences and instructions
- TODO items and next steps
Be concise but complete. Do not invent information.
```

**Configuration**:
```yaml
jclaw:
  compaction:
    enabled: true
    trigger-threshold: 0.8
    target-token-percent: 20
    summary-model: ${COMPACTION_MODEL:}  # empty = use primary
```

**Tests**: `CompactionServiceSpec`, `TokenEstimatorSpec`, `IdentifierPreserverSpec`

---

## Feature 2: Workspace Memory (Markdown-First)

**Problem**: JClaw's memory is ephemeral (in-memory BM25 or VectorStore). OpenClaw uses markdown files as durable, human-readable memory that persists across sessions and is versioned with the workspace.

**OpenClaw approach**: `MEMORY.md` is curated long-term memory. `memory/YYYY-MM-DD.md` are daily append-only logs. Session transcripts persist as JSONL. Hybrid search (vector + BM25) across all sources.

### Enhance: `jclaw-memory`

**New Classes**:

| Class | Responsibility |
|-------|---------------|
| `WorkspaceMemoryManager` | Read/write MEMORY.md + daily logs from workspace directory |
| `SessionTranscriptStore` | Persist session transcripts as JSONL files |
| `HybridSearchManager` | Combine vector search + BM25 keyword search with temporal decay |
| `MemoryFlushService` | Agent-initiated durable note saving (called during compaction) |
| `DailyLogAppender` | Append structured notes to `memory/YYYY-MM-DD.md` |

**Workspace Layout**:
```
~/.jclaw/workspace/
├── MEMORY.md                    # Long-term curated memory
├── memory/
│   ├── 2026-03-18.md           # Daily log
│   └── 2026-03-17.md
└── sessions/
    ├── agent1:telegram:123:456.jsonl
    └── agent1:shell:local:user.jsonl
```

**JSONL Transcript Format**:
```json
{"ts":"2026-03-18T10:30:00Z","role":"user","content":"Check pod status"}
{"ts":"2026-03-18T10:30:05Z","role":"assistant","content":"All 12 pods running..."}
```

**Memory Search Enhancement**:
- `HybridSearchManager` implements `MemorySearchManager`
- Searches: MEMORY.md (full), daily logs (last 7 days), session transcripts (BM25 + vector)
- Results ranked by: relevance score * temporal_decay_factor
- Temporal decay: `score * exp(-age_days / half_life)` where half_life = 7.0

**New Tool**: `memory_save` — agent can explicitly save durable notes
```json
{
  "name": "memory_save",
  "description": "Save a note to long-term memory",
  "parameters": {
    "content": "The note to save",
    "section": "Optional section header in MEMORY.md"
  }
}
```

**Configuration**:
```yaml
jclaw:
  memory:
    workspace-dir: ${JCLAW_HOME:~/.jclaw}/workspace
    daily-logs: true
    transcript-persistence: true
    search:
      temporal-decay-half-life: 7.0
      hybrid-weight-vector: 0.7
      hybrid-weight-bm25: 0.3
```

**Tests**: `WorkspaceMemoryManagerSpec`, `SessionTranscriptStoreSpec`, `HybridSearchManagerSpec`, `DailyLogAppenderSpec`

---

## Feature 3: Browser Automation

**Problem**: JClaw has no browser tool. OpenClaw's browser tool (Playwright + dedicated Chromium) enables web research, form filling, scraping, and testing — one of the most used agent capabilities.

**OpenClaw approach**: Dedicated Chromium instance managed by Playwright. Multi-tab sessions, profile persistence (cookies/cache), DOM snapshots as accessibility trees, screenshot capture.

### Module: `jclaw-browser`

**Package**: `io.jclaw.browser`

**External Dependency**: `com.microsoft.playwright:playwright:1.49.0`

**Core Types**:

| Class | Responsibility |
|-------|---------------|
| `BrowserService` | Manage Playwright browser lifecycle, tab pool, profile loading |
| `BrowserSession` | Single tab session: navigate, click, type, screenshot, evaluate JS |
| `PageSnapshot` | Serialized DOM accessibility tree + metadata (URL, title, elements) |
| `BrowserProfileStore` | Persist cookies/localStorage/cache per profile name |

**Tools** (8 tools in section "Browser"):

| Tool | Parameters | Description |
|------|-----------|-------------|
| `browser_navigate` | `url` | Navigate to URL, return page snapshot |
| `browser_click` | `selector` | Click element by CSS/XPath/role selector |
| `browser_type` | `selector`, `text` | Type text into input field |
| `browser_screenshot` | `selector?`, `fullPage?` | Capture screenshot (returns base64) |
| `browser_evaluate` | `javascript` | Execute JavaScript, return result |
| `browser_read_page` | `selector?` | Read text content of page or element |
| `browser_list_tabs` | — | List open tabs with URLs |
| `browser_close_tab` | `tabId?` | Close a tab (or current tab) |

**Architecture**:
```
Agent calls browser_navigate("https://example.com")
  → BrowserService ensures Playwright browser is running
  → BrowserSession.navigate(url)
  → Playwright page.goto(url)
  → Wait for networkidle
  → Generate PageSnapshot (accessibility tree)
  → Return snapshot as tool result text
```

**PageSnapshot Format** (what the agent sees):
```
URL: https://example.com
Title: Example Domain

[1] heading "Example Domain"
[2] paragraph "This domain is for use in illustrative examples..."
[3] link "More information..." href="https://www.iana.org/..."
[4] form
  [5] input[text] name="search" placeholder="Search..."
  [6] button "Submit"
```

**Configuration**:
```yaml
jclaw:
  browser:
    enabled: false               # opt-in
    headless: true
    profiles-dir: ${JCLAW_HOME:~/.jclaw}/browser-profiles
    download-dir: /tmp/jclaw-downloads
    timeout: 30000
    viewport:
      width: 1280
      height: 720
```

**Auto-Configuration**: `BrowserToolsAutoConfiguration` — `@ConditionalOnProperty(name = "jclaw.browser.enabled", havingValue = "true")` + `@ConditionalOnClass(name = "com.microsoft.playwright.Playwright")`

**Tests**: `BrowserServiceSpec` (mock Playwright), `PageSnapshotSpec`, `BrowserToolsSpec`

---

## Feature 4: Scheduling/Cron

**Problem**: JClaw agents are purely reactive — they only respond to user messages. OpenClaw supports proactive agents via cron jobs (e.g., daily cluster health checks, morning briefings, periodic report generation).

**OpenClaw approach**: `CronService` with persistent job store. Jobs fire on schedule, run an isolated agent session, and deliver results to a channel.

### Module: `jclaw-cron`

**Package**: `io.jclaw.cron`

**External Dependency**: `com.cronutils:cron-utils:9.2.1` (cron expression parsing)

**Core Types** (in `jclaw-core`):
```java
record CronJob(
    String id,
    String name,
    String agentId,
    String schedule,        // Cron expression: "0 9 * * *"
    String timezone,        // e.g., "America/New_York"
    String prompt,          // What to tell the agent
    String deliveryChannel, // "telegram", "slack", "email", null (log only)
    String deliveryTarget,  // Channel-specific peer ID
    boolean enabled,
    Instant lastRunAt,
    Instant nextRunAt
)

sealed interface CronJobResult {
    record Success(String jobId, String runId, String agentResponse, Instant completedAt) implements CronJobResult {}
    record Failure(String jobId, String runId, String error, Instant failedAt) implements CronJobResult {}
}
```

**Key Classes**:

| Class | Responsibility |
|-------|---------------|
| `CronService` | Schedule management: start/stop/pause jobs, compute next run times |
| `CronJobStore` | Persistence: JSON file or SQLite. Load on startup, save on change |
| `CronJobExecutor` | Run isolated agent session with job prompt, capture response |
| `CronJobDelivery` | Deliver result to specified channel via `ChannelAdapter.sendMessage()` |
| `CronScheduleComputer` | Parse cron expression, compute next fire time with timezone |

**Integration**:
- `CronService` starts as a Spring `SmartLifecycle` bean (starts after gateway)
- Uses `ScheduledExecutorService` for timer management
- Each job fires → creates isolated `AgentRuntimeContext` → runs agent → delivers result
- Gateway REST endpoints for job CRUD

**REST API**:
```
POST   /api/cron/jobs          — Create job
GET    /api/cron/jobs          — List jobs
GET    /api/cron/jobs/{id}     — Get job
PATCH  /api/cron/jobs/{id}     — Update job
DELETE /api/cron/jobs/{id}     — Delete job
POST   /api/cron/jobs/{id}/run — Force run now
GET    /api/cron/jobs/{id}/runs — Job history
```

**Shell Commands**: `cron-list`, `cron-add`, `cron-remove`, `cron-run`, `cron-history`

**Example Use Case** (K8s monitoring):
```yaml
# Daily 9 AM cluster health check
name: daily-cluster-health
schedule: "0 9 * * *"
timezone: America/New_York
prompt: "Run a full cluster health check. Report any pods in CrashLoopBackOff, nodes NotReady, or deployments with mismatched replicas. Summarize resource utilization."
deliveryChannel: telegram
deliveryTarget: "-1001234567890"  # Telegram group ID
```

**Configuration**:
```yaml
jclaw:
  cron:
    enabled: false
    store-path: ${JCLAW_HOME:~/.jclaw}/cron-jobs.json
    max-concurrent-jobs: 3
    job-timeout: 300  # seconds
```

**Tests**: `CronServiceSpec`, `CronScheduleComputerSpec`, `CronJobExecutorSpec`

---

## Feature 5: Voice / TTS / STT

**Problem**: JClaw is text-only. OpenClaw supports voice input (Speech-to-Text via Whisper) and voice output (Text-to-Speech via ElevenLabs/OpenAI/Edge TTS), plus Talk Mode for continuous voice conversation.

**OpenClaw approach**: TTS provider abstraction (ElevenLabs, OpenAI, Edge). STT via OpenAI Whisper. TTS directives `[[tts:...]]` inline in agent responses. Talk Mode on native apps streams audio bidirectionally.

### Module: `jclaw-voice`

**Package**: `io.jclaw.voice`

**Sub-packages**:
- `io.jclaw.voice.tts` — Text-to-Speech providers
- `io.jclaw.voice.stt` — Speech-to-Text providers
- `io.jclaw.voice.config` — Configuration

**External Dependencies**:
- OpenAI TTS API (via Spring AI or direct HTTP)
- ElevenLabs REST API
- OpenAI Whisper API (STT)

**Core Types**:

| Interface/Class | Responsibility |
|----------------|---------------|
| `TtsProvider` (SPI) | `synthesize(text, voice, options) → AudioResult` |
| `SttProvider` (SPI) | `transcribe(audioBytes, mimeType) → TranscriptionResult` |
| `OpenAiTtsProvider` | OpenAI TTS (tts-1, tts-1-hd) |
| `ElevenLabsTtsProvider` | ElevenLabs streaming TTS |
| `EdgeTtsProvider` | Azure Edge TTS (free, no API key) |
| `OpenAiSttProvider` | OpenAI Whisper transcription |
| `TtsDirectiveParser` | Parse `[[tts:...]]` directives from agent text |
| `VoiceService` | Orchestrate TTS/STT with fallback chain |

```java
// Core records (in jclaw-core)
record AudioResult(byte[] audioData, String mimeType, int durationMs) {}
record TranscriptionResult(String text, String language, double confidence) {}
record TtsDirective(String provider, Map<String, String> params, String text) {}
```

**TTS Directives** (inline voice control in agent responses):
```
Agent says: "Here's the weather forecast. [[tts:voice=alloy]]The temperature is 72 degrees.[[/tts]]"
  → TtsDirectiveParser extracts segments
  → Each segment synthesized with specified voice/provider
  → Audio chunks concatenated and returned
```

**Gateway Integration**:
- `POST /api/voice/tts` — Convert text to audio (returns audio/mpeg)
- `POST /api/voice/stt` — Convert audio to text (accepts audio/*)
- WebSocket: Binary audio frames for streaming TTS/STT

**Channel Integration**:
- Telegram: Voice messages auto-transcribed via STT, responses optionally TTS'd
- Shell: Audio playback via system `afplay` (macOS) or `aplay` (Linux)

**Configuration**:
```yaml
jclaw:
  voice:
    tts:
      provider: openai   # openai, elevenlabs, edge
      auto: off           # off, always, inbound, tagged
      openai:
        model: tts-1
        voice: alloy
      elevenlabs:
        api-key: ${ELEVENLABS_API_KEY:}
        voice-id: pMsXgVXv3BLzUgSXRplE
        model-id: eleven_turbo_v2_5
      edge:
        voice: en-US-AriaNeural
    stt:
      provider: openai
      openai:
        model: whisper-1
```

**Tests**: `TtsDirectiveParserSpec`, `VoiceServiceSpec`, `OpenAiTtsProviderSpec` (mock HTTP)

---

## Feature 6: Group Chat Routing

**Problem**: JClaw treats every inbound message the same. In group chats (Telegram groups, Slack channels, Discord servers), the agent should only activate when mentioned, and each group should have its own isolated session.

**OpenClaw approach**: Binding rules map (channel + peer) → agent. Group messages create per-group sessions. @mention extraction is channel-specific.

### Enhance: `jclaw-gateway` + `jclaw-channel-api`

**New Types** (in `jclaw-core`):
```java
enum ChatType { DIRECT, GROUP, CHANNEL, THREAD }

record RoutingBinding(
    String agentId,
    String channel,
    String peerKind,      // "direct", "group", "channel"
    String peerId,        // null = wildcard
    boolean mentionOnly   // true = only respond when @mentioned in groups
)
```

**Changes to `ChannelMessage`**: Add `chatType` and `mentionedBotIds` fields.

**Changes to `GatewayService`**:
1. Check `chatType` — if GROUP and `mentionOnly`, only process if bot is in `mentionedBotIds`
2. Session key includes chat type: `{agentId}:{channel}:group:{groupId}`
3. Load routing bindings from config

**Channel-Specific @Mention Parsing**:
- Telegram: `/command@botname` or reply-to-bot
- Slack: `<@BOT_USER_ID>` in message text
- Discord: `<@BOT_USER_ID>` in message content

**Configuration**:
```yaml
jclaw:
  routing:
    default-group-behavior: mention-only  # mention-only, always, ignore
    bindings:
      - channel: telegram
        peer-kind: group
        peer-id: "-1001234567890"
        mention-only: true
```

**Tests**: `RoutingServiceSpec`, `MentionParserSpec`

---

## Feature 7: Identity Linking

**Problem**: A user who talks to the agent on Telegram and Slack is treated as two separate users with separate sessions and memory. OpenClaw links identities so the agent recognizes the same person across channels.

### Module: `jclaw-identity`

**Package**: `io.jclaw.identity`

**Core Types** (in `jclaw-core`):
```java
record IdentityLink(
    String canonicalUserId,   // UUID — the "real" user
    String channel,           // "telegram"
    String channelUserId      // "123456789"
)

interface IdentityLinkStore {
    void link(String canonicalUserId, String channel, String channelUserId);
    void unlink(String channel, String channelUserId);
    Optional<String> resolveCanonicalId(String channel, String channelUserId);
    List<IdentityLink> getLinksForUser(String canonicalUserId);
}
```

**Key Classes**:

| Class | Responsibility |
|-------|---------------|
| `IdentityLinkStore` | Persist links (JSON file or SQLite) |
| `IdentityResolver` | Given (channel, channelUserId), return canonical user ID |
| `IdentityLinkService` | Link/unlink operations with validation |

**Integration**:
- `GatewayService.onMessage()` — resolve canonical user ID before computing session key
- Session key becomes: `{agentId}:main:{canonicalUserId}` for DMs (shared across channels)
- Per-group sessions remain channel-specific

**Shell Commands**: `identity-link`, `identity-unlink`, `identity-list`

**Configuration**:
```yaml
jclaw:
  identity:
    enabled: false
    auto-link: false       # true = auto-link first message from same phone/email
    store-path: ${JCLAW_HOME:~/.jclaw}/identity-links.json
```

**Tests**: `IdentityLinkServiceSpec`, `IdentityResolverSpec`

---

## Feature 8: Canvas / A2UI

**Problem**: JClaw can only output text. OpenClaw's Canvas enables agents to push rich visual content (HTML/CSS/JS dashboards, charts, forms) to connected clients.

### Module: `jclaw-canvas`

**Package**: `io.jclaw.canvas`

**Architecture**: Embedded HTTP server (separate port) serves agent-generated HTML. Connected clients (WebSocket, mobile apps) receive canvas URLs and render in WebView.

**Core Types**:
```java
record CanvasAction(String type, Map<String, Object> params) {}
// Types: "present", "hide", "navigate", "eval", "snapshot"

record CanvasState(String sessionId, String currentUrl, boolean visible) {}
```

**Key Classes**:

| Class | Responsibility |
|-------|---------------|
| `CanvasHostServer` | Embedded Netty/Jetty HTTP server on configurable port (default 18793) |
| `CanvasFileManager` | Write agent-generated HTML to temp dir, serve via CanvasHostServer |
| `CanvasWebSocketHandler` | Live reload: notify clients when content changes |
| `CanvasService` | Orchestrate: create content → serve → notify clients → capture snapshot |

**Tools** (3 tools in section "Canvas"):

| Tool | Parameters | Description |
|------|-----------|-------------|
| `canvas_present` | `html` or `url` | Push HTML content or URL to connected clients |
| `canvas_eval` | `javascript` | Execute JS in the canvas context |
| `canvas_snapshot` | — | Capture screenshot of current canvas (returns base64) |

**Configuration**:
```yaml
jclaw:
  canvas:
    enabled: false
    port: 18793
    host: 127.0.0.1
    live-reload: true
```

**Tests**: `CanvasHostServerSpec`, `CanvasServiceSpec`

---

## Feature 9: Device Nodes

**Problem**: JClaw has no access to mobile device capabilities (camera, location, screen, contacts). OpenClaw's node system allows iOS/Android devices to connect as sensor nodes.

### Module: `jclaw-node-sdk`

**Package**: `io.jclaw.node`

**Architecture**: Device nodes connect to the gateway via WebSocket, authenticate with Ed25519 key pairs, and expose local capabilities as remotely invocable commands.

**Core Types** (in `jclaw-core`):
```java
record DeviceIdentity(String deviceId, String publicKeyPem) {}
record NodeCapability(String command, String description) {}  // e.g., "camera.snap"
record NodeRegistration(DeviceIdentity identity, String displayName, List<NodeCapability> capabilities) {}
sealed interface NodeCommandResult {
    record Success(String data, String mimeType, Map<String, Object> metadata) implements NodeCommandResult {}
    record Failure(String error) implements NodeCommandResult {}
}
```

**Key Classes**:

| Class | Responsibility |
|-------|---------------|
| `NodeRegistry` | Track connected nodes + capabilities |
| `NodeAuthenticator` | Ed25519 signature verification for pairing |
| `NodePairingService` | Approve/reject pairing requests |
| `NodeCommandDispatcher` | Route tool calls to connected nodes |
| `NodeWebSocketHandler` | WebSocket endpoint for node connections |

**Node Commands** (exposed as agent tools):

| Tool | Description |
|------|-------------|
| `node_camera_snap` | Take photo from device camera |
| `node_camera_clip` | Record video clip |
| `node_screen_record` | Record device screen |
| `node_location` | Get GPS coordinates |
| `node_notify` | Send push notification to device |
| `node_list` | List connected nodes and capabilities |

**This is a framework** — actual iOS/Android apps would be separate repos (`jclaw-ios`, `jclaw-android`) that implement the node protocol and expose local device APIs.

**Configuration**:
```yaml
jclaw:
  nodes:
    enabled: false
    pairing-mode: manual   # manual, auto-approve
    ws-path: /ws/node
```

**Tests**: `NodeRegistrySpec`, `NodeAuthenticatorSpec`, `NodeCommandDispatcherSpec`

---

## Feature 10: Native Apps (Long-Term)

This is the largest effort and would be separate projects. Documented here for completeness.

| App | Tech Stack | Features |
|-----|-----------|----------|
| **jclaw-android** | Kotlin + Jetpack Compose | Chat, Voice, Canvas WebView, Camera/Screen/Location, Device commands |
| **jclaw-ios** | Swift + SwiftUI | Chat, Voice, Canvas WKWebView, Camera/Screen/Location |
| **jclaw-desktop** | JavaFX or Electron | Menu bar, Chat, Canvas, Voice Wake, Talk Mode |

Each app connects to jclaw-gateway via WebSocket (chat) and the node protocol (device commands). They are separate repositories with their own build systems.

---

## Implementation Roadmap

### Phase 1 — Core Agent Intelligence (P0, ~2 weeks)
1. **Context Compaction** (`jclaw-compaction`) — prevent silent context loss
2. **Workspace Memory** (enhance `jclaw-memory`) — durable markdown memory + session transcripts

### Phase 2 — High-Value Tools (P1, ~3 weeks)
3. **Browser Automation** (`jclaw-browser`) — Playwright-based web interaction
4. **Scheduling/Cron** (`jclaw-cron`) — proactive agent scheduling
5. **Voice/TTS/STT** (`jclaw-voice`) — audio I/O for all channels

### Phase 3 — Multi-User & UX (P2, ~2 weeks)
6. **Group Chat Routing** (enhance `jclaw-gateway`) — @mention activation
7. **Identity Linking** (`jclaw-identity`) — cross-channel user mapping
8. **Canvas/A2UI** (`jclaw-canvas`) — rich visual output

### Phase 4 — Device Ecosystem (P3, ~4+ weeks)
9. **Device Nodes** (`jclaw-node-sdk`) — node registration + command framework
10. **Native Apps** (separate repos) — iOS, Android, Desktop

### Module Count After Full Implementation
Current: 33 modules → After Phase 3: 39 modules (+6 new)

---

## File Changes Summary (Phase 1-3)

### New Modules
| Module | Type | Description |
|--------|------|-------------|
| `jclaw-compaction` | Library | Context compaction service |
| `jclaw-browser` | Library + tools | Playwright browser automation |
| `jclaw-cron` | Library | Cron job scheduling |
| `jclaw-voice` | Library | TTS/STT provider abstraction |
| `jclaw-identity` | Library | Cross-channel identity linking |
| `jclaw-canvas` | Library | A2UI canvas host |

### Modified Modules
| Module | Changes |
|--------|---------|
| `jclaw-core` | Add `ChatType`, `IdentityLink`, `CronJob`, `AudioResult`, `TranscriptionResult`, `CanvasAction` records |
| `jclaw-memory` | Add `WorkspaceMemoryManager`, `SessionTranscriptStore`, `HybridSearchManager`, `DailyLogAppender` |
| `jclaw-agent` | Integrate compaction into `AgentRuntime.executeSync()` |
| `jclaw-gateway` | Add group chat routing, @mention filtering, canvas WebSocket |
| `jclaw-channel-api` | Add `chatType`, `mentionedBotIds` to `ChannelMessage` |
| `jclaw-tools` | Add `SECTION_BROWSER`, `SECTION_CANVAS` to `ToolCatalog` |
| `jclaw-spring-boot-starter` | Wire new auto-configs |
| `pom.xml` (parent) | Add new modules |
| `jclaw-bom/pom.xml` | Add new module entries |
| `jclaw-starters/pom.xml` | Optionally add new starters |
