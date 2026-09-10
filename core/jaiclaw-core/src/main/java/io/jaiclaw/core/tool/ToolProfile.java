package io.jaiclaw.core.tool;

/**
 * Tool visibility profiles controlling which tools are available to the agent.
 * Modeled after OpenClaw's ToolProfileId system.
 */
public enum ToolProfile {
    /** No tools — pure conversation, no tool access */
    NONE,
    /** Minimal tools — read-only, no execution */
    MINIMAL,
    /** Coding tools — file read/write/edit, shell execution */
    CODING,
    /** Messaging tools — channel send/receive capabilities */
    MESSAGING,
    /**
     * Tools safe to expose to an unauthenticated inbound webhook: read-only
     * research and analysis (web search, web fetch, media analysis), and nothing
     * that writes. Deliberately excludes shell, file writes and delegation —
     * a webhook payload is attacker-controlled input, so anything it can reach
     * must be unable to change the host or spawn further work.
     *
     * <p>Added in 1.2.0 for {@code jaiclaw-channel-webhook}.
     */
    WEBHOOK_SAFE,
    /** Full tool access — all tools enabled */
    FULL;

    /**
     * Relative privilege, least to most. Used to clamp one profile to another —
     * a delegated child may never exceed its parent, and a webhook-originated
     * session may never exceed WEBHOOK_SAFE.
     *
     * <p>MESSAGING sits below CODING because sending a message is narrower than
     * shell and filesystem access. WEBHOOK_SAFE sits just above MINIMAL: it adds
     * read-only research but still writes nothing.
     */
    public int privilege() {
        return switch (this) {
            case NONE -> 0;
            case MINIMAL -> 1;
            case WEBHOOK_SAFE -> 2;
            case MESSAGING -> 3;
            case CODING -> 4;
            case FULL -> 5;
        };
    }

    /** The less-privileged of two profiles; null is treated as most restrictive. */
    public static ToolProfile narrowest(ToolProfile a, ToolProfile b) {
        if (a == null) return b == null ? NONE : b;
        if (b == null) return a;
        return a.privilege() <= b.privilege() ? a : b;
    }
}
