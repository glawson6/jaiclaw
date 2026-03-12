package io.jclaw.documents;

import java.util.List;

/**
 * Composite parser that delegates to the first parser supporting the given MIME type.
 */
public class CompositeDocumentParser implements DocumentParser {

    private final List<DocumentParser> parsers;

    public CompositeDocumentParser(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    /**
     * Creates a composite with the built-in parsers (PDF, HTML, plain text).
     */
    public static CompositeDocumentParser withDefaults() {
        return new CompositeDocumentParser(List.of(
                new PdfDocumentParser(),
                new HtmlDocumentParser(),
                new PlainTextDocumentParser()
        ));
    }

    @Override
    public boolean supports(String mimeType) {
        return parsers.stream().anyMatch(p -> p.supports(mimeType));
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType) {
        return parsers.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst()
                .orElseThrow(() -> new DocumentParseException(
                        "No parser found for MIME type: " + mimeType))
                .parse(bytes, mimeType);
    }
}
