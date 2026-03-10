package io.jclaw.core.skill;

import java.util.Set;

public record SkillMetadata(
        boolean alwaysInclude,
        String primaryEnv,
        Set<String> requiredBins,
        Set<String> supportedPlatforms
) {
    public static final SkillMetadata EMPTY = new SkillMetadata(
            false, "", Set.of(), Set.of()
    );
}
