package io.jclaw.config;

import java.util.Set;

public record ToolsProperties(
        String profile,
        Set<String> allow,
        Set<String> deny,
        WebToolsProperties web,
        ExecToolProperties exec
) {
    public static final ToolsProperties DEFAULT = new ToolsProperties(
            "coding", Set.of(), Set.of(),
            WebToolsProperties.DEFAULT, ExecToolProperties.DEFAULT
    );

    public record WebToolsProperties(
            boolean searchEnabled,
            boolean fetchEnabled
    ) {
        public static final WebToolsProperties DEFAULT = new WebToolsProperties(true, true);
    }

    public record ExecToolProperties(
            String host
    ) {
        public static final ExecToolProperties DEFAULT = new ExecToolProperties("sandbox");
    }
}
