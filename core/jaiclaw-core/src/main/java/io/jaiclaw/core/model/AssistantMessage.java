package io.jaiclaw.core.model;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String content;
        private String modelId;
        private TokenUsage usage;
        private Map<String, Object> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder usage(TokenUsage usage) { this.usage = usage; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public AssistantMessage build() {
            return new AssistantMessage(id, timestamp, content, modelId, usage, metadata);
        }
    }
}
