package io.jclaw.core.model;

/**
 * Result of a text-to-speech synthesis operation.
 *
 * @param audioData   raw audio bytes
 * @param mimeType    audio format (e.g., "audio/mpeg", "audio/ogg")
 * @param durationMs  audio duration in milliseconds
 */
public record AudioResult(byte[] audioData, String mimeType, int durationMs) {
}
