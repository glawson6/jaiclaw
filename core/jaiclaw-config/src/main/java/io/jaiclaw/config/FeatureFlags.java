package io.jaiclaw.config;

/**
 * Per-tenant feature flags controlling agent behavior.
 *
 * @param streaming         enable streaming responses
 * @param historyLength     max conversation history messages to retain
 * @param toolUse           enable tool calling
 * @param multiTurn         enable multi-turn conversations
 * @param memoryEnabled     enable workspace memory loading
 * @param compactionEnabled enable context compaction
 */
public record FeatureFlags(
        boolean streaming,
        int historyLength,
        boolean toolUse,
        boolean multiTurn,
        boolean memoryEnabled,
        boolean compactionEnabled
) {
    public static final FeatureFlags DEFAULT = new FeatureFlags(
            true, 50, true, true, true, true
    );

    public FeatureFlags {
        if (historyLength <= 0) historyLength = 50;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean streaming;
        private int historyLength;
        private boolean toolUse;
        private boolean multiTurn;
        private boolean memoryEnabled;
        private boolean compactionEnabled;

        public Builder streaming(boolean streaming) { this.streaming = streaming; return this; }
        public Builder historyLength(int historyLength) { this.historyLength = historyLength; return this; }
        public Builder toolUse(boolean toolUse) { this.toolUse = toolUse; return this; }
        public Builder multiTurn(boolean multiTurn) { this.multiTurn = multiTurn; return this; }
        public Builder memoryEnabled(boolean memoryEnabled) { this.memoryEnabled = memoryEnabled; return this; }
        public Builder compactionEnabled(boolean compactionEnabled) { this.compactionEnabled = compactionEnabled; return this; }

        public FeatureFlags build() {
            return new FeatureFlags(streaming, historyLength, toolUse, multiTurn, memoryEnabled, compactionEnabled);
        }
    }
}
