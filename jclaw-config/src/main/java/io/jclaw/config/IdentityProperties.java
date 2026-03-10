package io.jclaw.config;

public record IdentityProperties(
        String name,
        String description
) {
    public static final IdentityProperties DEFAULT = new IdentityProperties(
            "JClaw", "Personal AI assistant"
    );
}
