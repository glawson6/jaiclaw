package io.jaiclaw.core.model;

public record AgentIdentity(
        String id,
        String name,
        String description
) {
    public static final AgentIdentity DEFAULT = new AgentIdentity(
            "default", "JaiClaw", "Personal AI assistant"
    );
}
