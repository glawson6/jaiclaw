package io.jaiclaw.channel.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits long text messages into chunks that fit within platform character limits.
 * Splitting priority: paragraph boundary → line boundary → sentence boundary → hard cut.
 * Code block fences ({@code ```}) are preserved across chunk boundaries.
 */
public final class MessageChunker {

    private static final Pattern CODE_FENCE = Pattern.compile("^```.*$", Pattern.MULTILINE);
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    private MessageChunker() {}

    /**
     * Split text into chunks that each fit within the platform's character limit.
     *
     * @param text   the full message text (may be null or empty)
     * @param limits platform-specific limits
     * @return list of chunks; single-element list if text fits, empty list if text is blank
     */
    public static List<String> chunk(String text, PlatformLimits limits) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        int max = limits.maxTextLength();
        if (text.length() <= max) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        String remaining = text;
        boolean inCodeBlock = false;

        while (!remaining.isEmpty()) {
            if (remaining.length() <= max) {
                chunks.add(remaining);
                break;
            }

            int splitAt = findSplitPoint(remaining, max, inCodeBlock);
            String chunk = remaining.substring(0, splitAt).stripTrailing();
            remaining = remaining.substring(splitAt).stripLeading();

            // Determine code fence state after this chunk
            int fenceCount = countFences(chunk);
            boolean endsInCode = inCodeBlock ^ (fenceCount % 2 != 0);

            if (endsInCode) {
                // Close the code block at end of this chunk, reopen at start of next
                chunk = chunk + "\n```";
                remaining = "```\n" + remaining;
            }
            // After patching, inCodeBlock is always false for the next iteration:
            // - if endsInCode: we closed the fence, and prepended ``` will be counted next time
            // - if !endsInCode: chunk ended outside code, no action needed
            inCodeBlock = false;

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    /**
     * Find the best position to split text, trying paragraph → line → sentence → hard boundaries.
     */
    private static int findSplitPoint(String text, int max, boolean inCodeBlock) {
        // Try paragraph boundary (double newline)
        int pos = text.lastIndexOf("\n\n", max);
        if (pos > 0) {
            return pos + 2; // include the double newline in the first chunk
        }

        // Try line boundary
        pos = text.lastIndexOf('\n', max);
        if (pos > 0) {
            return pos + 1;
        }

        // Try sentence boundary
        String window = text.substring(0, max);
        Matcher m = SENTENCE_END.matcher(window);
        int lastSentence = -1;
        while (m.find()) {
            lastSentence = m.end();
        }
        if (lastSentence > max / 4) { // only use sentence split if it's not too early
            return lastSentence;
        }

        // Try word boundary (space)
        pos = window.lastIndexOf(' ');
        if (pos > max / 4) {
            return pos + 1;
        }

        // Hard cut at max
        return max;
    }

    private static int countFences(String text) {
        Matcher m = CODE_FENCE.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
