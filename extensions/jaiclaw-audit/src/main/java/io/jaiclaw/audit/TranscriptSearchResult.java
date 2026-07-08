package io.jaiclaw.audit;

import java.time.Instant;

/**
 * A single match from a {@link TranscriptStore#search} call. The
 * {@link #matchedUtterance} is the utterance whose content contained the
 * query — the caller can {@link TranscriptStore#load} the full session
 * if they need context.
 *
 * @param sessionId        the matching session
 * @param startTime        when that session began (useful for sorting)
 * @param channel          the channel it happened on (nullable if unknown)
 * @param agentId          the agent that handled it (nullable)
 * @param matchedUtterance the utterance whose content matched — content only,
 *                          truncated to the store's configured snippet length
 */
public record TranscriptSearchResult(
        String sessionId,
        Instant startTime,
        String channel,
        String agentId,
        TranscriptUtterance matchedUtterance
) {
    public TranscriptSearchResult {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}
