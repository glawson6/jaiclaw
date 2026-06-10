package io.jaiclaw.plugin;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.hook.HookHandler;
import io.jaiclaw.core.hook.event.HookEvent;
import io.jaiclaw.core.tool.ToolCallback;

import java.util.Map;

/**
 * API surface exposed to plugin implementations during registration.
 *
 * <p>0.8.0 hard-break: hook registration is now keyed by typed
 * {@link HookEvent} subclass rather than the pre-0.8.0
 * {@code HookName} enum. Handlers receive a single typed event argument
 * instead of the old {@code (Object event, Object context)} pair. See
 * {@code docs/MIGRATION-0.8.md} § P3.1.
 *
 * <p>Migration example:
 * <pre>{@code
 * // Pre-0.8.0
 * api.on(HookName.BEFORE_AGENT_START, (event, ctx) -> {
 *     String agentId = extractString(event, "agentId", "unknown");
 *     metrics.recordAgentInvocation(agentId, ...);
 *     return null;
 * });
 *
 * // 0.8.0
 * api.on(AgentStartedEvent.class, event -> {
 *     metrics.recordAgentInvocation(event.agentId(), ...);
 *     return null;
 * });
 * }</pre>
 */
@Experimental
public interface PluginApi {

    String id();

    String name();

    void registerTool(ToolCallback tool);

    /**
     * Register a hook handler for the given event type with default priority.
     *
     * @param eventType the {@link HookEvent} subclass to listen for
     * @param handler   the handler (single-arg, returns a replacement event
     *                  for modifying hooks or {@code null} otherwise)
     * @param <E>       the event subtype
     */
    <E extends HookEvent> void on(Class<E> eventType, HookHandler<E> handler);

    /**
     * Register a hook handler with explicit priority. Lower priority numbers
     * run first.
     */
    <E extends HookEvent> void on(Class<E> eventType, HookHandler<E> handler, int priority);

    Map<String, Object> pluginConfig();

    PluginStateStore stateStore();
}
