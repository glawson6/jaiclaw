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
            List<ModelDef> models,
            List<String> wizardModels,
            String fallbackModel,
            String displayName,
            /**
             * T1-4: whether this provider is HIPAA-BAA-eligible. Drives the
             * startup warning emitted for tenants marked
             * {@code hipaa.phi_processing=true}. Defaults per provider name
             * are supplied by {@code BaaEligibleProviders} — override here
             * (e.g. {@code true} for a custom Anthropic route via Bedrock,
             * {@code false} for the direct API).
             */
            Boolean baaEligible
    ) {
        public ModelProviderConfig {
            if (wizardModels == null) wizardModels = List.of();
        }

        /** Backward-compat 7-arg ctor for pre-T1-4 callers — baaEligible defaults to null (unknown). */
        public ModelProviderConfig(String baseUrl, String apiKey, String api,
                                    List<ModelDef> models, List<String> wizardModels,
                                    String fallbackModel, String displayName) {
            this(baseUrl, apiKey, api, models, wizardModels, fallbackModel, displayName, null);
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String baseUrl;
            private String apiKey;
            private String api;
            private List<ModelDef> models;
            private List<String> wizardModels;
            private String fallbackModel;
            private String displayName;
            private Boolean baaEligible;

            public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
            public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            public Builder api(String api) { this.api = api; return this; }
            public Builder models(List<ModelDef> models) { this.models = models; return this; }
            public Builder wizardModels(List<String> wizardModels) { this.wizardModels = wizardModels; return this; }
            public Builder fallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; return this; }
            public Builder displayName(String displayName) { this.displayName = displayName; return this; }
            public Builder baaEligible(Boolean baaEligible) { this.baaEligible = baaEligible; return this; }

            public ModelProviderConfig build() {
                return new ModelProviderConfig(baseUrl, apiKey, api, models, wizardModels,
                        fallbackModel, displayName, baaEligible);
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
