package io.jaiclaw.gateway.approval;

import io.jaiclaw.core.api.Experimental;

/**
 * Who to ask for tool approval, and where to reach them.
 *
 * @param channelId  channel to send the question on (e.g. {@code telegram})
 * @param accountId  sending account on that channel
 * @param peerId     conversation to ask in — the chat id for Telegram
 */
@Experimental
public record Approver(String channelId, String accountId, String peerId) {
}
