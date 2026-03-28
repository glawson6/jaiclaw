package io.jaiclaw.config;

import java.util.List;
import java.util.Map;

public record ModelsProperties(
        Map<String, ModelProviderConfig> providers
) {
    public static final ModelsProperties DEFAULT = new ModelsProperties(Map.of());

    public record ModelProviderConfig(
            String baseUrl,
            String apiKey,
            String api,
            List<ModelDef> models
    ) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String baseUrl;
            private String apiKey;
            private String api;
            private List<ModelDef> models;

            public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
            public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            public Builder api(String api) { this.api = api; return this; }
            public Builder models(List<ModelDef> models) { this.models = models; return this; }

            public ModelProviderConfig build() {
                return new ModelProviderConfig(baseUrl, apiKey, api, models);
            }
        }
    }

    public record ModelDef(
            String id,
            String name,
            int contextWindow
    ) {
    }
}
