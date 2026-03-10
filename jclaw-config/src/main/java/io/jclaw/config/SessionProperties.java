package io.jclaw.config;

public record SessionProperties(
        String scope,
        int idleMinutes
) {
    public static final SessionProperties DEFAULT = new SessionProperties(
            "per-sender", 30
    );
}
