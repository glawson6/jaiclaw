package io.jaiclaw.core.tool;

import java.util.*;

/**
 * A named composition of multiple {@link ToolProfile} base profiles with optional allow/deny lists.
 * Composite profiles can be defined in YAML or Java and referenced by name in agent configuration.
 *
 * <p>Resolution: union all tools from the constituent base profiles, apply the composite deny list
 * (deny-wins), then apply the composite allow list (if non-empty, keeps only listed tools).
 *
 * <p>Composite profiles cannot reference other composites — only base enum values.
 */
public record CompositeToolProfile(
        String name,
        Set<ToolProfile> profiles,
        List<String> allow,
        List<String> deny
) {
    public CompositeToolProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Composite profile name must not be blank");
        }
        if (profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("Composite profile must include at least one base profile");
        }
        // Defensive copies — immutable
        profiles = Set.copyOf(profiles);
        allow = allow != null ? List.copyOf(allow) : List.of();
        deny = deny != null ? List.copyOf(deny) : List.of();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final Set<ToolProfile> profiles = new LinkedHashSet<>();
        private final List<String> allow = new ArrayList<>();
        private final List<String> deny = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder profiles(ToolProfile... profiles) {
            Collections.addAll(this.profiles, profiles);
            return this;
        }

        public Builder profiles(Collection<ToolProfile> profiles) {
            this.profiles.addAll(profiles);
            return this;
        }

        public Builder allow(String... tools) {
            Collections.addAll(this.allow, tools);
            return this;
        }

        public Builder allow(Collection<String> tools) {
            this.allow.addAll(tools);
            return this;
        }

        public Builder deny(String... tools) {
            Collections.addAll(this.deny, tools);
            return this;
        }

        public Builder deny(Collection<String> tools) {
            this.deny.addAll(tools);
            return this;
        }

        public CompositeToolProfile build() {
            return new CompositeToolProfile(name, profiles, allow, deny);
        }
    }
}
