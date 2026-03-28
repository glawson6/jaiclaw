package io.jaiclaw.perplexity;

public record PerplexityProperties(
        String apiKey,
        String defaultModel,
        String defaultPreset,
        int maxTokens,
        double temperature,
        boolean imagesEnabled
) {
    public PerplexityProperties {
        if (defaultModel == null || defaultModel.isBlank()) defaultModel = "sonar-pro";
        if (defaultPreset == null || defaultPreset.isBlank()) defaultPreset = "pro-search";
        if (maxTokens <= 0) maxTokens = 4096;
        if (temperature < 0) temperature = 0.2;
    }

    public static PerplexityProperties defaults() {
        return new PerplexityProperties(null, "sonar-pro", "pro-search", 4096, 0.2, false);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String apiKey;
        private String defaultModel;
        private String defaultPreset;
        private int maxTokens;
        private double temperature;
        private boolean imagesEnabled;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder defaultModel(String defaultModel) { this.defaultModel = defaultModel; return this; }
        public Builder defaultPreset(String defaultPreset) { this.defaultPreset = defaultPreset; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder imagesEnabled(boolean imagesEnabled) { this.imagesEnabled = imagesEnabled; return this; }

        public PerplexityProperties build() {
            return new PerplexityProperties(
                    apiKey, defaultModel, defaultPreset, maxTokens, temperature, imagesEnabled);
        }
    }
}
