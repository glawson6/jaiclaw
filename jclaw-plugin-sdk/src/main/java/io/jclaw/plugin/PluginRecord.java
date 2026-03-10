package io.jclaw.plugin;

import java.util.Set;

public record PluginRecord(
        String id,
        String name,
        String version,
        PluginOrigin origin,
        boolean enabled,
        PluginStatus status,
        Set<String> toolNames,
        Set<String> hookNames
) {
}
