package io.jaiclaw.core.skill;

import java.util.Set;

public record SkillMetadata(
        boolean alwaysInclude,
        String primaryEnv,
        Set<String> requiredBins,
        Set<String> supportedPlatforms,
        String version,
        Set<String> tenantIds
) {
    public SkillMetadata(boolean alwaysInclude, String primaryEnv,
                         Set<String> requiredBins, Set<String> supportedPlatforms) {
        this(alwaysInclude, primaryEnv, requiredBins, supportedPlatforms, "1.0.0", Set.of());
    }

    public SkillMetadata {
        if (version == null || version.isBlank()) version = "1.0.0";
        if (tenantIds == null) tenantIds = Set.of();
    }

    /** Whether this skill is available to the given tenant (empty tenantIds = available to all). */
    public boolean isAvailableToTenant(String tenantId) {
        return tenantIds.isEmpty() || tenantIds.contains(tenantId);
    }

    public static final SkillMetadata EMPTY = new SkillMetadata(
            false, "", Set.of(), Set.of(), "1.0.0", Set.of()
    );

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean alwaysInclude;
        private String primaryEnv;
        private Set<String> requiredBins;
        private Set<String> supportedPlatforms;
        private String version;
        private Set<String> tenantIds;

        public Builder alwaysInclude(boolean alwaysInclude) { this.alwaysInclude = alwaysInclude; return this; }
        public Builder primaryEnv(String primaryEnv) { this.primaryEnv = primaryEnv; return this; }
        public Builder requiredBins(Set<String> requiredBins) { this.requiredBins = requiredBins; return this; }
        public Builder supportedPlatforms(Set<String> supportedPlatforms) { this.supportedPlatforms = supportedPlatforms; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder tenantIds(Set<String> tenantIds) { this.tenantIds = tenantIds; return this; }

        public SkillMetadata build() {
            return new SkillMetadata(
                    alwaysInclude, primaryEnv, requiredBins, supportedPlatforms, version, tenantIds);
        }
    }
}
