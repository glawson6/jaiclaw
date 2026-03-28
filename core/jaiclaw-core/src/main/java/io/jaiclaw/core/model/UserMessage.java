package io.jaiclaw.core.model;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String content;
        private String senderId;
        private Map<String, Object> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder senderId(String senderId) { this.senderId = senderId; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public UserMessage build() {
            return new UserMessage(id, timestamp, content, senderId, metadata);
        }
    }
}
