package io.jclaw.compaction;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts identifiers (UUIDs, URLs, IPs, file paths) from text
 * to verify they survive summarization.
 */
public class IdentifierPreserver {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s)>\"]+");
    private static final Pattern IP_PATTERN =
            Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    private static final Pattern FILE_PATH_PATTERN =
            Pattern.compile("(?:/[\\w.\\-]+){2,}");

    public Set<String> extractIdentifiers(String text) {
        Set<String> identifiers = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return identifiers;
        extractMatches(text, UUID_PATTERN, identifiers);
        extractMatches(text, URL_PATTERN, identifiers);
        extractMatches(text, IP_PATTERN, identifiers);
        extractMatches(text, FILE_PATH_PATTERN, identifiers);
        return identifiers;
    }

    public Set<String> findMissing(String originalText, String summary) {
        Set<String> original = extractIdentifiers(originalText);
        Set<String> preserved = extractIdentifiers(summary);
        original.removeAll(preserved);
        return original;
    }

    private void extractMatches(String text, Pattern pattern, Set<String> results) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            results.add(matcher.group());
        }
    }
}
