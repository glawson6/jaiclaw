package io.jclaw.config;

import java.util.List;
import java.util.Map;

public record AgentProperties(
        String defaultAgent,
        Map<String, AgentConfig> agents
) {
    public static final AgentProperties DEFAULT = new AgentProperties(
            "default", Map.of("default", AgentConfig.DEFAULT)
    );

    public record AgentConfig(
            String id,
            String name,
            String workspace,
            AgentModelConfig model,
            List<String> skills,
            ToolPolicyConfig tools,
            IdentityProperties identity
    ) {
        public static final AgentConfig DEFAULT = new AgentConfig(
                "default", "Default Agent", null,
                AgentModelConfig.DEFAULT, List.of(), ToolPolicyConfig.DEFAULT, null
        );
    }

    public record AgentModelConfig(
            String primary,
            List<String> fallbacks,
            String thinkingModel
    ) {
        public static final AgentModelConfig DEFAULT = new AgentModelConfig(
                "gpt-4o", List.of("gpt-4o-mini"), null
        );
    }

    public record ToolPolicyConfig(
            String profile,
            List<String> allow,
            List<String> deny
    ) {
        public static final ToolPolicyConfig DEFAULT = new ToolPolicyConfig(
                "coding", List.of(), List.of()
        );
    }
}
