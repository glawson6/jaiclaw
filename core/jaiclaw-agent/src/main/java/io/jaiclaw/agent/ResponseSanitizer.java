package io.jaiclaw.agent;

import java.util.regex.Pattern;

/**
 * Strips chain-of-thought reasoning that some models leak into their response.
 * Common patterns: "The user is asking...", "I should respond...", "Let me think..."
 *
 * <p>Some weaker models produce responses that are entirely internal reasoning
 * with no user-facing reply. When detected, the reasoning is stripped and
 * {@link #isEntirelyReasoning(String)} returns true so the caller can retry.
 */
final class ResponseSanitizer {

    private ResponseSanitizer() {}

    // Matches responses that are entirely chain-of-thought reasoning
    private static final Pattern ENTIRELY_COT = Pattern.compile(
            "(?is)^(?:The user (?:is|has|wants|said|asks|seems|just)|" +
            "I (?:should|need to|will|can|'ll|must|don't)|" +
            "Let me |This is a |They (?:are|want)|" +
            "Since the user|Based on|" +
            "(?:This|That) (?:is|seems|looks|requires)|" +
            "I'll just ).*$"
    );

    // Matches a reasoning prefix followed by an actual reply (separated by double newline)
    private static final Pattern COT_PREFIX = Pattern.compile(
            "(?is)^(?:The user (?:is|has|wants|said|asks|seems|just)|" +
            "I (?:should|need to|will|can|'ll|must|don't)|" +
            "Let me |This is a |They (?:are|want)|" +
            "Since the user|Based on)" +
            ".*?\\n\\n"
    );

    /**
     * Returns true if the entire response is chain-of-thought reasoning
     * with no user-facing content.
     */
    static boolean isEntirelyReasoning(String response) {
        if (response == null || response.isBlank()) return false;
        // Single paragraph (no double-newline break) that matches reasoning patterns
        return !response.contains("\n\n") && ENTIRELY_COT.matcher(response.trim()).matches();
    }

    /**
     * If the response starts with chain-of-thought reasoning followed by an actual reply,
     * strip the reasoning and return only the reply. If no reasoning prefix is found,
     * return unchanged.
     */
    static String sanitize(String response) {
        if (response == null || response.isBlank()) return response;

        String stripped = COT_PREFIX.matcher(response).replaceFirst("").stripLeading();

        // Only strip if there's still meaningful content left
        if (stripped.isEmpty() || stripped.length() < 5) {
            return response;
        }
        return stripped;
    }
}
