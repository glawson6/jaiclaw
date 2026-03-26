# Voice — TTS/STT

Module: `jaiclaw-voice`

## Overview

Provides text-to-speech (TTS) and speech-to-text (STT) capabilities via a provider SPI with fallback chain. Includes an OpenAI provider implementation and a directive parser for inline voice markup.

## Provider SPI

### TTS Provider

```java
public interface TtsProvider {
    String providerId();
    AudioResult synthesize(String text, String voice, Map<String, String> options);

    // Default convenience method
    default AudioResult synthesize(String text, String voice) {
        return synthesize(text, voice, Map.of());
    }
}
```

### STT Provider

```java
public interface SttProvider {
    String providerId();
    TranscriptionResult transcribe(byte[] audioBytes, String mimeType);
}
```

## Built-in Provider: OpenAI

- **TTS**: `OpenAiTtsProvider` — POST to `/v1/audio/speech` with RestClient
- **STT**: `OpenAiSttProvider` — Multipart POST to `/v1/audio/transcriptions`

Both require `OPENAI_API_KEY` environment variable.

## VoiceService

Orchestrates providers with automatic fallback:

```java
VoiceService voice = new VoiceService(
    List.of(openAiTts),      // TTS providers (tried in order)
    List.of(openAiStt),      // STT providers (tried in order)
    "alloy"                   // default voice
);

// Text to speech
Optional<AudioResult> audio = voice.synthesize("Hello world");
Optional<AudioResult> custom = voice.synthesize("Hello", "nova");

// Speech to text
Optional<TranscriptionResult> text = voice.transcribe(audioBytes, "audio/ogg");
```

If a provider throws an exception, the next provider in the list is tried. Returns `Optional.empty()` if all providers fail.

## TTS Directive Parser

Allows inline voice directives in agent responses:

```
Here is a normal text response.

[[tts:voice=nova]]This part will be spoken in the Nova voice.[[/tts]]

More text here.

[[tts:voice=alloy]]And this in Alloy.[[/tts]]
```

```java
TtsDirectiveParser parser = new TtsDirectiveParser();

List<TtsSegment> segments = parser.parse(text);
// Each segment has: text, voice, params

String stripped = parser.stripDirectives(text);
// Returns text with [[tts:...]] markers removed
```

## Configuration

```java
VoiceConfig config = new VoiceConfig(
    true,           // enabled
    "alloy",        // defaultVoice
    "mp3"           // outputFormat
);
```

## Core Types (in jaiclaw-core)

| Type | Fields |
|------|--------|
| `AudioResult` | `byte[] audioData`, `String mimeType`, `int durationMs` |
| `TranscriptionResult` | `String text`, `String language`, `double confidence` |
