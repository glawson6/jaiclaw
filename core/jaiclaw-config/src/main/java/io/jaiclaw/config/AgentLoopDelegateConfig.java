package io.jaiclaw.config;

import java.util.Map;

/**
 * Per-tenant agent loop delegate configuration. Allows a tenant to opt into
 * a pluggable alternative agent execution engine (e.g., Embabel GOAP planning).
 *
 * @param enabled          whether delegation is active (false by default)
 * @param delegateId       identifier for the delegate engine ("embabel", "langchain4j", etc.)
 * @param workflow         delegate-specific workflow name
 * @param llmRole          delegate-specific LLM role ("best", "cheapest", "reasoning")
 * @param timeoutSeconds   execution timeout
 * @param properties       delegate-specific configuration properties
 */
public record AgentLoopDelegateConfig(
        boolean enabled,
        String delegateId,
        String workflow,
        String llmRole,
        int timeoutSeconds,
        Map<String, String> properties
) {
    public static final AgentLoopDelegateConfig DISABLED = new AgentLoopDelegateConfig(
            false, null, null, null, 120, Map.of()
    );

    public AgentLoopDelegateConfig {
        if (timeoutSeconds <= 0) timeoutSeconds = 120;
        if (properties == null) properties = Map.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean enabled;
        private String delegateId;
        private String workflow;
        private String llmRole;
        private int timeoutSeconds;
        private Map<String, String> properties;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder delegateId(String delegateId) { this.delegateId = delegateId; return this; }
        public Builder workflow(String workflow) { this.workflow = workflow; return this; }
        public Builder llmRole(String llmRole) { this.llmRole = llmRole; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
        public Builder properties(Map<String, String> properties) { this.properties = properties; return this; }

        public AgentLoopDelegateConfig build() {
            return new AgentLoopDelegateConfig(enabled, delegateId, workflow, llmRole, timeoutSeconds, properties);
        }
    }
}
