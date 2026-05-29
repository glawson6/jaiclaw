package io.jaiclaw.modelcatalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads model catalog entries from YAML files.
 */
public class ModelCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(ModelCatalogLoader.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Load model entries from a YAML input stream and register them in the catalog.
     */
    public int load(InputStream yamlStream, ModelCatalog catalog) throws IOException {
        List<Map<String, Object>> rawEntries = YAML_MAPPER.readValue(yamlStream,
                new TypeReference<List<Map<String, Object>>>() {});

        int count = 0;
        for (Map<String, Object> raw : rawEntries) {
            try {
                ModelEntry entry = parseEntry(raw);
                catalog.register(entry);
                count++;
            } catch (Exception e) {
                log.warn("Skipping invalid model entry: {}", e.getMessage());
            }
        }
        return count;
    }

    /**
     * Load from a classpath resource.
     */
    public int loadFromClasspath(String resourcePath, ModelCatalog catalog) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.debug("Model catalog resource not found: {}", resourcePath);
                return 0;
            }
            int count = load(is, catalog);
            log.info("Loaded {} model entries from {}", count, resourcePath);
            return count;
        } catch (IOException e) {
            log.warn("Failed to load model catalog from {}: {}", resourcePath, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private ModelEntry parseEntry(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        String provider = (String) raw.get("provider");
        String displayName = (String) raw.getOrDefault("displayName", id);
        int contextWindow = raw.containsKey("contextWindow")
                ? ((Number) raw.get("contextWindow")).intValue() : 0;
        CostTier costTier = raw.containsKey("costTier")
                ? CostTier.valueOf(((String) raw.get("costTier")).toUpperCase()) : CostTier.MEDIUM;

        Set<ModelCapability> capabilities = Set.of();
        if (raw.containsKey("capabilities")) {
            List<String> caps = (List<String>) raw.get("capabilities");
            capabilities = caps.stream()
                    .map(s -> ModelCapability.valueOf(s.toUpperCase()))
                    .collect(Collectors.toUnmodifiableSet());
        }

        List<String> aliases = List.of();
        if (raw.containsKey("aliases")) {
            aliases = List.copyOf((List<String>) raw.get("aliases"));
        }

        return new ModelEntry(id, provider, displayName, contextWindow, costTier, capabilities, aliases);
    }
}
