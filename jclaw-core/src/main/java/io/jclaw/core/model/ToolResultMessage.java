package io.jclaw.core.model;

import java.time.Instant;
import java.util.Map;

public record ToolResultMessage(
        String id,
        Instant timestamp,
        String content,
        String toolCallId,
        String toolName,
        Map<String, Object> metadata
) implements Message {

    public ToolResultMessage(String id, String content, String toolCallId, String toolName) {
        this(id, Instant.now(), content, toolCallId, toolName, Map.of());
    }
}
