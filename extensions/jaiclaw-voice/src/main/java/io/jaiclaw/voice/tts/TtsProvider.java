package io.jaiclaw.voice.tts;

import io.jaiclaw.core.model.AudioResult;

import java.util.Map;

/**
 * SPI for text-to-speech providers.
 */
public interface TtsProvider {

    String providerId();

    AudioResult synthesize(String text, String voice, Map<String, String> options);

    default AudioResult synthesize(String text, String voice) {
        return synthesize(text, voice, Map.of());
    }
}
