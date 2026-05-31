# Phase 5 — P2 Media & Voice — Progress Tracker

**Started:** 2026-05-29
**Status:** COMPLETE
**Depends on:** Phase 1 complete (1.4 Image Generation establishes pattern)

## Work Items

### 5.1 Video Generation
- **Location:** `extensions/jaiclaw-video-generation/`
- **Status:** COMPLETE
- **Tests:** 25 passing
- **Approach:** Fully custom REST client wrappers with async polling pattern. No Java SDK exists for video generation.
- **Files created:**
  - [x] `extensions/jaiclaw-video-generation/pom.xml`
  - [x] `VideoJobStatus.java` — enum: QUEUED, PROCESSING, COMPLETED, FAILED
  - [x] `VideoCapabilities.java` — record: supportedResolutions, maxDurationSeconds, supportsTextInput, supportsImageInput
  - [x] `VideoGenerationRequest.java` — record with builder: prompt, imageUrl, durationSecs, resolution, options
  - [x] `VideoGenerationResult.java` — record with factory methods: queued(), processing(), completed(), failed()
  - [x] `VideoGenerationProvider.java` — SPI interface: providerId(), submit(), poll(), capabilities()
  - [x] `VideoGenerationRegistry.java` — provider registry with ConcurrentHashMap, submit/poll delegation
  - [x] `VideoConfig.java` — record: defaultProvider, runwayApiKey, runwayModel
  - [x] `RunwayVideoProvider.java` — Runway Gen-3 API: submit to `/v1/image_to_video`, poll `/v1/tasks/{jobId}`
  - [x] `VideoGenerationTool.java` — ToolCallback with generate/status actions
  - [x] `VideoGenerationSpec.groovy` — 25 tests covering all records, registry, provider, and tool

### 5.2 ElevenLabs TTS Provider
- **Location:** `extensions/jaiclaw-voice/src/main/java/io/jaiclaw/voice/tts/`
- **Status:** COMPLETE
- **Tests:** 9 passing (new)
- **Approach:** Native HTTP implementation using `java.net.http.HttpClient` for the ElevenLabs `/v1/text-to-speech/{voice_id}` endpoint. Avoids pulling Spring AI ElevenLabs starter since it was not in local Maven cache.
- **Files created:**
  - [x] `ElevenLabsTtsProvider.java` — TtsProvider impl using HttpClient, voice ID resolution, model configuration
  - [x] `ElevenLabsTtsProviderSpec.groovy` — 9 tests: providerId, success, HTTP error, connection error, voice ID override, default voice fallback, voice ID pass-through, null defaults, default param call

### 5.3 Deepgram STT Provider
- **Location:** `extensions/jaiclaw-voice/src/main/java/io/jaiclaw/voice/stt/`
- **Status:** COMPLETE
- **Tests:** 10 passing (new)
- **Approach:** Native HTTP implementation using `java.net.http.HttpClient` for the Deepgram `/v1/listen` endpoint. Avoids pulling Deepgram Java SDK since it was not in local Maven cache.
- **Files created:**
  - [x] `DeepgramSttProvider.java` — SttProvider impl using HttpClient, JSON parsing with Jackson, language detection
  - [x] `DeepgramSttProviderSpec.groovy` — 10 tests: providerId, success, HTTP error, connection error, query params, empty channels, empty alternatives, null mime type, default model, non-English language detection

### Configuration & Wiring
- **Status:** COMPLETE
- **Files created/modified:**
  - [x] `core/jaiclaw-config/src/main/java/io/jaiclaw/config/VoiceProperties.java` — new record with TTS/STT/ElevenLabs/Deepgram fields
  - [x] `core/jaiclaw-config/src/main/java/io/jaiclaw/config/VideoProperties.java` — new record with Runway config fields
  - [x] `core/jaiclaw-config/src/main/java/io/jaiclaw/config/JaiClawProperties.java` — added `voice` and `video` fields
  - [x] `extensions/jaiclaw-voice/src/main/java/io/jaiclaw/voice/config/VoiceConfig.java` — added deepgramApiKey and deepgramModel fields
  - [x] `extensions/jaiclaw-voice/pom.xml` — added byte-buddy and objenesis test deps for mocking concrete classes
  - [x] `jaiclaw-spring-boot-starter/src/main/java/io/jaiclaw/autoconfigure/JaiClawAutoConfiguration.java` — added ElevenLabsTtsAutoConfiguration, DeepgramSttAutoConfiguration, VideoGenerationAutoConfiguration nested classes
  - [x] `jaiclaw-spring-boot-starter/pom.xml` — added jaiclaw-voice and jaiclaw-video-generation as optional deps
  - [x] `extensions/pom.xml` — added jaiclaw-video-generation module
  - [x] `jaiclaw-bom/pom.xml` — added jaiclaw-video-generation to dependency management
  - [x] `pom.xml` (root) — added jaiclaw-video-generation to dependency management

## Test Summary

| Module | New Tests | Total Tests |
|--------|-----------|-------------|
| jaiclaw-video-generation | 25 | 25 |
| jaiclaw-voice (ElevenLabs) | 9 | 28 |
| jaiclaw-voice (Deepgram) | 10 | 28 |
| **Phase 5 total new** | **44** | — |

## Configuration

### Video Generation
```yaml
jaiclaw:
  video:
    default-provider: runway
    runway-api-key: ${RUNWAY_API_KEY}
    runway-model: gen3a_turbo
```

### ElevenLabs TTS
```yaml
jaiclaw:
  voice:
    tts-provider: elevenlabs
    elevenlabs-api-key: ${ELEVENLABS_API_KEY}
    elevenlabs-voice-id: 21m00Tcm4TlvDq8ikWAM
    elevenlabs-model-id: eleven_monolingual_v1
```

### Deepgram STT
```yaml
jaiclaw:
  voice:
    stt-provider: deepgram
    deepgram-api-key: ${DEEPGRAM_API_KEY}
    deepgram-model: nova-2
```

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 5.2 ElevenLabs: Spring AI starter → 2-3 hours (was 1-3 days)
  - 5.3 Deepgram: Java SDK wrapper → half day (was 1-3 days)
  - 5.1 Video: Unchanged — no library exists, fully custom

### Session 2 — 2026-05-29
- Implemented all Phase 5 items:
  - 5.1 Video Generation: Full custom module with Runway provider, async polling, 25 tests
  - 5.2 ElevenLabs TTS: Native HTTP provider (Spring AI ElevenLabs not in local cache), 9 tests
  - 5.3 Deepgram STT: Native HTTP provider (Deepgram SDK not in local cache), 10 tests
- Wired auto-configuration for all three in JaiClawAutoConfiguration
- Added VoiceProperties and VideoProperties to JaiClawProperties
- Updated VoiceConfig with Deepgram fields
- Full build passes (all modules compile and all tests pass)
