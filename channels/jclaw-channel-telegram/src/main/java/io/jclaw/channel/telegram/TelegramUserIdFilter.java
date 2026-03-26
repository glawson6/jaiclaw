package io.jclaw.channel.telegram;

import io.jclaw.channel.ChannelMessage;
import io.jclaw.channel.ChannelMessageHandler;
import io.jclaw.security.ratelimit.UserRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Gateway filter that intercepts inbound Telegram messages and enforces:
 *
 * <ol>
 *   <li><b>User ID authorization</b> — only configured user IDs may interact</li>
 *   <li><b>Rate limiting</b> — prevents command flooding per user</li>
 * </ol>
 *
 * <p>This filter wraps the downstream {@link ChannelMessageHandler} (the GatewayService)
 * and silently drops messages from unauthorized users. Rate-limited messages are also
 * dropped with a log warning.
 *
 * <p>Note: This filter supplements (not replaces) the allowedUserIds filtering built
 * into JClaw's TelegramAdapter. The TelegramAdapter's built-in filter runs first at
 * the polling/webhook level. This filter adds a second layer at the gateway level
 * for defense in depth, and also adds rate limiting which is not in the base adapter.
 */
public class TelegramUserIdFilter implements ChannelMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUserIdFilter.class);

    private final Set<String> allowedUserIds;
    private final UserRateLimiter rateLimiter;
    private ChannelMessageHandler downstream;

    public TelegramUserIdFilter(Set<String> allowedUserIds, UserRateLimiter rateLimiter) {
        this.allowedUserIds = allowedUserIds;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Set the downstream handler that receives approved messages.
     * Called during gateway startup to chain this filter before the GatewayService.
     */
    public void setDownstream(ChannelMessageHandler downstream) {
        this.downstream = downstream;
    }

    @Override
    public void onMessage(ChannelMessage message) {
        // Only filter Telegram messages
        if (!"telegram".equals(message.channelId())) {
            passThrough(message);
            return;
        }

        String userId = extractUserId(message);

        // Authorization check
        if (!allowedUserIds.isEmpty() && !allowedUserIds.contains(userId)) {
            log.warn("Blocked message from unauthorized Telegram user: {}", userId);
            return; // Silently drop
        }

        // Rate limit check
        if (!rateLimiter.isAllowed(userId)) {
            log.warn("Rate-limited Telegram user: {} (remaining: {})",
                    userId, rateLimiter.remaining(userId));
            return; // Silently drop
        }

        passThrough(message);
    }

    /**
     * Extract the Telegram user ID from the ChannelMessage.
     * In JClaw's Telegram adapter, peerId is the chat ID.
     * The from user ID is stored in platformData or can be derived from peerId
     * for direct messages (where chatId == userId).
     */
    private String extractUserId(ChannelMessage message) {
        // For direct messages, peerId (chatId) equals the userId
        return message.peerId();
    }

    private void passThrough(ChannelMessage message) {
        if (downstream != null) {
            downstream.onMessage(message);
        } else {
            log.warn("No downstream handler configured for TelegramUserIdFilter");
        }
    }
}
