package io.jclaw.config;

import java.util.Map;
import java.util.Set;

public record PluginsProperties(
        boolean enabled,
        Set<String> allow,
        Set<String> deny,
        Map<String, PluginEntryConfig> entries
) {
    public static final PluginsProperties DEFAULT = new PluginsProperties(
            true, Set.of(), Set.of(), Map.of()
    );

    public record PluginEntryConfig(
            boolean enabled,
            Map<String, Object> config
    ) {
    }
}
