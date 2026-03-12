package io.jclaw.core.skill;

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
}
