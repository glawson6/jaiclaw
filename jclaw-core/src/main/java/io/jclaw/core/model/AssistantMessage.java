package io.jclaw.core.model;

import java.time.Instant;
import java.util.Map;

public record AssistantMessage(
        String id,
        Instant timestamp,
        String content,
        String modelId,
        TokenUsage usage,
        Map<String, Object> metadata
) implements Message {

    public AssistantMessage(String id, String content, String modelId) {
        this(id, Instant.now(), content, modelId, null, Map.of());
    }
}
