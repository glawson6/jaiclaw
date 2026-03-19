package io.jclaw.core.model;

/**
 * Configuration for context compaction.
 *
 * @param enabled           whether compaction is active
 * @param triggerThreshold  fraction of context window that triggers compaction (e.g., 0.8 = 80%)
 * @param targetTokenPercent keep summary to this percentage of context window (e.g., 20)
 * @param summaryModel      model to use for summarization; null means use the agent's primary model
 */
public record CompactionConfig(
        boolean enabled,
        double triggerThreshold,
        int targetTokenPercent,
        String summaryModel
) {
    public CompactionConfig {
        if (triggerThreshold <= 0 || triggerThreshold > 1.0) triggerThreshold = 0.8;
        if (targetTokenPercent <= 0 || targetTokenPercent > 100) targetTokenPercent = 20;
    }

    public static final CompactionConfig DEFAULT = new CompactionConfig(true, 0.8, 20, null);
    public static final CompactionConfig DISABLED = new CompactionConfig(false, 0.8, 20, null);
}
