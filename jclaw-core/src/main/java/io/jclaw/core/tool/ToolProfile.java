package io.jclaw.core.tool;

/**
 * Tool visibility profiles controlling which tools are available to the agent.
 * Modeled after OpenClaw's ToolProfileId system.
 */
public enum ToolProfile {
    /** Minimal tools — read-only, no execution */
    MINIMAL,
    /** Coding tools — file read/write/edit, shell execution */
    CODING,
    /** Messaging tools — channel send/receive capabilities */
    MESSAGING,
    /** Full tool access — all tools enabled */
    FULL
}
