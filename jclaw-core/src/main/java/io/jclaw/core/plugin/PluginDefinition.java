package io.jclaw.core.plugin;

public record PluginDefinition(
        String id,
        String name,
        String description,
        String version,
        PluginKind kind
) {
}
