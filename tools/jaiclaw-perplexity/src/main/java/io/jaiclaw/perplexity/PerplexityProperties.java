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
}
