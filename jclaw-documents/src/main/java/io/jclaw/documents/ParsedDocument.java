package io.jclaw.documents;

import java.util.Map;

/**
 * Result of parsing a document — contains the extracted text and metadata.
 *
 * @param text     extracted text content
 * @param metadata document metadata (e.g., title, author, page count)
 */
public record ParsedDocument(
        String text,
        Map<String, String> metadata
) {
    public ParsedDocument {
        if (text == null) text = "";
        if (metadata == null) metadata = Map.of();
    }

    public ParsedDocument(String text) {
        this(text, Map.of());
    }

    /**
     * Whether the parsed document has any meaningful text content.
     */
    public boolean hasContent() {
        return !text.isBlank();
    }
}
