package io.jaiclaw.htmlpdf;

public class HtmlToPdfException extends RuntimeException {

    public HtmlToPdfException(String message, Throwable cause) {
        super(message, cause);
    }

    public HtmlToPdfException(String message) {
        super(message);
    }
}
