package io.jclaw.core.hook;

/**
 * Functional interface for hook handlers.
 *
 * @param <E> the event type
 * @param <C> the context type
 */
@FunctionalInterface
public interface HookHandler<E, C> {

    /**
     * Handle a lifecycle hook event.
     *
     * @param event   the hook event payload
     * @param context the hook execution context
     * @return result for modifying hooks, or null for void hooks
     */
    Object handle(E event, C context);
}
