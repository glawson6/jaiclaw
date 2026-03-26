package io.jaiclaw.config;

public record IdentityProperties(
        String name,
        String description
) {
    public static final IdentityProperties DEFAULT = new IdentityProperties(
            "JaiClaw", "Personal AI assistant"
    );
}
