package io.jaiclaw.web.errors.core;

/**
 * The mapper's decision: an HTTP status code, a response body string,
 * a content-type, and the kind of exception this response resulted
 * from. Value type; no Spring dependency.
 *
 * <p>{@code contentType} is a raw string (e.g. {@code "text/plain"},
 * {@code "application/problem+json"}) rather than Spring's
 * {@code MediaType} so this module compiles without any web-stack
 * dependency. The two web-stack modules translate the string to their
 * platform's media-type type at write time.
 *
 * @param status      HTTP status code (200..599)
 * @param body        response body (may be empty for 204-style responses)
 * @param contentType MIME string suitable for a {@code Content-Type} header
 * @param kind        the exception category the mapper decided this fell into
 * @param cause       the original throwable; retained so the WebErrorLogger
 *                    can log it at ERROR before the caller renders the body.
 *                    Never null.
 */
public record WebErrorResponse(
        int status,
        String body,
        String contentType,
        FrameworkExceptionKind kind,
        Throwable cause) {

    public WebErrorResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status out of range: " + status);
        }
        if (body == null) body = "";
        if (contentType == null || contentType.isBlank()) {
            contentType = "text/plain;charset=UTF-8";
        }
        if (kind == null) kind = FrameworkExceptionKind.UNHANDLED;
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
    }

    /**
     * True when the status is in the 5xx range. The
     * {@code WebErrorLogger} contract requires the caller to log at
     * ERROR with the full throwable for these responses.
     */
    public boolean isServerError() {
        return status >= 500 && status < 600;
    }

    /**
     * True when the status is in the 4xx range.
     */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }
}
