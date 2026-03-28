package io.jaiclaw.core.model;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String content;
        private String toolCallId;
        private String toolName;
        private Map<String, Object> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder toolCallId(String toolCallId) { this.toolCallId = toolCallId; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public ToolResultMessage build() {
            return new ToolResultMessage(id, timestamp, content, toolCallId, toolName, metadata);
        }
    }
}
