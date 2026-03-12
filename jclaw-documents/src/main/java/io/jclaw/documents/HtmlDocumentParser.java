package io.jclaw.documents;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parses HTML documents using Jsoup.
 * Extracts the body text content, stripping all HTML tags.
 */
public class HtmlDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "text/html", "application/xhtml+xml"
    );

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && SUPPORTED_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType) {
        try {
            String html = new String(bytes, StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);

            // Remove script and style elements before extracting text
            doc.select("script, style, nav, footer, header").remove();

            String text = doc.body() != null ? doc.body().text() : doc.text();

            Map<String, String> metadata = new HashMap<>();
            String title = doc.title();
            if (title != null && !title.isBlank()) {
                metadata.put("title", title);
            }

            return new ParsedDocument(text.strip(), Map.copyOf(metadata));
        } catch (Exception e) {
            throw new DocumentParseException("Failed to parse HTML document", e);
        }
    }
}
