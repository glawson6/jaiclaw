package io.jaiclaw.modelcatalog;

import java.util.List;
import java.util.Set;

/**
 * Metadata for a single model in the catalog.
 */
public record ModelEntry(
        String id,
        String provider,
        String displayName,
        int contextWindow,
        CostTier costTier,
        Set<ModelCapability> capabilities,
        List<String> aliases
) {
    public ModelEntry {
        if (capabilities == null) capabilities = Set.of();
        if (aliases == null) aliases = List.of();
    }
}
