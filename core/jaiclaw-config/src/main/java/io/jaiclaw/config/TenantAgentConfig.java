package io.jaiclaw.config;

import java.util.List;

/**
 * Unified resolved configuration for one tenant's agent. Combines defaults from
 * application.yml with per-tenant overrides from tenant YAML files.
 *
 * @param tenantId      tenant identifier
 * @param agentId       agent identifier
 * @param name          display name
 * @param llm           LLM provider/model configuration
 * @param systemPrompt  system prompt resolution strategy
 * @param tools         tool policy (profile + allow/deny)
 * @param skills        enabled skill names
 * @param mcpServers    MCP server references
 * @param channels      per-tenant channel credentials
 * @param features      feature flags
 * @param errorMessages customized error messages
 * @param identity      identity properties
 * @param toolLoop      tool loop configuration
 * @param loopDelegate  agent loop delegate configuration
 */
public record TenantAgentConfig(
        String tenantId,
        String agentId,
        String name,
        LlmConfig llm,
        SystemPromptConfig systemPrompt,
        AgentProperties.ToolPolicyConfig tools,
        List<String> skills,
        List<McpServerRef> mcpServers,
        TenantChannelsConfig channels,
        FeatureFlags features,
        ErrorMessages errorMessages,
        IdentityProperties identity,
        ToolLoopProperties toolLoop,
        AgentLoopDelegateConfig loopDelegate
) {
    public TenantAgentConfig {
        if (llm == null) llm = LlmConfig.DEFAULT;
        if (systemPrompt == null) systemPrompt = SystemPromptConfig.DEFAULT;
        if (tools == null) tools = AgentProperties.ToolPolicyConfig.DEFAULT;
        if (skills == null) skills = List.of();
        if (mcpServers == null) mcpServers = List.of();
        if (channels == null) channels = TenantChannelsConfig.EMPTY;
        if (features == null) features = FeatureFlags.DEFAULT;
        if (errorMessages == null) errorMessages = ErrorMessages.DEFAULT;
        if (identity == null) identity = IdentityProperties.DEFAULT;
        if (toolLoop == null) toolLoop = ToolLoopProperties.DEFAULT;
        if (loopDelegate == null) loopDelegate = AgentLoopDelegateConfig.DISABLED;
    }

    /**
     * Build a TenantAgentConfig from application.yml defaults (single-tenant mode).
     */
    public static TenantAgentConfig fromDefaults(String tenantId, AgentProperties.AgentConfig agentConfig) {
        LlmConfig llm = agentConfig.llm() != null
                ? agentConfig.llm()
                : fromAgentModelConfig(agentConfig.model());

        return new TenantAgentConfig(
                tenantId,
                agentConfig.id(),
                agentConfig.name(),
                llm,
                agentConfig.systemPrompt(),
                agentConfig.tools(),
                agentConfig.skills(),
                agentConfig.mcpServers() != null ? agentConfig.mcpServers() : List.of(),
                agentConfig.channels(),
                agentConfig.features(),
                agentConfig.errorMessages(),
                agentConfig.identity(),
                agentConfig.toolLoop(),
                agentConfig.loopDelegate()
        );
    }

    private static LlmConfig fromAgentModelConfig(AgentProperties.AgentModelConfig model) {
        if (model == null) return LlmConfig.DEFAULT;
        return new LlmConfig(
                null, model.primary(), model.fallbacks(),
                model.thinkingModel(), 0.7, 4096, 120
        );
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String tenantId;
        private String agentId;
        private String name;
        private LlmConfig llm;
        private SystemPromptConfig systemPrompt;
        private AgentProperties.ToolPolicyConfig tools;
        private List<String> skills;
        private List<McpServerRef> mcpServers;
        private TenantChannelsConfig channels;
        private FeatureFlags features;
        private ErrorMessages errorMessages;
        private IdentityProperties identity;
        private ToolLoopProperties toolLoop;
        private AgentLoopDelegateConfig loopDelegate;

        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder llm(LlmConfig llm) { this.llm = llm; return this; }
        public Builder systemPrompt(SystemPromptConfig systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder tools(AgentProperties.ToolPolicyConfig tools) { this.tools = tools; return this; }
        public Builder skills(List<String> skills) { this.skills = skills; return this; }
        public Builder mcpServers(List<McpServerRef> mcpServers) { this.mcpServers = mcpServers; return this; }
        public Builder channels(TenantChannelsConfig channels) { this.channels = channels; return this; }
        public Builder features(FeatureFlags features) { this.features = features; return this; }
        public Builder errorMessages(ErrorMessages errorMessages) { this.errorMessages = errorMessages; return this; }
        public Builder identity(IdentityProperties identity) { this.identity = identity; return this; }
        public Builder toolLoop(ToolLoopProperties toolLoop) { this.toolLoop = toolLoop; return this; }
        public Builder loopDelegate(AgentLoopDelegateConfig loopDelegate) { this.loopDelegate = loopDelegate; return this; }

        public TenantAgentConfig build() {
            return new TenantAgentConfig(tenantId, agentId, name, llm, systemPrompt, tools,
                    skills, mcpServers, channels, features, errorMessages, identity,
                    toolLoop, loopDelegate);
        }
    }
}
