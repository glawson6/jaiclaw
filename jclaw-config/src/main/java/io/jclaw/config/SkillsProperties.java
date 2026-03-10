package io.jclaw.config;

import java.util.List;

public record SkillsProperties(
        List<String> allowBundled,
        boolean watchWorkspace
) {
    public static final SkillsProperties DEFAULT = new SkillsProperties(
            List.of("*"), true
    );
}
