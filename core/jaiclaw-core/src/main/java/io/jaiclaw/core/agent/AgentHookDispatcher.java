package io.jaiclaw.core.agent;

import io.jaiclaw.core.hook.HookName;

/**
 * SPI for dispatching lifecycle hooks during agent execution.
 * Decouples the agent runtime from the plugin SDK's {@code HookRunner}.
 */
public interface AgentHookDispatcher {

    /**
     * Fire a void (non-modifying) hook — handlers run in parallel.
     */
    <E, C> void fireVoid(HookName hookName, E event, C context);

    /**
     * Fire a modifying hook — handlers run sequentially, each receiving the
     * previous handler's output. Returns the final modified event.
     */
    <E, C> E fireModifying(HookName hookName, E event, C context);

    /**
     * Check if any handlers are registered for the given hook.
     */
    boolean hasHandlers(HookName hookName);
}
