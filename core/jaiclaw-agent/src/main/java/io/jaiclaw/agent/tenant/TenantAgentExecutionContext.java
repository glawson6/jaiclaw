package io.jaiclaw.agent.tenant;

import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tool.ToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * Complete per-tenant execution context, ready for use by {@link io.jaiclaw.agent.AgentRuntime}.
 *
 * @param config              the resolved tenant agent config
 * @param chatModel           tenant-specific ChatModel
 * @param chatClientBuilder   ChatClient.Builder configured for this tenant's ChatModel
 * @param resolvedTools       tools filtered by tenant's tool policy
 * @param resolvedSkills      skills resolved for this tenant
 * @param resolvedSystemPrompt the fully resolved system prompt
 */
public record TenantAgentExecutionContext(
        TenantAgentConfig config,
        ChatModel chatModel,
        ChatClient.Builder chatClientBuilder,
        List<ToolCallback> resolvedTools,
        List<SkillDefinition> resolvedSkills,
        String resolvedSystemPrompt
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TenantAgentConfig config;
        private ChatModel chatModel;
        private ChatClient.Builder chatClientBuilder;
        private List<ToolCallback> resolvedTools;
        private List<SkillDefinition> resolvedSkills;
        private String resolvedSystemPrompt;

        public Builder config(TenantAgentConfig config) { this.config = config; return this; }
        public Builder chatModel(ChatModel chatModel) { this.chatModel = chatModel; return this; }
        public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) { this.chatClientBuilder = chatClientBuilder; return this; }
        public Builder resolvedTools(List<ToolCallback> resolvedTools) { this.resolvedTools = resolvedTools; return this; }
        public Builder resolvedSkills(List<SkillDefinition> resolvedSkills) { this.resolvedSkills = resolvedSkills; return this; }
        public Builder resolvedSystemPrompt(String resolvedSystemPrompt) { this.resolvedSystemPrompt = resolvedSystemPrompt; return this; }

        public TenantAgentExecutionContext build() {
            return new TenantAgentExecutionContext(
                    config, chatModel, chatClientBuilder, resolvedTools, resolvedSkills, resolvedSystemPrompt);
        }
    }
}
