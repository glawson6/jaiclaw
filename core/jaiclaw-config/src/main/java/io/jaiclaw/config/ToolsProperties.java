package io.jaiclaw.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ToolsProperties(
        String profile,
        Set<String> allow,
        Set<String> deny,
        Map<String, CompositeProfileEntry> compositeProfiles,
        WebToolsProperties web,
        ExecToolProperties exec,
        CodeToolsProperties code
) {
    public static final ToolsProperties DEFAULT = new ToolsProperties(
            "coding", Set.of(), Set.of(), Map.of(),
            WebToolsProperties.DEFAULT, ExecToolProperties.DEFAULT, CodeToolsProperties.DEFAULT
    );

    public ToolsProperties {
        if (compositeProfiles == null) compositeProfiles = Map.of();
        if (web == null) web = WebToolsProperties.DEFAULT;
        if (exec == null) exec = ExecToolProperties.DEFAULT;
        if (code == null) code = CodeToolsProperties.DEFAULT;
    }

    /**
     * YAML-bound entry for a composite tool profile definition.
     */
    public record CompositeProfileEntry(
            List<String> profiles,
            List<String> allow,
            List<String> deny
    ) {
        public CompositeProfileEntry {
            if (profiles == null) profiles = List.of();
            if (allow == null) allow = List.of();
            if (deny == null) deny = List.of();
        }
    }

    public record WebToolsProperties(
            boolean searchEnabled,
            boolean fetchEnabled,
            boolean ssrfProtection
    ) {
        public static final WebToolsProperties DEFAULT = new WebToolsProperties(true, true, true);
    }

    public record ExecToolProperties(
            String host,
            String policy,
            List<String> allowedCommands,
            List<String> blockedPatterns,
            int maxTimeout,
            KubectlPolicyProperties kubectl
    ) {
        public ExecToolProperties {
            if (allowedCommands == null) allowedCommands = List.of();
            if (blockedPatterns == null) blockedPatterns = List.of();
            if (kubectl == null) kubectl = KubectlPolicyProperties.DEFAULT;
        }

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
        public KubectlPolicyProperties {
            if (allowedVerbs == null) allowedVerbs = List.of();
            if (blockedVerbs == null) blockedVerbs = List.of();
        }

        public static final KubectlPolicyProperties DEFAULT = new KubectlPolicyProperties(
                "unrestricted", List.of(), List.of()
        );
    }

    public record CodeToolsProperties(
            boolean workspaceBoundary
    ) {
        public static final CodeToolsProperties DEFAULT = new CodeToolsProperties(true);
    }
}
