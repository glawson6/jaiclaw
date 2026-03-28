package io.jaiclaw.channel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Normalized message exchanged between a channel adapter and the gateway.
 * Inbound: adapter creates from platform-native format.
 * Outbound: gateway creates from AssistantMessage for adapter to deliver.
 */
public record ChannelMessage(
        String id,
        String channelId,
        String accountId,
        String peerId,
        String content,
        Instant timestamp,
        Direction direction,
        List<Attachment> attachments,
        Map<String, Object> platformData
) {
    public ChannelMessage {
        if (attachments == null) attachments = List.of();
        if (platformData == null) platformData = Map.of();
    }

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public record Attachment(
            String name,
            String mimeType,
            String url,
            byte[] data
    ) {}

    /**
     * Whether this message carries file attachments.
     */
    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    /**
     * Compute the session key for routing this message to an agent session.
     */
    public String sessionKey(String agentId) {
        return agentId + ":" + channelId + ":" + accountId + ":" + peerId;
    }

    public static ChannelMessage inbound(
            String id, String channelId, String accountId, String peerId,
            String content, Map<String, Object> platformData) {
        return new ChannelMessage(
                id, channelId, accountId, peerId, content,
                Instant.now(), Direction.INBOUND, List.of(), platformData);
    }

    public static ChannelMessage inbound(
            String id, String channelId, String accountId, String peerId,
            String content, List<Attachment> attachments, Map<String, Object> platformData) {
        return new ChannelMessage(
                id, channelId, accountId, peerId, content,
                Instant.now(), Direction.INBOUND, attachments, platformData);
    }

    public static ChannelMessage outbound(
            String id, String channelId, String accountId, String peerId,
            String content) {
        return new ChannelMessage(
                id, channelId, accountId, peerId, content,
                Instant.now(), Direction.OUTBOUND, List.of(), Map.of());
    }

    public static ChannelMessage outbound(
            String id, String channelId, String accountId, String peerId,
            String content, List<Attachment> attachments) {
        return new ChannelMessage(
                id, channelId, accountId, peerId, content,
                Instant.now(), Direction.OUTBOUND, attachments, Map.of());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String channelId;
        private String accountId;
        private String peerId;
        private String content;
        private Instant timestamp;
        private Direction direction;
        private List<Attachment> attachments;
        private Map<String, Object> platformData;

        public Builder id(String id) { this.id = id; return this; }
        public Builder channelId(String channelId) { this.channelId = channelId; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder peerId(String peerId) { this.peerId = peerId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder direction(Direction direction) { this.direction = direction; return this; }
        public Builder attachments(List<Attachment> attachments) { this.attachments = attachments; return this; }
        public Builder platformData(Map<String, Object> platformData) { this.platformData = platformData; return this; }

        public ChannelMessage build() {
            return new ChannelMessage(
                    id, channelId, accountId, peerId, content, timestamp, direction, attachments, platformData);
        }
    }
}
