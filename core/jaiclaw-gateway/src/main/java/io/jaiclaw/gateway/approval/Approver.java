package io.jaiclaw.gateway.approval;

import io.jaiclaw.core.api.Experimental;

/**
 * Where an approval question is asked, and who is allowed to answer it.
 *
 * @param channelId channel to ask on, e.g. {@code telegram}
 * @param accountId sending account on that channel
 * @param peerId    conversation to ask in — the chat id for Telegram, the channel for Slack and Discord
 * @param userId    the platform user id whose replies count, or {@code null} to accept any
 *                  participant of the conversation. On Telegram and Discord a {@code peerId} may
 *                  name a <em>group</em>, in which case leaving this unset lets any member approve;
 *                  set it, or ask in a direct message.
 */
@Experimental
public record Approver(String channelId, String accountId, String peerId, String userId) {

    /** True when replies are restricted to one platform user, not just one conversation. */
    public boolean restrictsUser() {
        return userId != null && !userId.isBlank();
    }
}
