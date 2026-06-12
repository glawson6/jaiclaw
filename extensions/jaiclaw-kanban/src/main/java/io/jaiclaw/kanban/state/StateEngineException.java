package io.jaiclaw.kanban.state;

/**
 * Thrown for engine-side failures unrelated to user input (e.g. a
 * misconfigured board or a Spring State Machine internal error). Rejected
 * transitions return a {@link TransitionResult} with {@code accepted=false}
 * rather than throwing — this exception is reserved for the "shouldn't
 * happen" path.
 */
public class StateEngineException extends RuntimeException {
    public StateEngineException(String message) {
        super(message);
    }

    public StateEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
