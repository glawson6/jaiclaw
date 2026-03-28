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
        ChannelsProperties channels,
        HttpProperties http,
        TenantConfigProperties tenant
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
        if (http == null) http = HttpProperties.DEFAULT;
        if (tenant == null) tenant = TenantConfigProperties.DEFAULT;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private IdentityProperties identity;
        private AgentProperties agent;
        private ToolsProperties tools;
        private SkillsProperties skills;
        private PluginsProperties plugins;
        private MemoryProperties memory;
        private ModelsProperties models;
        private SessionProperties session;
        private McpServerProperties mcpServers;
        private ChannelsProperties channels;
        private HttpProperties http;
        private TenantConfigProperties tenant;

        public Builder identity(IdentityProperties identity) { this.identity = identity; return this; }
        public Builder agent(AgentProperties agent) { this.agent = agent; return this; }
        public Builder tools(ToolsProperties tools) { this.tools = tools; return this; }
        public Builder skills(SkillsProperties skills) { this.skills = skills; return this; }
        public Builder plugins(PluginsProperties plugins) { this.plugins = plugins; return this; }
        public Builder memory(MemoryProperties memory) { this.memory = memory; return this; }
        public Builder models(ModelsProperties models) { this.models = models; return this; }
        public Builder session(SessionProperties session) { this.session = session; return this; }
        public Builder mcpServers(McpServerProperties mcpServers) { this.mcpServers = mcpServers; return this; }
        public Builder channels(ChannelsProperties channels) { this.channels = channels; return this; }
        public Builder http(HttpProperties http) { this.http = http; return this; }
        public Builder tenant(TenantConfigProperties tenant) { this.tenant = tenant; return this; }

        public JaiClawProperties build() {
            return new JaiClawProperties(identity, agent, tools, skills, plugins, memory,
                    models, session, mcpServers, channels, http, tenant);
        }
    }
}
