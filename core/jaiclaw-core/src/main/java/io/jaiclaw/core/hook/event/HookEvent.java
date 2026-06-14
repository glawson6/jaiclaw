package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Sealed root of the JaiClaw hook event hierarchy.
 *
 * <p>Each lifecycle hook fired by the agent runtime carries a typed event
 * record that implements this interface. Plugins register handlers against
 * the event {@code Class<? extends HookEvent>} they care about; the
 * dispatcher routes events by type rather than by an enum discriminator.
 *
 * <p>Replaces the pre-0.8.0 {@code Map<String, Object>} payloads + free-form
 * {@code Object event, Object context} hook handler signatures. Migration
 * notes: {@code docs/MIGRATION-0.8.md} § P3.1.
 *
 * <p>Every event carries three fields by contract:
 * <ul>
 *   <li>{@link #agentId()} — which {@code JaiClawAgent} is firing the event.</li>
 *   <li>{@link #sessionKey()} — the originating session
 *       ({@code agentId:channel:accountId:peerId}), or {@code null} for
 *       framework-wide events.</li>
 *   <li>{@link #timestamp()} — when the event was created.</li>
 * </ul>
 *
 * <p>Subtypes add hook-specific fields (the tool name on
 * {@link ToolCallStartedEvent}, the assistant message on
 * {@link AgentEndedEvent}, etc.).
 */
@Experimental
public sealed interface HookEvent
        permits AgentStartedEvent, AgentEndedEvent,
                BeforeModelResolveEvent, BeforePromptBuildEvent,
                LlmInputEvent, LlmOutputEvent,
                ToolCallStartedEvent, ToolCallEndedEvent,
                MessageReceivedEvent, MessageSendingEvent, MessageSentEvent,
                SessionStartedEvent, SessionEndedEvent, BeforeResetEvent,
                BeforeCompactionEvent, AfterCompactionEvent,
                TaskStateChangedEvent,
                SoulUpdatedEvent, MemoryUpdatedEvent, TendenciesUpdatedEvent {

    /** The agent id firing this event (e.g. {@code "default"}). Never null. */
    String agentId();

    /** Session key ({@code agentId:channel:accountId:peerId}), or null for non-session events. */
    String sessionKey();

    /** When this event was created. Never null. */
    Instant timestamp();
}
