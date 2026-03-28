package io.jaiclaw.core.model;

/**
 * Binding rule that maps (channel + peer) to an agent with activation rules.
 *
 * @param agentId      the agent to route to
 * @param channel      channel ID ("telegram", "slack", etc.)
 * @param peerKind     "direct", "group", "channel"
 * @param peerId       specific peer ID, or null for wildcard
 * @param mentionOnly  if true, only respond when @mentioned in groups
 */
public record RoutingBinding(
        String agentId,
        String channel,
        String peerKind,
        String peerId,
        boolean mentionOnly
) {
    public RoutingBinding {
        if (agentId == null) agentId = "default";
        if (peerKind == null) peerKind = "direct";
    }

    public boolean matches(String channelId, String peerId, ChatType chatType) {
        if (channel != null && !channel.equals(channelId)) return false;
        if (this.peerId != null && !this.peerId.equals(peerId)) return false;
        if (peerKind != null) {
            String expectedKind = chatType.name().toLowerCase();
            if (!peerKind.equals(expectedKind)) return false;
        }
        return true;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String agentId;
        private String channel;
        private String peerKind;
        private String peerId;
        private boolean mentionOnly;

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder channel(String channel) { this.channel = channel; return this; }
        public Builder peerKind(String peerKind) { this.peerKind = peerKind; return this; }
        public Builder peerId(String peerId) { this.peerId = peerId; return this; }
        public Builder mentionOnly(boolean mentionOnly) { this.mentionOnly = mentionOnly; return this; }

        public RoutingBinding build() {
            return new RoutingBinding(agentId, channel, peerKind, peerId, mentionOnly);
        }
    }
}
