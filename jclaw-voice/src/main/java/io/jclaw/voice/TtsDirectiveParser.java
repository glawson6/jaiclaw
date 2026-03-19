package io.jclaw.voice;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code [[tts:...]]} directives from agent response text.
 *
 * <p>Example: {@code "Hello [[tts:voice=alloy]]world[[/tts]] end"}
 * produces segments: "Hello " (default), "world" (voice=alloy), " end" (default).
 */
public class TtsDirectiveParser {

    private static final Pattern DIRECTIVE_OPEN = Pattern.compile("\\[\\[tts:([^\\]]+)\\]\\]");
    private static final Pattern DIRECTIVE_CLOSE = Pattern.compile("\\[\\[/tts\\]\\]");

    public record TtsSegment(String text, Map<String, String> params) {
        public TtsSegment(String text) {
            this(text, Map.of());
        }

        public String voice() {
            return params.getOrDefault("voice", null);
        }

        public String provider() {
            return params.getOrDefault("provider", null);
        }
    }

    public List<TtsSegment> parse(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<TtsSegment> segments = new ArrayList<>();
        Matcher openMatcher = DIRECTIVE_OPEN.matcher(text);
        int lastEnd = 0;

        while (openMatcher.find()) {
            if (openMatcher.start() > lastEnd) {
                String before = text.substring(lastEnd, openMatcher.start()).trim();
                if (!before.isEmpty()) {
                    segments.add(new TtsSegment(before));
                }
            }

            Map<String, String> params = parseParams(openMatcher.group(1));
            int contentStart = openMatcher.end();

            Matcher closeMatcher = DIRECTIVE_CLOSE.matcher(text);
            if (closeMatcher.find(contentStart)) {
                String content = text.substring(contentStart, closeMatcher.start());
                segments.add(new TtsSegment(content, params));
                lastEnd = closeMatcher.end();
            } else {
                String content = text.substring(contentStart);
                segments.add(new TtsSegment(content, params));
                lastEnd = text.length();
            }
        }

        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                segments.add(new TtsSegment(remaining));
            }
        }

        if (segments.isEmpty()) {
            segments.add(new TtsSegment(text));
        }

        return segments;
    }

    /**
     * Strip all TTS directives from text, returning plain text.
     */
    public String stripDirectives(String text) {
        if (text == null) return "";
        return text.replaceAll("\\[\\[tts:[^\\]]*\\]\\]", "")
                   .replaceAll("\\[\\[/tts\\]\\]", "");
    }

    private Map<String, String> parseParams(String paramString) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : paramString.split(",")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0].trim(), kv[1].trim());
            }
        }
        return params;
    }
}
