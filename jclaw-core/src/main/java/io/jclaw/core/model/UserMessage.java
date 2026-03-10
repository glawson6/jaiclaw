package io.jclaw.core.model;

import java.time.Instant;
import java.util.Map;

public record UserMessage(
        String id,
        Instant timestamp,
        String content,
        String senderId,
        Map<String, Object> metadata
) implements Message {

    public UserMessage(String id, String content, String senderId) {
        this(id, Instant.now(), content, senderId, Map.of());
    }
}
