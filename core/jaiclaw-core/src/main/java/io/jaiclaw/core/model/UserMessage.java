package io.jaiclaw.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserMessage(
        String id,
        Instant timestamp,
        String content,
        String senderId,
        Map<String, Object> metadata,
        List<MediaAttachment> media
) implements Message {

    public UserMessage {
        if (media == null) {
            media = List.of();
        } else {
            media = List.copyOf(media);
        }
    }

    /** Five-arg constructor — preserves prior shape, defaults {@code media} to empty. */
    public UserMessage(String id, Instant timestamp, String content, String senderId,
                       Map<String, Object> metadata) {
        this(id, timestamp, content, senderId, metadata, List.of());
    }

    /** Three-arg shorthand — defaults timestamp/metadata/media as before. */
    public UserMessage(String id, String content, String senderId) {
        this(id, Instant.now(), content, senderId, Map.of(), List.of());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String content;
        private String senderId;
        private Map<String, Object> metadata;
        private List<MediaAttachment> media;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder senderId(String senderId) { this.senderId = senderId; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder media(List<MediaAttachment> media) { this.media = media; return this; }

        public UserMessage build() {
            return new UserMessage(id, timestamp, content, senderId, metadata, media);
        }
    }
}
