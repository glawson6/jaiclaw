package io.jaiclaw.core.model;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean enabled;
        private double triggerThreshold;
        private int targetTokenPercent;
        private String summaryModel;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder triggerThreshold(double triggerThreshold) { this.triggerThreshold = triggerThreshold; return this; }
        public Builder targetTokenPercent(int targetTokenPercent) { this.targetTokenPercent = targetTokenPercent; return this; }
        public Builder summaryModel(String summaryModel) { this.summaryModel = summaryModel; return this; }

        public CompactionConfig build() {
            return new CompactionConfig(enabled, triggerThreshold, targetTokenPercent, summaryModel);
        }
    }
}
