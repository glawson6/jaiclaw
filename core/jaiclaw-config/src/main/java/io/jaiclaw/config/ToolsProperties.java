package io.jaiclaw.config;

import java.util.List;
import java.util.Set;

public record ToolsProperties(
        String profile,
        Set<String> allow,
        Set<String> deny,
        WebToolsProperties web,
        ExecToolProperties exec,
        CodeToolsProperties code
) {
    public static final ToolsProperties DEFAULT = new ToolsProperties(
            "coding", Set.of(), Set.of(),
            WebToolsProperties.DEFAULT, ExecToolProperties.DEFAULT, CodeToolsProperties.DEFAULT
    );

    public ToolsProperties {
        if (web == null) web = WebToolsProperties.DEFAULT;
        if (exec == null) exec = ExecToolProperties.DEFAULT;
        if (code == null) code = CodeToolsProperties.DEFAULT;
    }

    public record WebToolsProperties(
            boolean searchEnabled,
            boolean fetchEnabled,
            boolean ssrfProtection
    ) {
        public static final WebToolsProperties DEFAULT = new WebToolsProperties(true, true, false);
    }

    public record ExecToolProperties(
            String host,
            String policy,
            List<String> allowedCommands,
            List<String> blockedPatterns,
            int maxTimeout,
            KubectlPolicyProperties kubectl
    ) {
        public static final ExecToolProperties DEFAULT = new ExecToolProperties(
                "sandbox", "deny-dangerous", List.of(),
                List.of("rm -rf /", "mkfs", "> /dev/sd"), 300,
                KubectlPolicyProperties.DEFAULT
        );
    }

    public record KubectlPolicyProperties(
            String policy,
            List<String> allowedVerbs,
            List<String> blockedVerbs
    ) {
        public static final KubectlPolicyProperties DEFAULT = new KubectlPolicyProperties(
                "unrestricted", List.of(), List.of()
        );
    }

    public record CodeToolsProperties(
            boolean workspaceBoundary
    ) {
        public static final CodeToolsProperties DEFAULT = new CodeToolsProperties(false);
    }
}
