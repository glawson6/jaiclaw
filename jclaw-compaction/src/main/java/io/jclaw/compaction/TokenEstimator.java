package io.jclaw.compaction;

import io.jclaw.core.model.Message;

import java.util.List;

/**
 * Estimates token count for messages using a character-based approximation.
 * Uses the cl100k_base heuristic of ~4 characters per token.
 */
public class TokenEstimator {

    private static final double CHARS_PER_TOKEN = 4.0;
    private static final int PER_MESSAGE_OVERHEAD = 4;

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public int estimateTokens(Message message) {
        return estimateTokens(message.content()) + PER_MESSAGE_OVERHEAD;
    }

    public int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }
}
