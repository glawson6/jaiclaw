package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a human's free-text reply as an approve / deny verdict, optionally
 * bound to the approval code it answers.
 *
 * <p>Buttons would remove the ambiguity, but the Telegram adapter has no
 * inline-keyboard support and no {@code callback_query} routing, so the reply
 * arrives as ordinary message text. That makes the parse a security decision:
 * anything it accepts as consent authorises a tool call.
 *
 * <h2>Grammar</h2>
 *
 * <pre>
 *   reply := verdict
 *          | verdict code
 *          | code verdict
 * </pre>
 *
 * <p>where {@code verdict} is one token from a deliberately small vocabulary
 * and {@code code} has the shape issued by {@link PendingApprovalRegistry}
 * ({@code yes K7Q4}, {@code K7Q4 no}). A verdict with no code is still
 * recognised as an <em>answer</em> — so the filter can tell the approver what
 * is missing instead of letting the agent reply "ok!" to a bare "yes" — but it
 * cannot redeem anything on its own.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li><strong>Exact match on a short vocabulary.</strong> The whole message,
 *       trimmed and case-folded, must be a verdict or a verdict plus a code.
 *       Substring matching is not used — "don't approve that" contains
 *       "approve".</li>
 *   <li><strong>The vocabulary is narrow on purpose.</strong> {@code ok},
 *       {@code sure}, {@code go}, {@code y} are ordinary conversational
 *       acknowledgements; in a shared chat an incidental "ok" must not read as
 *       consent. Only words that mean nothing else are accepted.</li>
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

    /**
     * A recognised answer.
     *
     * @param decision the verdict
     * @param code     the approval code the answer names, upper-cased; {@code null} when absent
     */
    public record Reply(ToolApprovalDecision decision, String code) {
        public boolean hasCode() {
            return code != null;
        }

        public boolean isApproved() {
            return decision instanceof ToolApprovalDecision.Approved;
        }
    }

    private static final Set<String> APPROVE = Set.of(
            "yes", "approve", "approved", "confirm", "confirmed");

    private static final Set<String> DENY = Set.of(
            "no", "deny", "denied", "reject", "rejected", "cancel", "abort",
            "stop", "refuse", "veto");

    private ApprovalReplyParser() {
    }

    /**
     * Interprets {@code text} as an approval answer.
     *
     * @return the verdict and the code it names (if any), or empty when the
     *         reply is not an answer to the question
     */
    public static Optional<Reply> parse(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String[] tokens = normalized.split(" ");
        if (tokens.length == 1) {
            return verdict(tokens[0]).map(d -> new Reply(d, null));
        }
        if (tokens.length == 2) {
            if (PendingApprovalRegistry.isCode(tokens[1])) {
                return verdict(tokens[0]).map(d ->
                        new Reply(d, PendingApprovalRegistry.normalizeCode(tokens[1])));
            }
            if (PendingApprovalRegistry.isCode(tokens[0])) {
                return verdict(tokens[1]).map(d ->
                        new Reply(d, PendingApprovalRegistry.normalizeCode(tokens[0])));
            }
        }
        return Optional.empty();
    }

    /** True when the reply is recognisable as an answer either way, with or without a code. */
    public static boolean isAnswer(String text) {
        return parse(text).isPresent();
    }

    private static Optional<ToolApprovalDecision> verdict(String token) {
        // Deny first: if a token ever lands in both sets, refuse rather than allow.
        if (DENY.contains(token)) {
            return Optional.of(new ToolApprovalDecision.Denied("denied by approver"));
        }
        if (APPROVE.contains(token)) {
            return Optional.of(new ToolApprovalDecision.Approved());
        }
        return Optional.empty();
    }

    /**
     * Lower-cases, collapses internal whitespace, and strips trailing
     * punctuation. Deliberately does not strip leading punctuation: {@code
     * "/yes"} is a command invocation, not an answer.
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String s = text.strip().toLowerCase(Locale.ROOT);
        // Collapse runs of whitespace so "yes  K7Q4" matches "yes K7Q4".
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
