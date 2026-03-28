package io.jaiclaw.agent.tenant;

import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.config.prompt.SystemPromptLoaderFactory;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * Creates complete per-tenant execution contexts combining ChatModel, tools,
 * skills, and system prompts into a {@link TenantAgentExecutionContext}.
 */
public class TenantAgentRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(TenantAgentRuntimeFactory.class);

    private final TenantChatModelFactory chatModelFactory;
    private final ToolRegistry toolRegistry;
    private final List<SkillDefinition> defaultSkills;
    private final SystemPromptLoaderFactory promptLoaderFactory;

    public TenantAgentRuntimeFactory(TenantChatModelFactory chatModelFactory,
                                      ToolRegistry toolRegistry,
                                      List<SkillDefinition> defaultSkills,
                                      SystemPromptLoaderFactory promptLoaderFactory) {
        this.chatModelFactory = chatModelFactory;
        this.toolRegistry = toolRegistry;
        this.defaultSkills = defaultSkills;
        this.promptLoaderFactory = promptLoaderFactory;
    }

    /**
     * Create a complete execution context for the given tenant configuration.
     */
    public TenantAgentExecutionContext createContext(TenantAgentConfig config) {
        // 1. Get or create the tenant's ChatModel
        ChatModel chatModel = chatModelFactory.getOrCreate(config.tenantId(), config);

        // 2. Build a ChatClient.Builder for this tenant's model
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

        // 3. Resolve tools by tenant's tool policy
        ToolProfile profile = resolveProfile(config.tools().profile());
        List<ToolCallback> tools = toolRegistry.resolveForPolicy(
                profile, config.tools().allow(), config.tools().deny());

        // 4. Load system prompt using the configured strategy
        String systemPrompt = "";
        if (config.systemPrompt() != null) {
            systemPrompt = promptLoaderFactory.load(config.systemPrompt());
        }

        log.debug("Created execution context for tenant '{}': {} tools, prompt length={}",
                config.tenantId(), tools.size(), systemPrompt.length());

        return new TenantAgentExecutionContext(
                config, chatModel, chatClientBuilder, tools, defaultSkills, systemPrompt
        );
    }

    private ToolProfile resolveProfile(String profileName) {
        if (profileName == null) return ToolProfile.FULL;
        try {
            return ToolProfile.valueOf(profileName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown tool profile '{}', defaulting to FULL", profileName);
            return ToolProfile.FULL;
        }
    }
}
