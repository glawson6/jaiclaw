package io.jclaw.voice.stt;

import io.jclaw.core.model.TranscriptionResult;

/**
 * SPI for speech-to-text providers.
 */
public interface SttProvider {

    String providerId();

    TranscriptionResult transcribe(byte[] audioBytes, String mimeType);
}
