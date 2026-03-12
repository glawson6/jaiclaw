package io.jclaw.documents;

/**
 * Thrown when a document cannot be parsed.
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
