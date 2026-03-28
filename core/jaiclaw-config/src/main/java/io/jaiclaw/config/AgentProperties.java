package io.jaiclaw.config;

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
            IdentityProperties identity,
            ToolLoopProperties toolLoop,
            LlmConfig llm,
            SystemPromptConfig systemPrompt,
            FeatureFlags features,
            ErrorMessages errorMessages,
            List<McpServerRef> mcpServers,
            TenantChannelsConfig channels,
            AgentLoopDelegateConfig loopDelegate
    ) {
        public AgentConfig {
            if (toolLoop == null) toolLoop = ToolLoopProperties.DEFAULT;
        }

        /**
         * Backward-compatible constructor (original 8 fields).
         */
        public AgentConfig(String id, String name, String workspace,
                           AgentModelConfig model, List<String> skills,
                           ToolPolicyConfig tools, IdentityProperties identity,
                           ToolLoopProperties toolLoop) {
            this(id, name, workspace, model, skills, tools, identity, toolLoop,
                    null, null, null, null, null, null, null);
        }

        public static final AgentConfig DEFAULT = new AgentConfig(
                "default", "Default Agent", null,
                AgentModelConfig.DEFAULT, List.of(), ToolPolicyConfig.DEFAULT, null,
                ToolLoopProperties.DEFAULT
        );

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String id;
            private String name;
            private String workspace;
            private AgentModelConfig model;
            private List<String> skills;
            private ToolPolicyConfig tools;
            private IdentityProperties identity;
            private ToolLoopProperties toolLoop;
            private LlmConfig llm;
            private SystemPromptConfig systemPrompt;
            private FeatureFlags features;
            private ErrorMessages errorMessages;
            private List<McpServerRef> mcpServers;
            private TenantChannelsConfig channels;
            private AgentLoopDelegateConfig loopDelegate;

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder workspace(String workspace) { this.workspace = workspace; return this; }
            public Builder model(AgentModelConfig model) { this.model = model; return this; }
            public Builder skills(List<String> skills) { this.skills = skills; return this; }
            public Builder tools(ToolPolicyConfig tools) { this.tools = tools; return this; }
            public Builder identity(IdentityProperties identity) { this.identity = identity; return this; }
            public Builder toolLoop(ToolLoopProperties toolLoop) { this.toolLoop = toolLoop; return this; }
            public Builder llm(LlmConfig llm) { this.llm = llm; return this; }
            public Builder systemPrompt(SystemPromptConfig systemPrompt) { this.systemPrompt = systemPrompt; return this; }
            public Builder features(FeatureFlags features) { this.features = features; return this; }
            public Builder errorMessages(ErrorMessages errorMessages) { this.errorMessages = errorMessages; return this; }
            public Builder mcpServers(List<McpServerRef> mcpServers) { this.mcpServers = mcpServers; return this; }
            public Builder channels(TenantChannelsConfig channels) { this.channels = channels; return this; }
            public Builder loopDelegate(AgentLoopDelegateConfig loopDelegate) { this.loopDelegate = loopDelegate; return this; }

            public AgentConfig build() {
                return new AgentConfig(id, name, workspace, model, skills, tools, identity,
                        toolLoop, llm, systemPrompt, features, errorMessages, mcpServers,
                        channels, loopDelegate);
            }
        }
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
