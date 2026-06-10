package io.jaiclaw.plugin;

import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.hook.event.HookEvent;

/**
 * Adapts the existing {@link HookRunner} to the {@link AgentHookDispatcher} SPI,
 * allowing the agent runtime to fire hooks without a direct dependency on jaiclaw-plugin-sdk.
 *
 * <p>0.8.0 hard-break: methods are now keyed by typed event rather than the
 * pre-0.8.0 {@code HookName} enum. See {@code docs/MIGRATION-0.8.md} § P3.1.
 */
public class HookRunnerAdapter implements AgentHookDispatcher {

    private final HookRunner hookRunner;

    public HookRunnerAdapter(HookRunner hookRunner) {
        this.hookRunner = hookRunner;
    }

    @Override
    public <E extends HookEvent> void fireVoid(E event) {
        hookRunner.fireVoid(event);
    }

    @Override
    public <E extends HookEvent> E fireModifying(E event) {
        return hookRunner.fireModifying(event);
    }

    @Override
    public <E extends HookEvent> boolean hasHandlers(Class<E> eventType) {
        return hookRunner.hasHandlers(eventType);
    }
}
