package io.jaiclaw.pipeline.render;

/**
 * Minimal HTML entity encoder for the pipeline HTML renderer. Escapes the
 * five characters that matter for injecting user-controlled text into
 * HTML text nodes and attribute values: {@code & < > " '}.
 *
 * <p>Standalone (no jsoup dependency at production runtime — jsoup is
 * test-scope only for the renderer spec) and side-effect-free.
 */
final class HtmlEscaper {

    private HtmlEscaper() {}

    /**
     * Escapes {@code text} for safe inclusion in either an HTML text node
     * or a {@code "…"}-quoted attribute value. Null becomes the empty
     * string.
     */
    static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
