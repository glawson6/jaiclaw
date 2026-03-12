package io.jclaw.documents;

/**
 * SPI for extracting text content from documents.
 * Implementations handle specific document formats (PDF, HTML, plain text).
 */
public interface DocumentParser {

    /**
     * Whether this parser can handle the given MIME type.
     */
    boolean supports(String mimeType);

    /**
     * Parse the document bytes and extract text content.
     *
     * @param bytes    raw document content
     * @param mimeType MIME type of the document
     * @return parsed document with extracted text
     * @throws DocumentParseException if parsing fails
     */
    ParsedDocument parse(byte[] bytes, String mimeType);
}
