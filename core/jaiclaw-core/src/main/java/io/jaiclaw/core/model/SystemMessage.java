package io.jaiclaw.core.model;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String content;
        private Map<String, Object> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public SystemMessage build() {
            return new SystemMessage(id, timestamp, content, metadata);
        }
    }
}
