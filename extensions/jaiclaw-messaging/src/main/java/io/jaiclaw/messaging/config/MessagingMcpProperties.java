package io.jaiclaw.messaging.config;

import java.util.List;

/**
 * Configuration properties for the messaging MCP server.
 *
 * @param enabled                    whether the messaging MCP server is active
 * @param allowedChannels            channel whitelist — empty means all channels allowed
 * @param maxRecipientsPerBroadcast  safety limit for broadcast_message
 */
public record MessagingMcpProperties(
        boolean enabled,
        List<String> allowedChannels,
        int maxRecipientsPerBroadcast
) {
    public MessagingMcpProperties {
        if (allowedChannels == null) allowedChannels = List.of();
    }

    public MessagingMcpProperties() {
        this(false, List.of(), 50);
    }

    /**
     * Returns true if the given channelId is permitted by the whitelist.
     * An empty whitelist means all channels are allowed.
     */
    public boolean isChannelAllowed(String channelId) {
        return allowedChannels.isEmpty() || allowedChannels.contains(channelId);
    }
}
