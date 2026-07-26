package io.jaiclaw.web.errors.core;

/**
 * Categorises a {@link Throwable} into one of the handled kinds. Used
 * internally by {@link WebErrorMapper} to route to the right response
 * shape without dragging Spring's web-stack types into this module.
 *
 * <p>The two web-stack-specific autoconfig modules
 * ({@code jaiclaw-web-errors-mvc} and {@code jaiclaw-web-errors-webflux})
 * supply the class-name matchers that decide which kind applies to a
 * given throwable.
 */
public enum FrameworkExceptionKind {
    /** {@code NoResourceFoundException} / {@code NoHandlerFoundException} → 404. */
    NOT_FOUND,
    /** {@code HttpRequestMethodNotSupportedException} → 405. */
    METHOD_NOT_ALLOWED,
    /** {@code HttpMediaTypeNotSupportedException} → 415. */
    UNSUPPORTED_MEDIA_TYPE,
    /** {@code HttpMediaTypeNotAcceptableException} → 406. */
    NOT_ACCEPTABLE,
    /** Binding / validation family → 400. */
    VALIDATION,
    /** Spring Security {@code AuthenticationException} → 401. */
    UNAUTHENTICATED,
    /** Spring Security {@code AccessDeniedException} → 403. */
    FORBIDDEN,
    /** {@code ResponseStatusException} — status from the exception itself. */
    RESPONSE_STATUS,
    /** Custom mapping supplied via {@code status-overrides} in properties. */
    OVERRIDDEN,
    /** Anything not matched above — 500, logged at ERROR. */
    UNHANDLED
}
