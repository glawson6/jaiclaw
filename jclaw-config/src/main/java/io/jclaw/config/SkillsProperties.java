package io.jclaw.config;

import java.util.List;

public record SkillsProperties(
        List<String> allowBundled,
        boolean watchWorkspace,
        String workspaceDir
) {
    public static final SkillsProperties DEFAULT = new SkillsProperties(
            List.of("*"), true, null
    );
}
