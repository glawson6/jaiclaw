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
            Boolean baaEligible,
            /**
             * Federal compliance (2026-08): whether this provider route is
             * FedRAMP-authorized. Consumed by
             * {@code FedRampWarningChatModelDecorator} — WARN emitted for
             * tenants with {@code fedramp.impact_level} = moderate or high
             * whose resolved provider is not authorized. No baked-in
             * defaults for FedRAMP — the operator declares authorization
             * explicitly (FedRAMP scope varies by deployment).
             */
            Boolean fedrampAuthorized,
            /**
             * Federal compliance (2026-08): whether this provider route is
             * CUI-authorized (CMMC L2 / DFARS 252.204-7012). Consumed by
             * {@code CuiWarningChatModelDecorator}.
             */
            Boolean cuiAuthorized
    ) {
        public ModelProviderConfig {
            if (wizardModels == null) wizardModels = List.of();
        }

        /** Backward-compat 7-arg ctor for pre-T1-4 callers — baaEligible + federal flags default to null (unknown). */
        public ModelProviderConfig(String baseUrl, String apiKey, String api,
                                    List<ModelDef> models, List<String> wizardModels,
                                    String fallbackModel, String displayName) {
            this(baseUrl, apiKey, api, models, wizardModels, fallbackModel, displayName, null, null, null);
        }

        /** Backward-compat 8-arg ctor for T1-4 (BAA-only) callers — federal flags default to null. */
        public ModelProviderConfig(String baseUrl, String apiKey, String api,
                                    List<ModelDef> models, List<String> wizardModels,
                                    String fallbackModel, String displayName,
                                    Boolean baaEligible) {
            this(baseUrl, apiKey, api, models, wizardModels, fallbackModel, displayName, baaEligible, null, null);
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
            private Boolean fedrampAuthorized;
            private Boolean cuiAuthorized;

            public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
            public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            public Builder api(String api) { this.api = api; return this; }
            public Builder models(List<ModelDef> models) { this.models = models; return this; }
            public Builder wizardModels(List<String> wizardModels) { this.wizardModels = wizardModels; return this; }
            public Builder fallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; return this; }
            public Builder displayName(String displayName) { this.displayName = displayName; return this; }
            public Builder baaEligible(Boolean baaEligible) { this.baaEligible = baaEligible; return this; }
            public Builder fedrampAuthorized(Boolean fedrampAuthorized) { this.fedrampAuthorized = fedrampAuthorized; return this; }
            public Builder cuiAuthorized(Boolean cuiAuthorized) { this.cuiAuthorized = cuiAuthorized; return this; }

            public ModelProviderConfig build() {
                return new ModelProviderConfig(baseUrl, apiKey, api, models, wizardModels,
                        fallbackModel, displayName, baaEligible, fedrampAuthorized, cuiAuthorized);
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
