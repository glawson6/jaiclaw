package io.jaiclaw.gateway.attachment;

import java.util.Optional;

/**
 * Result of an {@link AttachmentRouter#route} call. Lets a router optionally
 * annotate the prompt that will be sent to the agent and/or signal that it
 * has fully handled an attachment.
 *
 * <p>Typical shapes:
 * <ul>
 *   <li>{@link #none()} — fire-and-forget; router did its work, no prompt
 *       annotation. Equivalent to the pre-0.9.1 {@code void route(...)}
 *       behaviour.</li>
 *   <li>{@link #annotated(String)} — router wants a snippet of text
 *       prepended to the user input that will reach the agent. Useful for
 *       PDF extractors that summarise the document, image classifiers
 *       that surface a description, etc.</li>
 *   <li>{@link #handled()} — router has fully handled the attachment;
 *       framework-side auto-vision injection should skip it. Informational
 *       in v1; reserved for a future short-circuit hook.</li>
 * </ul>
 *
 * @param annotation optional prompt-side text to prepend to the user input
 * @param handled    {@code true} if the router has fully handled the
 *                   attachment (informational)
 */
public record RouterResult(Optional<String> annotation, boolean handled) {

    public RouterResult {
        if (annotation == null) {
            annotation = Optional.empty();
        }
    }

    /** No annotation, not handled — fire-and-forget. */
    public static RouterResult none() {
        return new RouterResult(Optional.empty(), false);
    }

    /** Prepend the given text to the user input that reaches the agent. */
    public static RouterResult annotated(String text) {
        return new RouterResult(
                text == null || text.isBlank() ? Optional.empty() : Optional.of(text),
                false);
    }

    /** Router fully handled the attachment (no annotation, no further pipeline work). */
    public static RouterResult fullyHandled() {
        return new RouterResult(Optional.empty(), true);
    }

    /** Combine annotation + handled in one factory. */
    public static RouterResult annotatedAndHandled(String text) {
        return new RouterResult(
                text == null || text.isBlank() ? Optional.empty() : Optional.of(text),
                true);
    }
}
