package io.jaiclaw.kanban.state;

/**
 * Outcome of asking a {@link TaskStateEngine} to fire an event against a card.
 *
 * <p>{@code accepted} engines update the task and persist; rejected results
 * carry the {@code reason} that the REST layer surfaces as {@code 409 + body}.
 *
 * @param accepted   whether the engine accepted the transition
 * @param fromState  current state (always set, even on rejection)
 * @param toState    target state when accepted; {@code null} when rejected
 * @param reason     human-readable reason when rejected; {@code null} when accepted
 */
public record TransitionResult(
        boolean accepted,
        String fromState,
        String toState,
        String reason
) {
    public static TransitionResult accept(String from, String to) {
        return new TransitionResult(true, from, to, null);
    }

    public static TransitionResult reject(String from, String reason) {
        return new TransitionResult(false, from, null, reason);
    }
}
