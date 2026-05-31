package io.jaiclaw.audit;

import java.time.Instant;
import java.util.Map;

/**
 * A single utterance in a conversation transcript.
 *
 * @param role      the speaker role (e.g. "user", "assistant", "system", "tool")
 * @param content   the text content of the utterance
 * @param timestamp when the utterance occurred
 * @param metadata  additional structured data (e.g. tool name, token count)
 */
public record TranscriptUtterance(
        String role,
        String content,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public TranscriptUtterance {
        if (role == null) role = "unknown";
        if (content == null) content = "";
        if (timestamp == null) timestamp = Instant.now();
        if (metadata == null) metadata = Map.of();
    }

    public static TranscriptUtterance user(String content) {
        return new TranscriptUtterance("user", content, Instant.now(), Map.of());
    }

    public static TranscriptUtterance assistant(String content) {
        return new TranscriptUtterance("assistant", content, Instant.now(), Map.of());
    }

    public static TranscriptUtterance system(String content) {
        return new TranscriptUtterance("system", content, Instant.now(), Map.of());
    }

    public static TranscriptUtterance tool(String toolName, String content) {
        return new TranscriptUtterance("tool", content, Instant.now(), Map.of("toolName", toolName));
    }
}
