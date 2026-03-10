package io.jclaw.config;

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
    }

    public record ModelDef(
            String id,
            String name,
            int contextWindow
    ) {
    }
}
