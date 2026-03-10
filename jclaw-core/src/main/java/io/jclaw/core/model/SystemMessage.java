package io.jclaw.core.model;

import java.time.Instant;
import java.util.Map;

public record SystemMessage(
        String id,
        Instant timestamp,
        String content,
        Map<String, Object> metadata
) implements Message {

    public SystemMessage(String id, String content) {
        this(id, Instant.now(), content, Map.of());
    }
}
