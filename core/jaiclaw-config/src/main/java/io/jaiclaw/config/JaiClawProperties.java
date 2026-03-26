package io.jaiclaw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration properties for JaiClaw, bound from {@code jaiclaw.*} in application.yml.
 */
@ConfigurationProperties(prefix = "jaiclaw")
public record JaiClawProperties(
        IdentityProperties identity,
        AgentProperties agent,
        ToolsProperties tools,
        SkillsProperties skills,
        PluginsProperties plugins,
        MemoryProperties memory,
        ModelsProperties models,
        SessionProperties session,
        McpServerProperties mcpServers,
        ChannelsProperties channels
) {
    public JaiClawProperties {
        if (identity == null) identity = IdentityProperties.DEFAULT;
        if (agent == null) agent = AgentProperties.DEFAULT;
        if (tools == null) tools = ToolsProperties.DEFAULT;
        if (skills == null) skills = SkillsProperties.DEFAULT;
        if (plugins == null) plugins = PluginsProperties.DEFAULT;
        if (memory == null) memory = MemoryProperties.DEFAULT;
        if (models == null) models = ModelsProperties.DEFAULT;
        if (session == null) session = SessionProperties.DEFAULT;
        if (mcpServers == null) mcpServers = McpServerProperties.DEFAULT;
        if (channels == null) channels = ChannelsProperties.DEFAULT;
    }
}
