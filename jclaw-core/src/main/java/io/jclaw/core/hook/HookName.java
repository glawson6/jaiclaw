package io.jclaw.core.hook;

/**
 * Lifecycle hook names covering the full agent execution pipeline.
 * Modeled after OpenClaw's 24 hook events, scoped to the initial agent runtime.
 */
public enum HookName {
    // Model resolution
    BEFORE_MODEL_RESOLVE,

    // Prompt construction
    BEFORE_PROMPT_BUILD,

    // Agent lifecycle
    BEFORE_AGENT_START,
    AGENT_END,

    // LLM interaction
    LLM_INPUT,
    LLM_OUTPUT,

    // Context compaction
    BEFORE_COMPACTION,
    AFTER_COMPACTION,

    // Session lifecycle
    SESSION_START,
    SESSION_END,
    BEFORE_RESET,

    // Message flow
    MESSAGE_RECEIVED,
    MESSAGE_SENDING,
    MESSAGE_SENT,

    // Tool execution
    BEFORE_TOOL_CALL,
    AFTER_TOOL_CALL
}
