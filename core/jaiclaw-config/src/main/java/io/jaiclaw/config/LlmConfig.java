package io.jaiclaw.config;

import java.util.List;

/**
 * Per-tenant LLM configuration.
 *
 * @param provider       provider key into {@code jaiclaw.models.providers} (e.g. "anthropic", "openai", "ollama")
 * @param primary        primary model id
 * @param fallbacks      fallback model ids
 * @param thinkingModel  specialized thinking/reasoning model
 * @param temperature    sampling temperature
 * @param maxTokens      max response tokens
 * @param timeoutSeconds request timeout
 */
public record LlmConfig(
        String provider,
        String primary,
        List<String> fallbacks,
        String thinkingModel,
        double temperature,
        int maxTokens,
        int timeoutSeconds
) {
    public static final LlmConfig DEFAULT = new LlmConfig(
            null, null, List.of(), null, 0.7, 4096, 120
    );

    public LlmConfig {
        if (fallbacks == null) fallbacks = List.of();
        if (maxTokens <= 0) maxTokens = 4096;
        if (timeoutSeconds <= 0) timeoutSeconds = 120;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String provider;
        private String primary;
        private List<String> fallbacks;
        private String thinkingModel;
        private double temperature;
        private int maxTokens;
        private int timeoutSeconds;

        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder primary(String primary) { this.primary = primary; return this; }
        public Builder fallbacks(List<String> fallbacks) { this.fallbacks = fallbacks; return this; }
        public Builder thinkingModel(String thinkingModel) { this.thinkingModel = thinkingModel; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }

        public LlmConfig build() {
            return new LlmConfig(provider, primary, fallbacks, thinkingModel, temperature, maxTokens, timeoutSeconds);
        }
    }
}
