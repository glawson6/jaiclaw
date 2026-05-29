# Phase 5 — P2 Media & Voice — Progress Tracker

**Started:** —
**Status:** Not Started
**Depends on:** Phase 1 complete (1.4 Image Generation establishes pattern)

## Work Items

### 5.1 Video Generation
- **Location:** New module `extensions/jaiclaw-video-generation/`
- **Status:** NOT STARTED
- **Revised estimate:** 2+ weeks (unchanged — 100% custom, no library exists)
- **Depends on:** 1.4 (follows same tool pattern, but no Spring AI video model)
- **Approach:** Fully custom REST client wrappers. Spring AI has **no video generation model API**. Each provider (Runway, Sora, Veo) has different REST APIs with no Java SDKs. These are all async/polling APIs (submit job → poll for completion).
- **No usable library.** Build custom `VideoGenerationProvider` SPI + per-provider REST clients.
- **Files to create:**
  - [ ] `extensions/jaiclaw-video-generation/pom.xml`
  - [ ] `VideoGenerationProvider.java` (SPI — ~30 lines)
  - [ ] `VideoGenerationRegistry.java`
  - [ ] `VideoGenerationRequest.java`, `VideoGenerationResult.java`, `VideoCapabilities.java`
  - [ ] `VideoJobStatus.java` (enum: QUEUED, PROCESSING, COMPLETED, FAILED)
  - [ ] `RunwayVideoProvider.java` (REST client — ~100-150 lines)
  - [ ] `VideoGenerationTool.java` (ToolCallback)
  - [ ] Config + auto-configuration
  - [ ] Spock specs
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-video-generation/pom.xml`
- **Key design:** Async polling via `CompletableFuture`. The tool submits a job and returns a job ID. A separate poll mechanism checks status.
- **Notes:** ~200 lines SPI + ~100-150 lines per provider. This is the most effort of any item in Phase 5.

### 5.2 ElevenLabs TTS Provider
- **Location:** Enhance existing `extensions/jaiclaw-voice/`
- **Status:** NOT STARTED
- **Revised estimate:** 2-3 hours (down from 1-3 days)
- **Approach:** Use **Spring AI ElevenLabs** (`spring-ai-starter-model-elevenlabs`). Spring AI 1.1.x includes a full ElevenLabs integration with `ElevenLabsTextToSpeechModel`, streaming support, voice selection, and multiple audio formats. Auto-configured.
- **Library:** `org.springframework.ai:spring-ai-starter-model-elevenlabs`
- **What Spring AI provides (~90% coverage):**
  - `ElevenLabsTextToSpeechModel` with streaming
  - Voice selection by voice ID
  - Multiple audio formats (mp3, pcm, etc.)
  - Auto-configuration — add starter + API key
- **What to build:**
  - [ ] Add `spring-ai-starter-model-elevenlabs` as optional dependency
  - [ ] `ElevenLabsTtsProvider.java` — `TtsProvider` wrapping Spring AI's model (~40-60 lines)
  - [ ] Auto-configure `@ConditionalOnBean(ElevenLabsTextToSpeechModel.class)`
  - [ ] Spock spec
- **No need to build:** Custom REST client, custom audio stream handling, custom config — Spring AI handles everything.
- **Configuration:**
  ```yaml
  spring:
    ai:
      elevenlabs:
        api-key: ${ELEVENLABS_API_KEY}
        voice:
          voice-id: 21m00Tcm4TlvDq8ikWAM
        model: eleven_multilingual_v2
  ```
- **Notes:** ~40-60 lines. Just bridge Spring AI's model to JaiClaw's `TtsProvider` SPI.

### 5.3 Deepgram STT Provider
- **Location:** Enhance existing `extensions/jaiclaw-voice/`
- **Status:** NOT STARTED
- **Revised estimate:** half day (down from 1-3 days)
- **Approach:** Use **Deepgram Java SDK** (`com.deepgram:deepgram-java-sdk:0.2.0`). Spring AI only supports OpenAI Whisper for STT, not Deepgram.
- **Library:** `com.deepgram:deepgram-java-sdk:0.2.0`
- **What the SDK provides (~75% coverage):**
  - Pre-recorded audio transcription
  - WebSocket streaming transcription
  - Language detection
  - Smart formatting
- **What to build:**
  - [ ] Add `deepgram-java-sdk` as optional dependency
  - [ ] `DeepgramSttProvider.java` — `SttProvider` wrapping SDK (~60-80 lines)
  - [ ] `DeepgramConfig.java` — config record
  - [ ] Auto-configure when Deepgram API key is present
  - [ ] Spock spec
- **No need to build:** Custom REST client, custom WebSocket handling — SDK handles transport.
- **Configuration:**
  ```yaml
  jaiclaw:
    voice:
      stt:
        provider: deepgram
        deepgram:
          api-key: ${DEEPGRAM_API_KEY}
          model: nova-2
          language: en
  ```
- **Notes:** ~60-80 lines. SDK wrapper.

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 5.2 ElevenLabs: Spring AI starter → 2-3 hours (was 1-3 days)
  - 5.3 Deepgram: Java SDK wrapper → half day (was 1-3 days)
  - 5.1 Video: Unchanged — no library exists, fully custom
