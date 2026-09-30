package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a human's free-text reply as an approve / deny / unrecognised verdict.
 *
 * <p>Buttons would remove the ambiguity, but the Telegram adapter has no
 * inline-keyboard support and no {@code callback_query} routing, so the reply
 * arrives as ordinary message text. That makes the parse a security decision:
 * anything it accepts as consent authorises a tool call.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li><strong>Exact match on a short vocabulary.</strong> The whole message,
 *       trimmed and case-folded, must equal a known token. Substring matching
 *       is not used — "don't approve that" contains "approve".</li>
 *   <li><strong>Deny is checked first.</strong> On any overlap, the safer
 *       reading wins.</li>
 *   <li><strong>Unrecognised is not denial.</strong> It returns empty so the
 *       caller can leave the request pending and let the message through as an
 *       ordinary turn. Treating "what does that do?" as a refusal would be
 *       both wrong and confusing; the timeout remains the backstop.</li>
 * </ul>
 *
 * <p>Trailing punctuation is tolerated ({@code "yes!"}, {@code "no."}) since it
 * does not change meaning. Leading punctuation is not — a leading slash means a
 * command, which is not an answer.
 */
@Experimental
public final class ApprovalReplyParser {

    private static final Set<String> APPROVE = Set.of(
            "y", "yes", "yeah", "yep", "yup", "ok", "okay", "approve", "approved",
            "confirm", "confirmed", "go", "go ahead", "do it", "proceed", "allow",
            "sure", "affirmative", "permit");

    private static final Set<String> DENY = Set.of(
            "n", "no", "nope", "nah", "deny", "denied", "reject", "rejected",
            "cancel", "stop", "abort", "don't", "dont", "do not", "disallow",
            "negative", "refuse", "veto");

    private ApprovalReplyParser() {
    }

    /**
     * Interprets {@code text} as an approval verdict.
     *
     * @return {@code Approved} / {@code Denied}, or empty when the reply is not
     *         an answer to the question
     */
    public static Optional<ToolApprovalDecision> parse(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        // Deny first: if a token ever lands in both sets, refuse rather than allow.
        if (DENY.contains(normalized)) {
            return Optional.of(new ToolApprovalDecision.Denied("denied by approver"));
        }
        if (APPROVE.contains(normalized)) {
            return Optional.of(new ToolApprovalDecision.Approved());
        }
        return Optional.empty();
    }

    /** True when the reply is recognisable as an answer either way. */
    public static boolean isAnswer(String text) {
        return parse(text).isPresent();
    }

    /**
     * Lower-cases, collapses internal whitespace, and strips trailing
     * punctuation. Deliberately does not strip leading punctuation: {@code
     * "/yes"} is a command invocation, not an answer.
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String s = text.strip().toLowerCase(Locale.ROOT);
        // Collapse runs of whitespace so "go  ahead" matches "go ahead".
        s = s.replaceAll("\\s+", " ");
        // Trailing . ! ? , and combinations thereof carry no meaning here.
        s = s.replaceAll("[.!?,]+$", "");
        return s.strip();
    }

    /** The vocabulary, for docs and for the prompt sent to the approver. */
    public static Set<String> approveTokens() {
        return APPROVE;
    }

    public static Set<String> denyTokens() {
        return DENY;
    }
}
