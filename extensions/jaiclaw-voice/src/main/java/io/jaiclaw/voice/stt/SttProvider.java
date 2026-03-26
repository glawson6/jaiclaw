package io.jaiclaw.voice.stt;

import io.jaiclaw.core.model.TranscriptionResult;

/**
 * SPI for speech-to-text providers.
 */
public interface SttProvider {

    String providerId();

    TranscriptionResult transcribe(byte[] audioBytes, String mimeType);
}
