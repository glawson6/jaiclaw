package io.jaiclaw.core.plugin;

public record PluginDefinition(
        String id,
        String name,
        String description,
        String version,
        PluginKind kind
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String name;
        private String description;
        private String version;
        private PluginKind kind;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder kind(PluginKind kind) { this.kind = kind; return this; }

        public PluginDefinition build() {
            return new PluginDefinition(id, name, description, version, kind);
        }
    }
}
