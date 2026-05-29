package io.jaiclaw.agent.ownership;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects @mention patterns in message text.
 * Supports patterns like {@code @agentName} and {@code @agent-name}.
 */
public final class MentionDetector {

    private MentionDetector() {}

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z][a-zA-Z0-9_-]*)");

    /**
     * Extract all @mentions from the given text.
     *
     * @param text the message text to scan
     * @return list of mentioned names (without the @ prefix), in order of appearance
     */
    public static List<String> extractMentions(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        Matcher matcher = MENTION_PATTERN.matcher(text);
        List<String> mentions = new ArrayList<>();
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return Collections.unmodifiableList(mentions);
    }

    /**
     * Check if the text contains a mention of the given name (case-insensitive).
     */
    public static boolean isMentioned(String text, String name) {
        if (text == null || name == null) return false;
        return extractMentions(text).stream()
                .anyMatch(m -> m.equalsIgnoreCase(name));
    }

    /**
     * Get the first mention in the text, if any.
     */
    public static java.util.Optional<String> firstMention(String text) {
        List<String> mentions = extractMentions(text);
        return mentions.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(mentions.getFirst());
    }
}
