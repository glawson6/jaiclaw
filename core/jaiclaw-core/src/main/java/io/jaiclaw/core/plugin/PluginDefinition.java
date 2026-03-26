package io.jaiclaw.core.plugin;

public record PluginDefinition(
        String id,
        String name,
        String description,
        String version,
        PluginKind kind
) {
}
