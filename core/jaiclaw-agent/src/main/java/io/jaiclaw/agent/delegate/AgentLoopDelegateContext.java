package io.jaiclaw.agent.delegate;

import io.jaiclaw.config.TenantAgentConfig;

import java.util.Map;

/**
 * Context provided to an {@link AgentLoopDelegate} for execution.
 *
 * @param sessionKey          session identifier
 * @param tenantId            tenant identifier
 * @param conversationHistory serialized conversation history
 * @param tenantConfig        the resolved per-tenant agent configuration
 * @param systemPrompt        the resolved system prompt
 * @param properties          delegate-specific properties from AgentLoopDelegateConfig
 */
public record AgentLoopDelegateContext(
        String sessionKey,
        String tenantId,
        String conversationHistory,
        TenantAgentConfig tenantConfig,
        String systemPrompt,
        Map<String, Object> properties
) {
    public AgentLoopDelegateContext {
        if (properties == null) properties = Map.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String sessionKey;
        private String tenantId;
        private String conversationHistory;
        private TenantAgentConfig tenantConfig;
        private String systemPrompt;
        private Map<String, Object> properties;

        public Builder sessionKey(String sessionKey) { this.sessionKey = sessionKey; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder conversationHistory(String conversationHistory) { this.conversationHistory = conversationHistory; return this; }
        public Builder tenantConfig(TenantAgentConfig tenantConfig) { this.tenantConfig = tenantConfig; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder properties(Map<String, Object> properties) { this.properties = properties; return this; }

        public AgentLoopDelegateContext build() {
            return new AgentLoopDelegateContext(
                    sessionKey, tenantId, conversationHistory, tenantConfig, systemPrompt, properties);
        }
    }
}
