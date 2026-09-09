package io.jaiclaw.agent.loop;

import io.jaiclaw.core.api.Experimental;

/**
 * Detects a model that has stopped saying anything.
 *
 * <p>Some providers, under some prompt shapes, return an assistant message with
 * neither text nor tool calls. One such response is a transient hiccup; two in a
 * row means the run is wedged and will otherwise spin until the iteration budget
 * drains. On the second consecutive empty response the loop forces a final turn
 * with an explicit instruction to answer.
 *
 * <p>Not thread-safe: one instance belongs to one run.
 *
 * <p>Phase 1 of the 1.2.0 plan.
 */
@Experimental
public final class EmptyResponseGuard {

    /** Consecutive empty responses tolerated before the loop forces a final turn. */
    public static final int THRESHOLD = 2;

    private int consecutiveEmpty;

    /**
     * Records one model response.
     *
     * @param text      the assistant text, may be null
     * @param toolCalls how many tool calls the response requested
     * @return true when the loop should force a tool-less final turn
     */
    public boolean observe(String text, int toolCalls) {
        boolean empty = toolCalls == 0 && (text == null || text.isBlank());
        if (empty) {
            consecutiveEmpty++;
        } else {
            consecutiveEmpty = 0;
        }
        return consecutiveEmpty >= THRESHOLD;
    }

    /** Consecutive empty responses seen so far. */
    public int consecutiveEmpty() {
        return consecutiveEmpty;
    }

    /** The instruction attached to the forced final turn. */
    public static String instruction() {
        return "[guard] The previous responses were empty. Reply now, in plain text, with your "
                + "best answer using the information already gathered. Do not call any tools.";
    }
}
