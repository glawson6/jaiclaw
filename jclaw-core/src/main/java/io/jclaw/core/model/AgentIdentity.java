package io.jclaw.core.model;

public record AgentIdentity(
        String id,
        String name,
        String description
) {
    public static final AgentIdentity DEFAULT = new AgentIdentity(
            "default", "JClaw", "Personal AI assistant"
    );
}
