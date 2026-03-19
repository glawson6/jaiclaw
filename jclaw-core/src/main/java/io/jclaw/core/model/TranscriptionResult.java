package io.jclaw.core.model;

/**
 * Result of a speech-to-text transcription operation.
 *
 * @param text       the transcribed text
 * @param language   detected language code (e.g., "en")
 * @param confidence confidence score between 0.0 and 1.0
 */
public record TranscriptionResult(String text, String language, double confidence) {
}
