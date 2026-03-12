package io.jclaw.documents;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Passthrough parser for plain text documents.
 * Returns the content as-is with minimal metadata.
 */
public class PlainTextDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "text/plain", "text/csv", "text/markdown"
    );

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && SUPPORTED_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return new ParsedDocument(text, Map.of("mimeType", mimeType));
    }
}
