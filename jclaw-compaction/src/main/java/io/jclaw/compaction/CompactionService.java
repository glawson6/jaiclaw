package io.jclaw.compaction;

import io.jclaw.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Orchestrates context compaction: checks budget, chunks old messages,
 * summarizes them, and produces a compacted message list.
 *
 * <p>This service is stateless — it operates on the provided messages and returns
 * the result. The caller (AgentRuntime) is responsible for replacing messages in the session.
 */
public class CompactionService {

    private static final Logger log = LoggerFactory.getLogger(CompactionService.class);

    private final TokenEstimator tokenEstimator;
    private final CompactionConfig config;

    public CompactionService(CompactionConfig config) {
        this.config = config;
        this.tokenEstimator = new TokenEstimator();
    }

    /**
     * Check if compaction is needed and perform it if so.
     *
     * @param messages           current conversation messages
     * @param contextWindowSize  the model's max context tokens
     * @param llmCall            function to call the LLM for summarization
     * @return compaction result if compaction was performed, or null if not needed
     */
    public CompactionResult compactIfNeeded(List<Message> messages, int contextWindowSize,
                                            Function<String, String> llmCall) {
        if (!config.enabled()) return null;

        int currentTokens = tokenEstimator.estimateTokens(messages);
        int threshold = (int) (contextWindowSize * config.triggerThreshold());

        if (currentTokens < threshold) {
            log.debug("Compaction not needed: {} tokens < {} threshold", currentTokens, threshold);
            return null;
        }

        log.info("Compaction triggered: {} tokens >= {} threshold ({} messages)",
                currentTokens, threshold, messages.size());

        return performCompaction(messages, contextWindowSize, llmCall, currentTokens);
    }

    /**
     * Returns a new message list with old messages replaced by a summary.
     * Call this after {@link #compactIfNeeded} returns a non-null result.
     */
    public List<Message> applyCompaction(List<Message> messages, int contextWindowSize,
                                          Function<String, String> llmCall) {
        CompactionResult result = compactIfNeeded(messages, contextWindowSize, llmCall);
        if (result == null) return messages;

        int keepCount = computeKeepCount(messages, contextWindowSize);
        List<Message> recentMessages = messages.subList(messages.size() - keepCount, messages.size());

        SystemMessage summaryMessage = new SystemMessage(
                UUID.randomUUID().toString(),
                "[Context Summary]\n" + result.summary()
        );

        List<Message> compacted = new ArrayList<>();
        compacted.add(summaryMessage);
        compacted.addAll(recentMessages);
        return List.copyOf(compacted);
    }

    private CompactionResult performCompaction(List<Message> messages, int contextWindowSize,
                                                Function<String, String> llmCall, int currentTokens) {
        int keepCount = computeKeepCount(messages, contextWindowSize);
        int removeCount = messages.size() - keepCount;

        if (removeCount <= 0) {
            log.debug("Nothing to compact — all messages are recent");
            return null;
        }

        List<Message> toSummarize = messages.subList(0, removeCount);
        CompactionSummarizer summarizer = new CompactionSummarizer(llmCall);
        String summary = summarizer.summarize(toSummarize);

        int compactedTokens = tokenEstimator.estimateTokens(summary)
                + tokenEstimator.estimateTokens(messages.subList(removeCount, messages.size()));

        log.info("Compaction complete: {} → {} tokens, {} messages removed",
                currentTokens, compactedTokens, removeCount);

        return new CompactionResult(summary, currentTokens, compactedTokens, removeCount);
    }

    private int computeKeepCount(List<Message> messages, int contextWindowSize) {
        int targetTokens = (int) (contextWindowSize * config.targetTokenPercent() / 100.0);
        int kept = 0;
        int tokenBudget = targetTokens;

        for (int i = messages.size() - 1; i >= 0; i--) {
            int msgTokens = tokenEstimator.estimateTokens(messages.get(i));
            if (tokenBudget - msgTokens < 0 && kept > 0) break;
            tokenBudget -= msgTokens;
            kept++;
        }

        return Math.max(kept, 1);
    }
}
