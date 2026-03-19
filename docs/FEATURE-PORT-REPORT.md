# OpenClaw Feature Port — Change Report

**Date:** 2026-03-19
**Modules before:** 33 | **Modules after:** 39 (+6 new)
**Tests before:** 376 | **Tests after:** 462 (+86 new)

## Summary

Ported 8 features from the OpenClaw Feature Port Plan. All existing tests remain green; no existing functionality was changed. Six new modules were created and two existing modules were enhanced.

---

## New Modules

| Module | Artifact | Description | Classes | Tests |
|--------|----------|-------------|---------|-------|
| **Compaction** | `jclaw-compaction` | Context window compaction via summarization | 4 | 17 |
| **Browser** | `jclaw-browser` | Browser automation via Playwright (reflection-based) | 5 | 5 |
| **Cron** | `jclaw-cron` | Cron job scheduling for proactive agents | 4 | 11 |
| **Voice** | `jclaw-voice` | TTS/STT with provider fallback chain | 7 | 9 |
| **Identity** | `jclaw-identity` | Cross-channel identity linking | 3 | 7 |
| **Canvas** | `jclaw-canvas` | Canvas/A2UI artifact rendering | 4 | 8 |

## Enhanced Modules

| Module | Changes | New Tests |
|--------|---------|-----------|
| **jclaw-memory** | +5 classes (WorkspaceMemoryManager, DailyLogAppender, SessionTranscriptStore, HybridSearchManager, MemorySaveTool); +3 enum values to MemorySource | 15 |
| **jclaw-gateway** | +2 classes (RoutingService, MentionParser) in `routing/` package | 14 |

## Modified Build/Config Files

| File | Change |
|------|--------|
| `pom.xml` (parent) | Added 6 modules to `<modules>` list and `<dependencyManagement>` |
| `jclaw-bom/pom.xml` | Added 6 module entries to `<dependencyManagement>` |
| `jclaw-tools/ToolCatalog.java` | Added `SECTION_BROWSER` and `SECTION_CANVAS` constants |
| `jclaw-memory/MemorySource.java` | Added `WORKSPACE`, `DAILY_LOG`, `TRANSCRIPT` enum values |

## New Core Types (jclaw-core)

All are pure Java records/enums with zero Spring dependency:

| Type | Purpose |
|------|---------|
| `CompactionConfig` | Token budget, threshold, target percent |
| `CompactionResult` | Summary text, token counts, messages removed |
| `CronJob` | Scheduled job definition (cron expression, agent, prompt) |
| `CronJobResult` | Sealed: Success/Failure for job execution |
| `AudioResult` | TTS output (bytes, mime type, duration) |
| `TranscriptionResult` | STT output (text, language, confidence) |
| `ChatType` | Enum: DIRECT, GROUP, CHANNEL, THREAD |
| `RoutingBinding` | Agent-to-chat routing rule with matching |
| `IdentityLink` | Cross-channel user identity mapping |
| `CanvasAction` | Canvas operation (type + params map) |

## Detailed File Listing

### Feature 1: Context Compaction (`jclaw-compaction`)

```
jclaw-compaction/
├── pom.xml
└── src/
    ├── main/java/io/jclaw/compaction/
    │   ├── TokenEstimator.java          — ~4 chars/token heuristic, overloads for String/Message/List
    │   ├── IdentifierPreserver.java      — Regex extraction of UUIDs, URLs, IPs, file paths
    │   ├── CompactionSummarizer.java     — LLM call abstracted via Function<String, String>
    │   └── CompactionService.java        — Orchestrator: compactIfNeeded() + applyCompaction()
    └── test/groovy/io/jclaw/compaction/
        ├── TokenEstimatorSpec.groovy     — 5 tests
        ├── IdentifierPreserverSpec.groovy — 7 tests
        └── CompactionServiceSpec.groovy  — 5 tests
```

### Feature 2: Workspace Memory (`jclaw-memory` enhancements)

```
jclaw-memory/src/main/java/io/jclaw/memory/
├── WorkspaceMemoryManager.java    — Read/write MEMORY.md, section append
├── DailyLogAppender.java          — Append to memory/YYYY-MM-DD.md daily logs
├── SessionTranscriptStore.java    — JSONL transcript persistence
├── HybridSearchManager.java       — Keyword search + temporal decay scoring
└── MemorySaveTool.java            — ToolCallback for agent to save notes

jclaw-memory/src/test/groovy/io/jclaw/memory/
├── WorkspaceMemoryManagerSpec.groovy   — 4 tests
├── DailyLogAppenderSpec.groovy         — 3 tests
├── SessionTranscriptStoreSpec.groovy   — 4 tests
└── HybridSearchManagerSpec.groovy      — 4 tests
```

### Feature 3: Browser Automation (`jclaw-browser`)

```
jclaw-browser/
├── pom.xml                              — Playwright 1.49.0 (optional dep)
└── src/
    ├── main/java/io/jclaw/browser/
    │   ├── PageSnapshot.java            — Record with PageElement list, toText() accessor tree
    │   ├── BrowserConfig.java           — Config: headless, viewport, timeout, profile dir
    │   ├── BrowserService.java          — Lazy Playwright init via reflection, session pool
    │   ├── BrowserSession.java          — Navigate, click, type, screenshot, evaluate via reflection
    │   └── BrowserTools.java            — 8 tools: navigate, click, type, screenshot, evaluate, read_page, list_tabs, close_tab
    └── test/groovy/io/jclaw/browser/
        ├── PageSnapshotSpec.groovy      — 2 tests
        └── BrowserToolsSpec.groovy      — 3 tests
```

### Feature 4: Scheduling/Cron (`jclaw-cron`)

```
jclaw-cron/
├── pom.xml
└── src/
    ├── main/java/io/jclaw/cron/
    │   ├── CronScheduleComputer.java    — 5-field cron parser, brute-force next fire time
    │   ├── CronJobStore.java            — JSON file persistence (Jackson + JavaTimeModule)
    │   ├── CronJobExecutor.java         — Agent call abstracted via Function<CronJob, String>
    │   └── CronService.java             — ScheduledExecutorService, job CRUD, run history
    └── test/groovy/io/jclaw/cron/
        ├── CronScheduleComputerSpec.groovy — 6 tests
        └── CronServiceSpec.groovy          — 5 tests
```

### Feature 5: Voice/TTS/STT (`jclaw-voice`)

```
jclaw-voice/
├── pom.xml                               — spring-web (for RestClient)
└── src/
    ├── main/java/io/jclaw/voice/
    │   ├── tts/TtsProvider.java          — SPI: synthesize(text, voice, options)
    │   ├── tts/OpenAiTtsProvider.java    — REST: POST /v1/audio/speech
    │   ├── stt/SttProvider.java          — SPI: transcribe(audioBytes, mimeType)
    │   ├── stt/OpenAiSttProvider.java    — Multipart POST /v1/audio/transcriptions
    │   ├── TtsDirectiveParser.java       — Parses [[tts:voice=alloy]]text[[/tts]] markup
    │   ├── VoiceService.java             — Provider fallback chain for TTS/STT
    │   └── config/VoiceConfig.java       — Configuration record
    └── test/groovy/io/jclaw/voice/
        ├── TtsDirectiveParserSpec.groovy  — 5 tests
        └── VoiceServiceSpec.groovy        — 4 tests
```

### Feature 6: Group Chat Routing (`jclaw-gateway` enhancements)

```
jclaw-gateway/src/main/java/io/jclaw/gateway/routing/
├── RoutingService.java           — shouldProcess() + resolveAgentId() for group/DM routing
└── MentionParser.java            — Channel-specific @mention extraction (Slack/Discord/Telegram)

jclaw-gateway/src/test/groovy/io/jclaw/gateway/routing/
├── RoutingServiceSpec.groovy     — 8 tests
└── MentionParserSpec.groovy      — 6 tests
```

### Feature 7: Identity Linking (`jclaw-identity`)

```
jclaw-identity/
├── pom.xml
└── src/
    ├── main/java/io/jclaw/identity/
    │   ├── IdentityLinkStore.java       — JSON file persistence, ConcurrentHashMap
    │   ├── IdentityResolver.java        — Resolves canonical user ID from channel+userId
    │   └── IdentityLinkService.java     — Link/unlink with auto UUID generation
    └── test/groovy/io/jclaw/identity/
        ├── IdentityLinkServiceSpec.groovy — 4 tests
        └── IdentityResolverSpec.groovy    — 3 tests
```

### Feature 8: Canvas/A2UI (`jclaw-canvas`)

```
jclaw-canvas/
├── pom.xml
└── src/
    ├── main/java/io/jclaw/canvas/
    │   ├── CanvasConfig.java            — Config: port 18793, host 127.0.0.1
    │   ├── CanvasFileManager.java       — Write/read HTML artifacts to temp directory
    │   ├── CanvasService.java           — Orchestrates present/hide/getCurrentContent
    │   └── CanvasTools.java             — 3 tools: canvas_present, canvas_eval, canvas_snapshot
    └── test/groovy/io/jclaw/canvas/
        ├── CanvasServiceSpec.groovy     — 4 tests
        └── CanvasToolsSpec.groovy       — 4 tests
```

## Notes

- All new modules follow the existing project conventions: pure Java records in `jclaw-core`, Spock tests, SPI interfaces
- Browser module uses reflection to avoid hard compile-time dependency on Playwright (optional at runtime)
- Voice module uses provider SPI pattern matching existing channel adapter design
- Cron module uses virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
- No existing tests were modified; all 376 pre-existing tests still pass
